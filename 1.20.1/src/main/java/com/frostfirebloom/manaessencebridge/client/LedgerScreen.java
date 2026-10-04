package com.frostfirebloom.manaessencebridge.client;

import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.LedgerPacket;
import com.frostfirebloom.manaessencebridge.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

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
        super(Component.translatable("gui.manaessencebridge.ledger.title"));
        this.rows = new ArrayList<>(rows);
    }

    public static void open(List<LedgerPacket.Row> rows) {
        Minecraft.getInstance().setScreen(new LedgerScreen(rows));
    }

    @Override
    protected void init() {
        visible = Mth.clamp((height - HEADER_H - 24) / CARD_H, 1, MAX_VISIBLE);
        panelH = HEADER_H + visible * CARD_H + 8;
        left = (width - PANEL_W) / 2;
        top = (height - panelH) / 2;
        String[] keys = {"gui.manaessencebridge.ledger.sort_distance", "gui.manaessencebridge.ledger.sort_tier",
                "gui.manaessencebridge.ledger.sort_mana"};
        int bw = 64;
        for (int i = 0; i < keys.length; i++) {
            final int mode = i;
            addRenderableWidget(Button.builder(Component.translatable(keys[i]), b -> setSort(mode))
                    .bounds(left + PANEL_W - 8 - (3 - i) * (bw + 2), top + 18, bw, 16).build());
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
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !sameDimension(r)) {
            return Double.MAX_VALUE;
        }
        return Math.sqrt(player.distanceToSqr(r.pos.getX() + 0.5, r.pos.getY() + 0.5, r.pos.getZ() + 0.5));
    }

    private boolean sameDimension(LedgerPacket.Row r) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.dimension().location().toString().equals(r.dim);
    }

    /** Стрелка от взгляда игрока к пулу: вперёд, вправо-вперёд и так далее. */
    private String arrow(LedgerPacket.Row r) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return "";
        }
        double dx = r.pos.getX() + 0.5 - player.getX();
        double dz = r.pos.getZ() + 0.5 - player.getZ();
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = Mth.wrapDegrees(target - player.getYRot());
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + panelH + 1, BORDER);
        g.fill(left, top, left + PANEL_W, top + panelH, BG);
        g.drawString(font, title, left + 8, top + 6, TEXT, false);
        Component count = Component.translatable("gui.manaessencebridge.ledger.count", rows.size());
        g.drawString(font, count, left + PANEL_W - 8 - font.width(count), top + 6, TEXT_DIM, false);

        if (rows.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    Component.translatable("message.manaessencebridge.ledger_empty"), PANEL_W - 24);
            int y = top + HEADER_H + 20;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                g.drawString(font, line, left + 12, y, TEXT_DIM, false);
                y += 10;
            }
        }

        int maxScroll = Math.max(0, rows.size() - visible);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            LedgerPacket.Row r = rows.get(scroll + i);
            int x = left + 6;
            int y = top + HEADER_H + i * CARD_H;
            int w = PANEL_W - 18;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CARD_H - 4;
            drawCard(g, r, x, y, w, hover);
        }
        if (maxScroll > 0) {
            int trackTop = top + HEADER_H;
            int trackH = visible * CARD_H - 4;
            int thumbH = Math.max(12, trackH * visible / rows.size());
            int thumbY = trackTop + (trackH - thumbH) * scroll / maxScroll;
            g.fill(left + PANEL_W - 9, trackTop, left + PANEL_W - 5, trackTop + trackH, BAR_BG);
            g.fill(left + PANEL_W - 9, thumbY, left + PANEL_W - 5, thumbY + thumbH, BORDER);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawCard(GuiGraphics g, LedgerPacket.Row r, int x, int y, int w, boolean hover) {
        int color = tierColor(r.tier);
        int h = CARD_H - 4;
        g.fill(x, y, x + w, y + h, hover ? CARD_HOVER : CARD);
        g.fill(x, y, x + 3, y + h, color);

        EssenceTier tier = EssenceTier.byLevel(r.tier);
        if (tier != null) {
            g.renderItem(new ItemStack(ModItems.getCatalyst(tier).get()), x + 7, y + 4);
        }
        Component name = tier == null ? Component.literal("?") : tier.getDisplayName();
        g.drawString(font, Component.translatable("gui.manaessencebridge.ledger.tier", r.tier, name),
                x + 28, y + 4, color, false);

        String where;
        if (sameDimension(r)) {
            where = Component.translatable("gui.manaessencebridge.ledger.distance",
                    (int) Math.round(distance(r))).getString() + " " + arrow(r);
        } else {
            where = shortDim(r.dim);
        }
        g.drawString(font, where, x + w - 6 - font.width(where), y + 4, TEXT, false);

        g.drawString(font, Component.translatable("gui.manaessencebridge.ledger.coords",
                r.pos.getX(), r.pos.getY(), r.pos.getZ(), shortDim(r.dim)), x + 28, y + 14, TEXT_DIM, false);

        // полоса маны цвета тира, числа - прямо на ней
        int barX = x + 28;
        int barY = y + 25;
        int barW = w - 34;
        double frac = r.maxMana > 0 ? Math.min(1.0, (double) r.mana / r.maxMana) : 0;
        g.fill(barX, barY, barX + barW, barY + 11, BAR_BG);
        g.fill(barX, barY, barX + (int) (barW * frac), barY + 11, color);
        String manaText = CatalystItem.format(r.mana) + " / " + CatalystItem.format(r.maxMana)
                + " (" + (int) Math.round(frac * 100) + "%)";
        g.drawString(font, manaText, barX + (barW - font.width(manaText)) / 2, barY + 2, 0xFFFFFFFF, true);
        g.drawString(font, Component.translatable("gui.manaessencebridge.ledger.turnover",
                CatalystItem.format(r.processed),
                Component.translatable(r.pull ? "message.manaessencebridge.ledger_on" : "message.manaessencebridge.ledger_off")),
                x + 28, y + 39, TEXT_DIM, false);
        if (!r.live) {
            String stale = Component.translatable("gui.manaessencebridge.ledger.stale").getString();
            g.drawString(font, stale, x + w - 6 - font.width(stale), y + 39, TEXT_DIM, false);
        }
        // скорость маны: растёт пул или пустеет
        if (r.hasRate) {
            String rate = (r.rate > 0 ? "+" : r.rate < 0 ? "-" : "") + CatalystItem.format(Math.abs((long) r.rate))
                    + " " + Component.translatable("gui.manaessencebridge.ledger.per_minute").getString();
            int rateColor = r.rate > 0 ? 0xFF7CD67C : r.rate < 0 ? 0xFFE06C6C : TEXT_DIM;
            g.drawString(font, rate, x + w - 6 - font.width(rate), y + 14, rateColor, false);
        }
        // последний конденсатор, бравший ману из пула
        EssenceTier condTier = EssenceTier.byLevel(r.condTier);
        if (condTier != null) {
            String reserve = r.condReserve >= 90
                    ? Component.translatable("gui.manaessencebridge.ledger.surplus").getString()
                    : r.condReserve + "%";
            g.drawString(font, Component.translatable("gui.manaessencebridge.ledger.condenser", condTier.getDisplayName(), reserve), x + 28, y + 49, TEXT_DIM, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, rows.size() - visible));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
