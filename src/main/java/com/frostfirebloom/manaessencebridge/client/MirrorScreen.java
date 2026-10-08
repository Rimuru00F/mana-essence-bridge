package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.PoolExchange;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.MirrorActionPacket;
import com.frostfirebloom.manaessencebridge.MirrorPacket;
import com.frostfirebloom.manaessencebridge.ModItems;
import com.frostfirebloom.manaessencebridge.ModNetwork;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
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
        super(new TranslationTextComponent("gui.manaessencebridge.mirror.title"));
        this.state = state;
    }

    /** Пакет с сервера: открыть окно или обновить уже открытое. */
    public static void receive(MirrorPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.open) {
            mc.displayGuiScreen(new MirrorScreen(packet));
        } else if (mc.currentScreen instanceof MirrorScreen) {
            MirrorScreen screen = (MirrorScreen) mc.currentScreen;
            screen.state = packet;
            // init(Minecraft, w, h) сбрасывает кнопки и строит их заново
            screen.init(mc, screen.width, screen.height);
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
            buy.add(addButton(new Button(left + PANEL_W - 122, y, 56, 16,
                    new TranslationTextComponent("gui.manaessencebridge.mirror.buy"), b -> act(MirrorActionPacket.BUY, offer.tier))));
            sell.add(addButton(new Button(left + PANEL_W - 64, y, 56, 16,
                    new TranslationTextComponent("gui.manaessencebridge.mirror.sell"), b -> act(MirrorActionPacket.SELL, offer.tier))));
        }
        int y = top + panelH - 26;
        addButton(new Button(left + 8, y, 20, 20, new StringTextComponent("<"), b -> step(-1)));
        addButton(new Button(left + 74, y, 20, 20, new StringTextComponent(">"), b -> step(1)));
        sendAll = addButton(new Button(left + 100, y, PANEL_W - 108, 20, StringTextComponent.EMPTY,
                b -> act(MirrorActionPacket.SEND_ALL, 0)));
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
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
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
        return mc.world != null && mc.world.getDimensionKey().getLocation().toString().equals(state.dim);
    }

    private String where() {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null || !sameDimension()) {
            return LedgerScreen.shortDim(state.dim);
        }
        double dx = state.pos.getX() + 0.5 - player.getPosX();
        double dz = state.pos.getZ() + 0.5 - player.getPosZ();
        double dist = Math.sqrt(player.getDistanceSq(state.pos.getX() + 0.5, state.pos.getY() + 0.5, state.pos.getZ() + 0.5));
        double rel = MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - player.rotationYaw);
        String arrow = ARROWS[Math.floorMod((int) Math.round(rel / 45.0), 8)];
        return new TranslationTextComponent("gui.manaessencebridge.ledger.distance", (int) Math.round(dist)).getString() + " " + arrow;
    }

    @Override
    public void render(MatrixStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        fill(pose, left - 1, top - 1, left + PANEL_W + 1, top + panelH + 1, BORDER);
        fill(pose, left, top, left + PANEL_W, top + panelH, BG);
        font.drawText(pose, title, left + 8, top + 6, TEXT);
        String where = where();
        font.drawString(pose, where, left + PANEL_W - 8 - font.getStringWidth(where), top + 6, TEXT);
        font.drawText(pose, new TranslationTextComponent("gui.manaessencebridge.ledger.coords",
                state.pos.getX(), state.pos.getY(), state.pos.getZ(), LedgerScreen.shortDim(state.dim)),
                left + 8, top + 17, TEXT_DIM);
        if (state.links > 0) {
            ITextComponent links = new TranslationTextComponent("gui.manaessencebridge.mirror.links", state.links);
            font.drawText(pose, links, left + PANEL_W - 8 - font.getStringPropertyWidth(links), top + 17, TEXT_DIM);
        }

        // пул: катализатор, тир, полоса маны цвета тира
        int color = LedgerScreen.tierColor(state.tier);
        EssenceTier poolTier = EssenceTier.byLevel(state.tier);
        if (poolTier != null) {
            itemRenderer.renderItemAndEffectIntoGUI(new ItemStack(ModItems.getCatalyst(poolTier).get()), left + 8, top + 30);
        }
        ITextComponent name = poolTier == null ? new StringTextComponent("?") : poolTier.getDisplayName();
        font.drawText(pose, new TranslationTextComponent("gui.manaessencebridge.ledger.tier", state.tier, name),
                left + 28, top + 29, color);
        int barX = left + 28;
        int barY = top + 40;
        int barW = PANEL_W - 36;
        double frac = state.maxMana > 0 ? Math.min(1.0, (double) state.mana / state.maxMana) : 0;
        fill(pose, barX, barY, barX + barW, barY + 11, BAR_BG);
        fill(pose, barX, barY, barX + (int) (barW * frac), barY + 11, color);
        String manaText = CatalystItem.format(state.mana) + " / " + CatalystItem.format(state.maxMana)
                + " (" + (int) Math.round(frac * 100) + "%)";
        font.drawStringWithShadow(pose, manaText, barX + (barW - font.getStringWidth(manaText)) / 2, barY + 2, 0xFFFFFFFF);

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
                    itemRenderer.renderItemAndEffectIntoGUI(new ItemStack(essence), x + 6, y + 3);
                }
                ITextComponent tierName = tier.getDisplayName();
                font.drawText(pose, tierName, x + 26, y + 2, LedgerScreen.tierColor(offer.tier));
                font.drawText(pose, new TranslationTextComponent("gui.manaessencebridge.mirror.have",
                        essenceInInventory(offer.tier)), x + 32 + font.getStringPropertyWidth(tierName), y + 2, TEXT_DIM);
            }
            font.drawText(pose, new TranslationTextComponent("gui.manaessencebridge.mirror.prices",
                    CatalystItem.format(offer.buyCost), CatalystItem.format(offer.sellPrice)),
                    x + 26, y + 12, TEXT_DIM);
        }
        if (!state.mayBuy) {
            font.drawText(pose, new TranslationTextComponent("gui.manaessencebridge.mirror.private"),
                    left + 8, top + HEADER_H + state.offers.size() * ROW_H + 1, 0xFFE06C6C);
        }

        for (int i = 0; i < state.offers.size() && i < buy.size(); i++) {
            MirrorPacket.Offer offer = state.offers.get(i);
            buy.get(i).active = state.mayBuy && state.mana >= offer.buyCost;
            sell.get(i).active = essenceInInventory(offer.tier) > 0 && state.maxMana - state.mana >= offer.sellPrice;
        }
        font.drawString(pose, "x" + AMOUNTS[amount], left + 51 - font.getStringWidth("x" + AMOUNTS[amount]) / 2, top + panelH - 20, TEXT);
        int have = essenceInInventory(0);
        sendAll.setMessage(new TranslationTextComponent("gui.manaessencebridge.mirror.send_all", have));
        sendAll.active = have > 0 && state.mana < state.maxMana;
        super.render(pose, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
