package com.frostfirebloom.manaessencebridge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.resources.JsonReloadListener;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraft.tags.ITag;
import net.minecraft.tags.TagCollectionManager;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Курсы из датапака - для авторов сборок. Пул принимает не только эссенции
 * MA, но и любые предметы, описанные в файлах
 * data/<namespace>/manaessencebridge_exchange/<имя>.json:
 *
 * { "required_mod": "botania",                      (необязательно)
 *   "entries": [
 *     { "item": "minecraft:diamond", "mana": 40000, "tier": 2 },
 *     { "tag": "forge:gems/emerald", "mana": 30000 } ] }
 *
 * или одной записью прямо в корне. tier - с какого тира пула предмет
 * принимается (по умолчанию 1). Записи только на ПРОДАЖУ в пул: выкупить
 * такой предмет обратно нельзя, так что петли «продал - выкупил дешевле»
 * через наш мод не будет; цены - на совести автора сборки. Эссенции MA
 * всегда идут по своей лестнице x4, запись для них игнорируется.
 *
 * Сервер грузит файлы при старте и /reload и рассылает клиентам
 * (ExchangePacket) - для подсказки на предмете.
 */
public final class PoolExchange {

    public static final String FOLDER = "manaessencebridge_exchange";
    private static final int MAX_TIER = 6;

    private PoolExchange() {
    }

    /** Цена предмета для пула. essence - эссенция MA этого тира, null - запись датапака. */
    public static final class Price {
        public final int tier;
        public final int mana;
        @Nullable
        public final EssenceTier essence;

        Price(int tier, int mana, @Nullable EssenceTier essence) {
            this.tier = tier;
            this.mana = mana;
            this.essence = essence;
        }

        /** Примет ли пул такой тир. */
        public boolean fits(@Nullable InferiumCatalystCapability cap) {
            return cap != null && cap.getTier() >= tier;
        }

        /** Тир для цвета частиц и названия в подсказке. */
        public EssenceTier look() {
            EssenceTier t = essence != null ? essence : EssenceTier.byLevel(tier);
            return t != null ? t : EssenceTier.INFERIUM;
        }
    }

    /** Строка датапака: предмет или тег. */
    public static final class Entry {
        public final String id;
        public final boolean tag;
        public final int tier;
        public final int mana;

        public Entry(String id, boolean tag, int tier, int mana) {
            this.id = id;
            this.tag = tag;
            this.tier = tier;
            this.mana = mana;
        }
    }

    private static volatile List<Entry> entries = Collections.emptyList();
    private static volatile Map<Item, Price> items = Collections.emptyMap();
    private static volatile List<Map.Entry<ResourceLocation, Price>> tags = Collections.emptyList();

    public static List<Entry> entries() {
        return entries;
    }

    /** Новый набор записей - с сервера или из пакета. */
    static void set(List<Entry> list) {
        Map<Item, Price> byItem = new HashMap<>();
        List<Map.Entry<ResourceLocation, Price>> byTag = new ArrayList<>();
        for (Entry e : list) {
            ResourceLocation id = ResourceLocation.tryCreate(e.id);
            if (id == null) {
                continue;
            }
            Price price = new Price(e.tier, e.mana, null);
            if (e.tag) {
                byTag.add(new java.util.AbstractMap.SimpleImmutableEntry<>(id, price));
            } else {
                Item item = ForgeRegistries.ITEMS.getValue(id);
                if (item != null && item != Items.AIR) {
                    byItem.put(item, price);
                }
            }
        }
        entries = Collections.unmodifiableList(new ArrayList<>(list));
        items = byItem;
        tags = byTag;
    }

    /** Цена для пула: эссенция MA или запись датапака; null - пул такое не берёт. */
    @Nullable
    public static Price priceOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        EssenceTier essence = EssenceTier.fromItem(stack.getItem());
        if (essence != null) {
            return essence.isEnabled() ? new Price(essence.getLevel(), essence.getManaPerEssence(), essence) : null;
        }
        return custom(stack);
    }

    /** Только запись датапака (не эссенция MA). */
    @Nullable
    public static Price custom(ItemStack stack) {
        if (stack.isEmpty() || EssenceTier.fromItem(stack.getItem()) != null) {
            return null;
        }
        Price price = items.get(stack.getItem());
        if (price != null) {
            return price;
        }
        for (Map.Entry<ResourceLocation, Price> e : tags) {
            ITag<Item> tag = TagCollectionManager.getManager().getItemTags().get(e.getKey());
            if (tag != null && tag.contains(stack.getItem())) {
                return e.getValue();
            }
        }
        return null;
    }

    // --- загрузка и рассылка ------------------------------------------------------

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Loader());
    }

    public static void onDatapackSync(OnDatapackSyncEvent event) {
        ExchangePacket packet = new ExchangePacket(entries);
        if (event.getPlayer() != null) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
        } else {
            ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }

    private static final class Loader extends JsonReloadListener {

        Loader() {
            super(new Gson(), FOLDER);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, IResourceManager manager, IProfiler profiler) {
            List<Entry> list = new ArrayList<>();
            files.forEach((file, json) -> {
                try {
                    parse(json, list);
                } catch (RuntimeException e) {
                    ManaEssenceBridge.LOGGER.warn("Skipping exchange file {}: {}", file, e.getMessage());
                }
            });
            set(list);
            if (!list.isEmpty()) {
                ManaEssenceBridge.LOGGER.info("Loaded {} pool exchange entries from datapacks", list.size());
            }
        }
    }

    private static void parse(JsonElement json, List<Entry> out) {
        JsonObject root = JSONUtils.getJsonObject(json, "exchange file");
        if (root.has("required_mod") && !ModList.get().isLoaded(JSONUtils.getString(root, "required_mod"))) {
            return;
        }
        if (root.has("entries")) {
            for (JsonElement e : JSONUtils.getJsonArray(root, "entries")) {
                out.add(parseEntry(JSONUtils.getJsonObject(e, "entry")));
            }
        } else {
            out.add(parseEntry(root));
        }
    }

    private static Entry parseEntry(JsonObject o) {
        boolean tag = o.has("tag");
        String id = tag ? JSONUtils.getString(o, "tag") : JSONUtils.getString(o, "item");
        int mana = JSONUtils.getInt(o, "mana");
        int tier = JSONUtils.getInt(o, "tier", 1);
        if (mana <= 0) {
            throw new IllegalArgumentException("mana must be positive for " + id);
        }
        return new Entry(id, tag, Math.max(1, Math.min(MAX_TIER, tier)), mana);
    }
}
