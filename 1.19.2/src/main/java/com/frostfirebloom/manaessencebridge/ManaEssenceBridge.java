package com.frostfirebloom.manaessencebridge;

import com.frostfirebloom.manaessencebridge.client.ClientHudHandler;
import com.frostfirebloom.manaessencebridge.compat.TopIntegration;
import com.frostfirebloom.manaessencebridge.client.ClientTooltipHandler;
import com.frostfirebloom.manaessencebridge.client.PoolGlowHandler;
import com.frostfirebloom.manaessencebridge.client.CondenserRenderer;
import com.frostfirebloom.manaessencebridge.client.WandHudAttacher;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Frostfire Bloom - Mana Essence Bridge
 *
 * Аддон, который добавляет двусторонний конвертер между маной Botania
 * и эссенциями Mystical Agriculture всех пяти тиров.
 *
 * Логика намеренно НЕ использует Mixin - вся интеграция идёт через
 * публичный API Botania (ManaPool) и обычные forge-события, без правки
 * байткода сторонних модов.
 */
@Mod(ManaEssenceBridge.MODID)
public class ManaEssenceBridge {

    public static final String MODID = "manaessencebridge";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public ManaEssenceBridge() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Конфиг с балансом - правится в config/manaessencebridge-common.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BridgeConfig.COMMON_SPEC);

        // Регистрация предметов
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(this::enqueueImc);
        modEventBus.addListener(ManaFertilizerItem::onCommonSetup);
        ModItems.ITEMS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);

        // Регистрация капабилити
        modEventBus.addListener(ModCapabilities::register);

        // Сетевой канал: досылаем клиенту тир пула для HUD
        ModNetwork.register();

        // Обработчики игровых событий
        MinecraftForge.EVENT_BUS.register(new CatalystInteractionHandler());
        MinecraftForge.EVENT_BUS.register(new CapabilityAttachHandler());
        MinecraftForge.EVENT_BUS.register(new ChunkSyncHandler());
        MinecraftForge.EVENT_BUS.register(new GateSyncHandler());
        MinecraftForge.EVENT_BUS.register(new PoolDropHandler());
        MinecraftForge.EVENT_BUS.register(new PoolAutoPull());
        MinecraftForge.EVENT_BUS.register(new CakeDay());
        MinecraftForge.EVENT_BUS.addListener(WardeniaBlockEntity::onTrample);
        MinecraftForge.EVENT_BUS.addListener(WardeniaBlockEntity::onExplosionStart);
        MinecraftForge.EVENT_BUS.addListener(WardeniaBlockEntity::onExplosionDetonate);
        // Пояс Стражении - после цветка: взрыв, погашенный цветком, сюда уже не дойдёт
        MinecraftForge.EVENT_BUS.addListener(WardeniaBeltItem::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(WardeniaBeltItem::onExplosionStart);
        MinecraftForge.EVENT_BUS.addListener(WardeniaBeltItem::onTrample);
        MinecraftForge.EVENT_BUS.addListener(MelodiaBlockEntity::onNote);
        MinecraftForge.EVENT_BUS.addListener(BoltbloomBlockEntity::onEntityJoin);
        MinecraftForge.EVENT_BUS.addListener(BirthdayCheerEffect::onManaDiscount);
        MinecraftForge.EVENT_BUS.addListener(BridgeCommand::register);
        MinecraftForge.EVENT_BUS.addListener(PoolLedger::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(PoolNetwork::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(PoolExchange::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(PoolExchange::onDatapackSync);

        // The One Probe узнаёт о нас через IMC. Проверка ModList не только
        // ради вежливости: без неё класс интеграции подтянул бы за собой
        // классы TOP, которых в сборке может не быть.
        // Jade же находит свой плагин сама, по аннотации.
        if (ModList.get().isLoaded("theoneprobe")) {
            TopIntegration.register();
        }

        // HUD существует только на клиенте - на сервере класс даже не грузится
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> {
                    MinecraftForge.EVENT_BUS.register(new ClientHudHandler());
                    MinecraftForge.EVENT_BUS.register(new ClientTooltipHandler());
                    MinecraftForge.EVENT_BUS.register(new PoolGlowHandler());
                    MinecraftForge.EVENT_BUS.register(new WandHudAttacher());
                    modEventBus.addListener(CondenserRenderer::register);
                    modEventBus.addListener(com.frostfirebloom.manaessencebridge.client.ManaFlowerRenderer::register);
                });

        LOGGER.info("Mana Essence Bridge loaded - the bridge between Botania and Mystical Agriculture is ready");
    }

    /**
     * Carry On перехватывает Shift+ПКМ пустыми руками по любому блоку с
     * блок-сущностью и уносит блок - наш конденсатор этот клик просто не
     * получал. Просим Carry On его не трогать; если Carry On в сборке нет,
     * сообщение молча пропадёт. Пулы Botania у Carry On и так в чёрном списке.
     */
    private void enqueueImc(InterModEnqueueEvent event) {
        // слот пояса для Пояса Стражении (Botania тоже его заводит - повтор безвреден)
        InterModComms.sendTo(top.theillusivec4.curios.api.CuriosApi.MODID,
                top.theillusivec4.curios.api.SlotTypeMessage.REGISTER_TYPE,
                () -> top.theillusivec4.curios.api.SlotTypePreset.BELT.getMessageBuilder().build());
        InterModComms.sendTo("carryon", "blacklistBlock",
                () -> ModBlocks.ESSENCE_CONDENSER.getId().toString());
    }
}
