package com.frostfirebloom.manaessencebridge;

import net.minecraft.particles.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.world.NoteBlockEvent;
import vazkii.botania.api.subtile.RadiusDescriptor;
import vazkii.botania.api.subtile.TileEntityGeneratingFlower;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Нотоцвет: даёт ману, когда рядом (квадрат 9x9) играют нотные блоки.
 *
 * Новая нота - 6 маны. Та же нота подряд даёт всё меньше (6, 4, 2, 0), так что
 * спам одной нотой бесполезен. Разнообразие поощряется: за каждые 4 разные
 * ноты среди последних 16 - ещё +1 (до +4), за аккорд (разные ноты в один тик)
 * - ещё +2. Потолок - 30 маны в секунду на цветок.
 */
public class MelodiaBlockEntity extends TileEntityGeneratingFlower implements FlowerStatus {

    public static final int RANGE = 4;
    private static final int REPEAT_PENALTY = 2;
    private static final int CHORD_BONUS = 2;
    private static final int HISTORY = 16;
    private static final int COLOR = 0xD05AC0;

    /** Загруженные нотоцветы: событие ноты ищет их здесь, а не перебором блоков. */
    private static final Set<MelodiaBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private final int[] history = new int[HISTORY];
    private int historyPos;
    private int lastKey = -1;
    private long lastTick = -1;
    private int streak;
    private long window = -1;
    private int earned;

    public MelodiaBlockEntity() {
        super(ModBlocks.MELODIA_BE.get());
        Arrays.fill(history, -1);
    }

    /** Парящий вариант: Botania после загрузки отмечает только свои парящие блоки. */
    @Override
    public void onLoad() {
        super.onLoad();
        setFloating(FloatingManaFlowerBlock.isFloating(getBlockState()));
    }

    @Override
    public void tickFlower() {
        super.tickFlower();
        if (getWorld() instanceof ServerWorld) {
            LOADED.add(this);
        }
    }

    @Override
    public void remove() {
        super.remove();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    private void hear(ServerWorld world, int key, int pitch) {
        long now = world.getGameTime();
        if (now / 20 != window) {
            window = now / 20;
            earned = 0;
        }
        boolean chord = now == lastTick && key != lastKey;
        streak = key == lastKey && now != lastTick ? streak + 1 : 0;
        history[historyPos] = key;
        historyPos = (historyPos + 1) % HISTORY;
        lastKey = key;
        lastTick = now;

        int gain = Math.max(0, BridgeConfig.melodiaNoteMana() - REPEAT_PENALTY * streak);
        if (gain > 0) {
            gain += distinctNotes() / 4;
            if (chord) {
                gain += CHORD_BONUS;
            }
        }
        gain = Math.min(gain, Math.min(BridgeConfig.melodiaMaxPerSecond() - earned, maxMana() - getMana()));
        if (gain <= 0) {
            return;
        }
        earned += gain;
        addMana(gain);
        markDirty();
        BlockPos pos = getEffectivePos();
        // count = 0: первая «скорость» задаёт цвет ноты, как у нотного блока
        world.spawnParticle(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                0, pitch / 24.0, 0, 0, 1);
    }

    private int distinctNotes() {
        return (int) Arrays.stream(history).filter(k -> k >= 0).distinct().count();
    }

    /** Нотный блок сыграл - его слышат все нотоцветы в радиусе. */
    public static void onNote(NoteBlockEvent.Play event) {
        if (!(event.getWorld() instanceof ServerWorld) || LOADED.isEmpty()) {
            return;
        }
        ServerWorld world = (ServerWorld) event.getWorld();
        BlockPos note = event.getPos();
        int pitch = event.getVanillaNoteId();
        int key = event.getInstrument().ordinal() * 25 + pitch;
        for (MelodiaBlockEntity flower : new ArrayList<>(LOADED)) {
            if (flower.isRemoved() || flower.getWorld() != world) {
                continue;
            }
            BlockPos pos = flower.getEffectivePos();
            if (Math.abs(pos.getX() - note.getX()) <= RANGE && Math.abs(pos.getY() - note.getY()) <= RANGE
                    && Math.abs(pos.getZ() - note.getZ()) <= RANGE) {
                flower.hear(world, key, pitch);
            }
        }
    }

    /** Буфер маны под настройки конфига. */
    private static int maxMana() {
        return Math.max(600, BridgeConfig.melodiaMaxPerSecond() * 20);
    }

    /** Состояние для Jade и TOP. */
    @Override
    public java.util.List<net.minecraft.util.text.ITextComponent> statusLines() {
        long second = getWorld() == null ? -1 : getWorld().getGameTime() / 20;
        return java.util.Collections.singletonList(new net.minecraft.util.text.TranslationTextComponent("status.manaessencebridge.per_second", window == second ? earned : 0, BridgeConfig.melodiaMaxPerSecond()));
    }

    @Override
    public int getMaxMana() {
        return maxMana();
    }

    @Override
    public int getColor() {
        return COLOR;
    }

    @Override
    public RadiusDescriptor getRadius() {
        return new RadiusDescriptor.Square(getEffectivePos(), RANGE);
    }
}
