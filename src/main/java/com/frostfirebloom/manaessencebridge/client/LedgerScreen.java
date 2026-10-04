package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.LedgerPacket;
import com.frostfirebloom.manaessencebridge.ModItems;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.util.IReorderingProcessor;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Окно Мана-гроссбуха: карточка на каждый свой прокачанный пул - тир,
 * координаты, расстояние и стрелка направления, полоса маны цвета тира,
 * оборот и автозабор. Сортировка по расстоянию, тиру или мане, прокрутка
 * колёсиком. Данные присылает сервер (LedgerPacket) при открытии.
 */
public class LedgerScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int CARD_H = 64;
    private static final int MAX_VISIBLE = 4;
    private static final int HEADER_H = 40;

    private static final int BG = 0xF0101828;
    private static final int BORDER = 0xFF4A8FD6;
    private static final int CARD = 0xFF1C2A44;
    private static final int CARD_HOVER = 0xFF26385A;
    private static final int BAR_BG = 0xFF0A1220;
    private static final int TEXT = 0xFFE8E2C8;
    private static final int TEXT_DIM = 0xFF8FA3C0;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private final List<LedgerPacket.Row> rows;
    private int sort;
    private int scroll;
    private int left;
    /** Сколько карточек влезает: до четырёх, меньше - на крупном масштабе интерфейса. */
    private int visible = MAX_VISIBLE;
    private int panelH;
    private int top;

    public LedgerScreen(List<LedgerPacket.Row> rows) {
        super(new TranslationTextComponent("gui.manaessencebridge.ledger.title"));
        this.rows = new ArrayList<>(rows);
    }

    public static void open(List<LedgerPacket.Row> rows) {
        Minecraft.getInstance().displayGuiScreen(new LedgerScreen(rows));
    }

    @Override
    protected void init() {
        visible = MathHelper.clamp((height - HEADER_H - 24) / CARD_H, 1, MAX_VISIBLE);
        panelH = HEADER_H + visible * CARD_H + 8;
        left = (width - PANEL_W) / 2;
        top = (height - panelH) / 2;
        String[] keys = {"gui.manaessencebridge.ledger.sort_distance", "gui.manaessencebridge.ledger.sort_tier",
                "gui.manaessencebridge.ledger.sort_mana"};
        int bw = 64;
        for (int i = 0; i < keys.length; i++) {
            final int mode = i;
            addButton(new Button(left + PANEL_W - 8 - (3 - i) * (bw + 2), top + 18, bw, 16,
                    new TranslationTextComponent(keys[i]), b -> setSort(mode)));
        }
        resort();
    }

    private void setSort(int mode) {
        sort = mode;
        scroll = 0;
        resort();
    }

    private void resort() {
        Comparator<LedgerPacket.Row> cmp;
        if (sort == 1) {
            cmp = Comparator.comparingInt((LedgerPacket.Row r) -> -r.tier).thenComparingDouble(this::distance);
        } else if (sort == 2) {
            cmp = Comparator.comparingInt((LedgerPacket.Row r) -> -r.mana).thenComparingDouble(this::distance);
        } else {
            cmp = Comparator.comparingDouble(this::distance);
        }
        rows.sort(cmp);
    }

    /** Расстояние до пула; пулы в других измерениях - в конец списка. */
    private double distance(LedgerPacket.Row r) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null || !sameDimension(r)) {
            return Double.MAX_VALUE;
        }
        return Math.sqrt(player.getDistanceSq(r.pos.getX() + 0.5, r.pos.getY() + 0.5, r.pos.getZ() + 0.5));
    }

    private boolean sameDimension(LedgerPacket.Row r) {
        Minecraft mc = Minecraft.getInstance();
        return mc.world != null && mc.world.getDimensionKey().getLocation().toString().equals(r.dim);
    }

    /** Стрелка от взгляда игрока к пулу: вперёд, вправо-вперёд и так далее. */
    private String arrow(LedgerPacket.Row r) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        if (player == null) {
            return "";
        }
        double dx = r.pos.getX() + 0.5 - player.getPosX();
        double dz = r.pos.getZ() + 0.5 - player.getPosZ();
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = MathHelper.wrapDegrees(target - player.rotationYaw);
        int index = Math.floorMod((int) Math.round(rel / 45.0), 8);
        return ARROWS[index];
    }

    static int tierColor(int tier) {
        EssenceTier t = EssenceTier.byLevel(tier);
        if (t == null) {
            return 0xFF8FA3C0;
        }
        int r = (int) (t.particleRed() * 255) & 0xFF;
        int g = (int) (t.particleGreen() * 255) & 0xFF;
        int b = (int) (t.particleBlue() * 255) & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static String shortDim(String dim) {
        return dim.contains(":") ? dim.substring(dim.indexOf(':') + 1) : dim;
    }

    @Override
    public void render(MatrixStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        fill(pose, left - 1, top - 1, left + PANEL_W + 1, top + panelH + 1, BORDER);
        fill(pose, left, top, left + PANEL_W, top + panelH, BG);
        font.drawText(pose, title, left + 8, top + 6, TEXT);
        String count = new TranslationTextComponent("gui.manaessencebridge.ledger.count", rows.size()).getString();
        font.drawString(pose, count, left + PANEL_W - 8 - font.getStringWidth(count), top + 6, TEXT_DIM);

        if (rows.isEmpty()) {
            List<IReorderingProcessor> lines = font.trimStringToWidth(
                    new TranslationTextComponent("message.manaessencebridge.ledger_empty"), PANEL_W - 24);
            int y = top + HEADER_H + 20;
            for (IReorderingProcessor line : lines) {
                font.func_238422_b_(pose, line, left + 12, y, TEXT_DIM);
                y += 10;
            }
        }

        int maxScroll = Math.max(0, rows.size() - visible);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            LedgerPacket.Row r = rows.get(scroll + i);
            int x = left + 6;
            int y = top + HEADER_H + i * CARD_H;
            int w = PANEL_W - 18;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CARD_H - 4;
            drawCard(pose, r, x, y, w, hover);
        }
        if (maxScroll > 0) {
            int trackTop = top + HEADER_H;
            int trackH = visible * CARD_H - 4;
            int thumbH = Math.max(12, trackH * visible / rows.size());
            int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
            fill(pose, left + PANEL_W - 9, trackTop, left + PANEL_W - 5, trackTop + trackH, BAR_BG);
            fill(pose, left + PANEL_W - 9, thumbY, left + PANEL_W - 5, thumbY + thumbH, BORDER);
        }
        super.render(pose, mouseX, mouseY, partialTick);
    }

    private void drawCard(MatrixStack pose, LedgerPacket.Row r, int x, int y, int w, boolean hover) {
        int color = tierColor(r.tier);
        int h = CARD_H - 4;
        fill(pose, x, y, x + w, y + h, hover ? CARD_HOVER : CARD);
        fill(pose, x, y, x + 3, y + h, color);

        EssenceTier tier = EssenceTier.byLevel(r.tier);
        if (tier != null) {
            itemRenderer.renderItemAndEffectIntoGUI(new ItemStack(ModItems.getCatalyst(tier).get()), x + 7, y + 4);
        }
        ITextComponent name = tier == null ? new StringTextComponent("?") : tier.getDisplayName();
        font.drawText(pose, new TranslationTextComponent("gui.manaessencebridge.ledger.tier", r.tier, name),
                x + 28, y + 4, color);

        String where;
        if (sameDimension(r)) {
            where = new TranslationTextComponent("gui.manaessencebridge.ledger.distance",
                    (int) Math.round(distance(r))).getString() + " " + arrow(r);
        } else {
            where = shortDim(r.dim);
        }
        font.drawString(pose, where, x + w - 6 - font.getStringWidth(where), y + 4, TEXT);

        font.drawText(pose, new TranslationTextComponent("gui.manaessencebridge.ledger.coords",
                r.pos.getX(), r.pos.getY(), r.pos.getZ(), shortDim(r.dim)), x + 28, y + 14, TEXT_DIM);

        // полоса маны цвета тира, числа - прямо на ней
        int barX = x + 28;
        int barY = y + 25;
        int barW = w - 34;
        double frac = r.maxMana > 0 ? Math.min(1.0, (double) r.mana / r.maxMana) : 0;
        fill(pose, barX, barY, barX + barW, barY + 11, BAR_BG);
        fill(pose, barX, barY, barX + (int) (barW * frac), barY + 11, color);
        String manaText = CatalystItem.format(r.mana) + " / " + CatalystItem.format(r.maxMana)
                + " (" + (int) Math.round(frac * 100) + "%)";
        font.drawStringWithShadow(pose, manaText, barX + (barW - font.getStringWidth(manaText)) / 2, barY + 2, 0xFFFFFFFF);
        font.drawText(pose, new TranslationTextComponent("gui.manaessencebridge.ledger.turnover",
                CatalystItem.format(r.processed),
                new TranslationTextComponent(r.pull ? "message.manaessencebridge.ledger_on" : "message.manaessencebridge.ledger_off")),
                x + 28, y + 39, TEXT_DIM);
        if (!r.live) {
            String stale = new TranslationTextComponent("gui.manaessencebridge.ledger.stale").getString();
            font.drawString(pose, stale, x + w - 6 - font.getStringWidth(stale), y + 39, TEXT_DIM);
        }
        // скорость маны: растёт пул или пустеет
        if (r.hasRate) {
            String rate = (r.rate > 0 ? "+" : r.rate < 0 ? "-" : "") + CatalystItem.format(Math.abs((long) r.rate))
                    + " " + new TranslationTextComponent("gui.manaessencebridge.ledger.per_minute").getString();
            int rateColor = r.rate > 0 ? 0xFF7CD67C : r.rate < 0 ? 0xFFE06C6C : TEXT_DIM;
            font.drawString(pose, rate, x + w - 6 - font.getStringWidth(rate), y + 14, rateColor);
        }
        // последний конденсатор, бравший ману из пула
        EssenceTier condTier = EssenceTier.byLevel(r.condTier);
        if (condTier != null) {
            String reserve = r.condReserve >= 90
                    ? new TranslationTextComponent("gui.manaessencebridge.ledger.surplus").getString()
                    : r.condReserve + "%";
            font.drawText(pose, new TranslationTextComponent("gui.manaessencebridge.ledger.condenser", condTier.getDisplayName(), reserve), x + 28, y + 49, TEXT_DIM);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = MathHelper.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, rows.size() - visible));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
