package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.PoolExchange;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.MirrorActionPacket;
import com.frostfirebloom.manaessencebridge.MirrorPacket;
import com.frostfirebloom.manaessencebridge.ModItems;
import com.frostfirebloom.manaessencebridge.ModNetwork;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Окно Зеркала эссенции: привязанный пул - тир, где стоит, полоса маны -
 * и ряд на каждый тир эссенции, который пул принимает: цена выкупа и продажи,
 * сколько такой эссенции у игрока, кнопки «×1» и «×64». Внизу - «Отправить
 * всю эссенцию». Кнопки только просят сервер (MirrorActionPacket); после
 * каждого действия сервер присылает свежее состояние (MirrorPacket).
 */
public class MirrorScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int HEADER_H = 60;
    private static final int ROW_H = 24;

    private static final int BG = 0xF0101828;
    private static final int BORDER = 0xFF4A8FD6;
    private static final int ROW = 0xFF1C2A44;
    private static final int BAR_BG = 0xFF0A1220;
    private static final int TEXT = 0xFFE8E2C8;
    private static final int TEXT_DIM = 0xFF8FA3C0;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private MirrorPacket state;
    private int left;
    private int top;
    private int panelH;
    private Button sendAll;
    private final java.util.List<Button> buy = new java.util.ArrayList<>();
    private final java.util.List<Button> sell = new java.util.ArrayList<>();

    /** Сколько покупать и продавать за нажатие; выбор помнится, пока игра открыта. */
    private static final int[] AMOUNTS = {1, 4, 8, 16, 32, 64};
    private static int amount = 3;

    public MirrorScreen(MirrorPacket state) {
        super(Component.translatable("gui.manaessencebridge.mirror.title"));
        this.state = state;
    }

    /** Пакет с сервера: открыть окно или обновить уже открытое. */
    public static void receive(MirrorPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.open) {
            mc.setScreen(new MirrorScreen(packet));
        } else if (mc.screen instanceof MirrorScreen) {
            MirrorScreen screen = (MirrorScreen) mc.screen;
            screen.state = packet;
            screen.rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        buy.clear();
        sell.clear();
        int rows = state.offers.size();
        panelH = HEADER_H + rows * ROW_H + (state.mayBuy ? 0 : 12) + 30;
        left = (width - PANEL_W) / 2;
        top = Math.max(4, (height - panelH) / 2);
        for (int i = 0; i < rows; i++) {
            MirrorPacket.Offer offer = state.offers.get(i);
            int y = top + HEADER_H + i * ROW_H + 4;
            buy.add(addRenderableWidget(new Button(left + PANEL_W - 122, y, 56, 16, Component.translatable("gui.manaessencebridge.mirror.buy"), b -> act(MirrorActionPacket.BUY, offer.tier))));
            sell.add(addRenderableWidget(new Button(left + PANEL_W - 64, y, 56, 16, Component.translatable("gui.manaessencebridge.mirror.sell"), b -> act(MirrorActionPacket.SELL, offer.tier))));
        }
        int y = top + panelH - 26;
        addRenderableWidget(new Button(left + 8, y, 20, 20, Component.literal("<"), b -> step(-1)));
        addRenderableWidget(new Button(left + 74, y, 20, 20, Component.literal(">"), b -> step(1)));
        sendAll = addRenderableWidget(new Button(left + 100, y, PANEL_W - 108, 20, Component.empty(), b -> act(MirrorActionPacket.SEND_ALL, 0)));
    }

    private void act(int action, int tier) {
        ModNetwork.CHANNEL.sendToServer(new MirrorActionPacket(state.hand, action, tier, AMOUNTS[amount]));
    }

    private void step(int delta) {
        amount = Math.max(0, Math.min(AMOUNTS.length - 1, amount + delta));
    }

    /** Колесо мыши меняет количество. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        step(delta > 0 ? 1 : -1);
        return true;
    }

    /** Сколько эссенции, которую берёт пул, лежит у игрока (всего или одного тира). */
    private int essenceInInventory(int onlyTier) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            EssenceTier tier = stack.isEmpty() ? null : EssenceTier.fromItem(stack.getItem());
            if (tier == null) {
                // предмет из курсов датапака уходит в пул вместе со «всей эссенцией»
                PoolExchange.Price price = onlyTier > 0 ? null : PoolExchange.custom(stack);
                if (price != null && price.tier <= state.tier) {
                    total += stack.getCount();
                }
                continue;
            }
            if (onlyTier > 0 && tier.getLevel() != onlyTier) {
                continue;
            }
            for (MirrorPacket.Offer offer : state.offers) {
                if (offer.tier == tier.getLevel()) {
                    total += stack.getCount();
                    break;
                }
            }
        }
        return total;
    }

    private boolean sameDimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().location().toString().equals(state.dim);
    }

    private String where() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !sameDimension()) {
            return LedgerScreen.shortDim(state.dim);
        }
        double dx = state.pos.getX() + 0.5 - player.getX();
        double dz = state.pos.getZ() + 0.5 - player.getZ();
        double dist = Math.sqrt(player.distanceToSqr(state.pos.getX() + 0.5, state.pos.getY() + 0.5, state.pos.getZ() + 0.5));
        double rel = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - player.getYRot());
        String arrow = ARROWS[Math.floorMod((int) Math.round(rel / 45.0), 8)];
        return Component.translatable("gui.manaessencebridge.ledger.distance", (int) Math.round(dist)).getString() + " " + arrow;
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        fill(pose, left - 1, top - 1, left + PANEL_W + 1, top + panelH + 1, BORDER);
        fill(pose, left, top, left + PANEL_W, top + panelH, BG);
        font.draw(pose, title, left + 8, top + 6, TEXT);
        String where = where();
        font.draw(pose, where, left + PANEL_W - 8 - font.width(where), top + 6, TEXT);
        font.draw(pose, Component.translatable("gui.manaessencebridge.ledger.coords",
                state.pos.getX(), state.pos.getY(), state.pos.getZ(), LedgerScreen.shortDim(state.dim)),
                left + 8, top + 17, TEXT_DIM);
        if (state.links > 0) {
            Component links = Component.translatable("gui.manaessencebridge.mirror.links", state.links);
            font.draw(pose, links, left + PANEL_W - 8 - font.width(links), top + 17, TEXT_DIM);
        }

        // пул: катализатор, тир, полоса маны цвета тира
        int color = LedgerScreen.tierColor(state.tier);
        EssenceTier poolTier = EssenceTier.byLevel(state.tier);
        if (poolTier != null) {
            itemRenderer.renderGuiItem(new ItemStack(ModItems.getCatalyst(poolTier).get()), left + 8, top + 30);
        }
        Component name = poolTier == null ? Component.literal("?") : poolTier.getDisplayName();
        font.draw(pose, Component.translatable("gui.manaessencebridge.ledger.tier", state.tier, name),
                left + 28, top + 29, color);
        int barX = left + 28;
        int barY = top + 40;
        int barW = PANEL_W - 36;
        double frac = state.maxMana > 0 ? Math.min(1.0, (double) state.mana / state.maxMana) : 0;
        fill(pose, barX, barY, barX + barW, barY + 11, BAR_BG);
        fill(pose, barX, barY, barX + (int) (barW * frac), barY + 11, color);
        String manaText = CatalystItem.format(state.mana) + " / " + CatalystItem.format(state.maxMana)
                + " (" + (int) Math.round(frac * 100) + "%)";
        font.drawShadow(pose, manaText, barX + (barW - font.width(manaText)) / 2, barY + 2, 0xFFFFFFFF);

        // ряды эссенций
        for (int i = 0; i < state.offers.size(); i++) {
            MirrorPacket.Offer offer = state.offers.get(i);
            EssenceTier tier = EssenceTier.byLevel(offer.tier);
            int x = left + 6;
            int y = top + HEADER_H + i * ROW_H;
            fill(pose, x, y, left + PANEL_W - 6, y + ROW_H - 2, ROW);
            fill(pose, x, y, x + 3, y + ROW_H - 2, LedgerScreen.tierColor(offer.tier));
            if (tier != null) {
                Item essence = ForgeRegistries.ITEMS.getValue(tier.getEssenceId());
                if (essence != null) {
                    itemRenderer.renderGuiItem(new ItemStack(essence), x + 6, y + 3);
                }
                Component tierName = tier.getDisplayName();
                font.draw(pose, tierName, x + 26, y + 2, LedgerScreen.tierColor(offer.tier));
                font.draw(pose, Component.translatable("gui.manaessencebridge.mirror.have",
                        essenceInInventory(offer.tier)), x + 32 + font.width(tierName), y + 2, TEXT_DIM);
            }
            font.draw(pose, Component.translatable("gui.manaessencebridge.mirror.prices",
                    CatalystItem.format(offer.buyCost), CatalystItem.format(offer.sellPrice)),
                    x + 26, y + 12, TEXT_DIM);
        }
        if (!state.mayBuy) {
            font.draw(pose, Component.translatable("gui.manaessencebridge.mirror.private"),
                    left + 8, top + HEADER_H + state.offers.size() * ROW_H + 1, 0xFFE06C6C);
        }

        for (int i = 0; i < state.offers.size() && i < buy.size(); i++) {
            MirrorPacket.Offer offer = state.offers.get(i);
            buy.get(i).active = state.mayBuy && state.mana >= offer.buyCost;
            sell.get(i).active = essenceInInventory(offer.tier) > 0 && state.maxMana - state.mana >= offer.sellPrice;
        }
        font.draw(pose, "x" + AMOUNTS[amount], left + 51 - font.width("x" + AMOUNTS[amount]) / 2, top + panelH - 20, TEXT);
        int have = essenceInInventory(0);
        sendAll.setMessage(Component.translatable("gui.manaessencebridge.mirror.send_all", have));
        sendAll.active = have > 0 && state.mana < state.maxMana;
        super.render(pose, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
