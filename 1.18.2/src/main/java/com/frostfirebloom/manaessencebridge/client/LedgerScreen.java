package com.frostfirebloom.manaessencebridge.client;

import net.minecraft.ChatFormatting;

import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import com.frostfirebloom.manaessencebridge.CatalystItem;
import com.frostfirebloom.manaessencebridge.EssenceTier;
import com.frostfirebloom.manaessencebridge.LedgerPacket;
import com.frostfirebloom.manaessencebridge.ModItems;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
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
    /** Сеть пулов: подпись и рамка связанных карточек. */
    private static final int LINK_COLOR = 0xFF7FE0FF;
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
        super(new TranslatableComponent("gui.manaessencebridge.ledger.title"));
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
            addRenderableWidget(new Button(left + PANEL_W - 8 - (3 - i) * (bw + 2), top + 18, bw, 16,
                    new TranslatableComponent(keys[i]), b -> setSort(mode)));
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
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        fill(pose, left - 1, top - 1, left + PANEL_W + 1, top + panelH + 1, BORDER);
        fill(pose, left, top, left + PANEL_W, top + panelH, BG);
        font.draw(pose, title, left + 8, top + 6, TEXT);
        Component count = new TranslatableComponent("gui.manaessencebridge.ledger.count", rows.size());
        font.draw(pose, count, left + PANEL_W - 8 - font.width(count), top + 6, TEXT_DIM);

        if (rows.isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                    new TranslatableComponent("message.manaessencebridge.ledger_empty"), PANEL_W - 24);
            int y = top + HEADER_H + 20;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                font.draw(pose, line, left + 12, y, TEXT_DIM);
                y += 10;
            }
        }

        int maxScroll = Math.max(0, rows.size() - visible);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        LedgerPacket.Row hovered = null;
        for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
            LedgerPacket.Row r = rows.get(scroll + i);
            int x = left + 6;
            int y = top + HEADER_H + i * CARD_H;
            int w = PANEL_W - 18;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + CARD_H - 4;
            drawCard(pose, r, x, y, w, hover);
            if (hover) {
                hovered = r;
            }
        }
        // сеть пулов: при наведении подсвечиваем карточки связанных пулов
        if (hovered != null && hovered.links.length > 0) {
            for (int i = 0; i < visible && scroll + i < rows.size(); i++) {
                LedgerPacket.Row r = rows.get(scroll + i);
                if (r != hovered && r.dim.equals(hovered.dim) && linked(hovered, r)) {
                    outline(pose, left + 6, top + HEADER_H + i * CARD_H, PANEL_W - 18, CARD_H - 4);
                }
            }
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
        if (hovered != null && hovered.links.length > 0) {
            renderComponentTooltip(pose, networkLines(hovered), mouseX, mouseY);
        }
    }

    private void drawCard(PoseStack pose, LedgerPacket.Row r, int x, int y, int w, boolean hover) {
        int color = tierColor(r.tier);
        int h = CARD_H - 4;
        fill(pose, x, y, x + w, y + h, hover ? CARD_HOVER : CARD);
        fill(pose, x, y, x + 3, y + h, color);

        EssenceTier tier = EssenceTier.byLevel(r.tier);
        if (tier != null) {
            itemRenderer.renderGuiItem(new ItemStack(ModItems.getCatalyst(tier).get()), x + 7, y + 4);
        }
        Component name = tier == null ? new TextComponent("?") : tier.getDisplayName();
        font.draw(pose, new TranslatableComponent("gui.manaessencebridge.ledger.tier", r.tier, name),
                x + 28, y + 4, color);

        String where;
        if (sameDimension(r)) {
            where = new TranslatableComponent("gui.manaessencebridge.ledger.distance",
                    (int) Math.round(distance(r))).getString() + " " + arrow(r);
        } else {
            where = shortDim(r.dim);
        }
        font.draw(pose, where, x + w - 6 - font.width(where), y + 4, TEXT);

        font.draw(pose, new TranslatableComponent("gui.manaessencebridge.ledger.coords",
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
        font.drawShadow(pose, manaText, barX + (barW - font.width(manaText)) / 2, barY + 2, 0xFFFFFFFF);
        font.draw(pose, new TranslatableComponent("gui.manaessencebridge.ledger.turnover",
                CatalystItem.format(r.processed),
                new TranslatableComponent(r.pull ? "message.manaessencebridge.ledger_on" : "message.manaessencebridge.ledger_off")),
                x + 28, y + 39, TEXT_DIM);
        if (!r.live) {
            String stale = new TranslatableComponent("gui.manaessencebridge.ledger.stale").getString();
            font.draw(pose, stale, x + w - 6 - font.width(stale), y + 39, TEXT_DIM);
        }
        // скорость маны: растёт пул или пустеет
        if (r.hasRate) {
            String rate = (r.rate > 0 ? "+" : r.rate < 0 ? "-" : "") + CatalystItem.format(Math.abs((long) r.rate))
                    + " " + new TranslatableComponent("gui.manaessencebridge.ledger.per_minute").getString();
            int rateColor = r.rate > 0 ? 0xFF7CD67C : r.rate < 0 ? 0xFFE06C6C : TEXT_DIM;
            font.draw(pose, rate, x + w - 6 - font.width(rate), y + 14, rateColor);
        }
        // последний конденсатор, бравший ману из пула
        EssenceTier condTier = EssenceTier.byLevel(r.condTier);
        if (condTier != null) {
            String reserve = r.condReserve >= 90
                    ? new TranslatableComponent("gui.manaessencebridge.ledger.surplus").getString()
                    : r.condReserve + "%";
            font.draw(pose, new TranslatableComponent("gui.manaessencebridge.ledger.condenser", condTier.getDisplayName(), reserve), x + 28, y + 49, TEXT_DIM);
        }
        if (r.links.length > 0) {
            String net = new TranslatableComponent("gui.manaessencebridge.ledger.links", r.links.length).getString();
            font.draw(pose, net, x + w - 6 - font.width(net), y + 49, LINK_COLOR);
        }
    }

    private static boolean linked(LedgerPacket.Row a, LedgerPacket.Row b) {
        long packed = b.pos.asLong();
        for (long l : a.links) {
            if (l == packed) {
                return true;
            }
        }
        return false;
    }

    private static void outline(PoseStack pose, int x, int y, int w, int h) {
        fill(pose, x - 1, y - 1, x + w + 1, y, LINK_COLOR);
        fill(pose, x - 1, y + h, x + w + 1, y + h + 1, LINK_COLOR);
        fill(pose, x - 1, y, x, y + h, LINK_COLOR);
        fill(pose, x + w, y, x + w + 1, y + h, LINK_COLOR);
    }

    /** Подсказка: связи пула с заполненностью тех, что есть в гроссбухе. */
    private java.util.List<Component> networkLines(LedgerPacket.Row row) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(new TranslatableComponent("gui.manaessencebridge.ledger.network", row.links.length).withStyle(ChatFormatting.AQUA));
        for (long packed : row.links) {
            net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.of(packed);
            String fill = "?";
            for (LedgerPacket.Row r : rows) {
                if (r.dim.equals(row.dim) && r.pos.equals(p) && r.maxMana > 0) {
                    fill = (int) Math.round(100.0 * r.mana / r.maxMana) + "%";
                    break;
                }
            }
            lines.add(new TranslatableComponent("gui.manaessencebridge.ledger.network_line", p.getX(), p.getY(), p.getZ(), fill)
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
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
