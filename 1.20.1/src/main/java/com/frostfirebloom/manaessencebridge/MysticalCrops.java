package com.frostfirebloom.manaessencebridge;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Работа с культурами Mystical Agriculture без зависимости от её кода:
 * культуры MA - обычные ванильные культуры (наследники CropBlock), так что
 * узнаём их по пространству имён, а растим и собираем ванильными методами.
 */
final class MysticalCrops {

    private static final String MA = "mysticalagriculture";
    /** Mystical Agradditions: культуры шестого тира - те же культуры MA. */
    private static final String AGRADDITIONS = "mysticalagradditions";

    /** Тир культуры по предмету ресурсной эссенции; 0 - не ресурсная эссенция MA. */
    private static final Map<Item, Integer> RESOURCE_TIERS = new ConcurrentHashMap<>();

    private MysticalCrops() {
    }

    static boolean isCrop(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof CropBlock)) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id != null && (MA.equals(id.getNamespace()) || AGRADDITIONS.equals(id.getNamespace()));
    }

    static boolean isGrowing(BlockState state) {
        return isCrop(state) && !((CropBlock) state.getBlock()).isMaxAge(state);
    }

    static boolean isRipe(BlockState state) {
        return isCrop(state) && ((CropBlock) state.getBlock()).isMaxAge(state);
    }

    /**
     * Защита от бесконечной маны: одну культуру ускоряют не больше MAX_STACK
     * Мистикарнаций. Из всех, что до неё дотягиваются, работают первые по
     * координатам - всегда одни и те же, без мигания. Остальные эту культуру
     * пропускают и ману на ней не тратят.
     */
    static boolean isAmongFirstBoosters(ServerLevel level, BlockPos crop, BlockPos self, int range, int maxStack) {
        List<BlockPos> boosters = new ArrayList<>();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = crop.offset(dx, dy, dz);
                    if (level.isLoaded(p) && level.getBlockEntity(p) instanceof MysticarnationBlockEntity) {
                        boosters.add(p);
                    }
                }
            }
        }
        if (boosters.size() <= maxStack) {
            return true;
        }
        boosters.sort(Comparator.comparingLong(BlockPos::asLong));
        return boosters.subList(0, maxStack).contains(self);
    }

    /**
     * Собрать созревшую культуру и пересадить её, как это сделал бы игрок:
     * одно семя из урожая уходит на посадку, остальное падает рядом.
     */
    static void harvestAndReplant(ServerLevel level, BlockPos pos, BlockState state) {
        CropBlock crop = (CropBlock) state.getBlock();
        List<ItemStack> drops = Block.getDrops(state, level, pos, null);
        ItemStack seed = crop.getCloneItemStack(level, pos, state);
        if (!seed.isEmpty()) {
            for (ItemStack drop : drops) {
                if (!drop.isEmpty() && drop.getItem() == seed.getItem()) {
                    drop.shrink(1);
                    break;
                }
            }
        }
        level.setBlock(pos, crop.getStateForAge(0), Block.UPDATE_ALL);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
    }

    /**
     * Тир культуры, из которой выросла ресурсная эссенция MA (железная, алмазная...),
     * или 0, если это не она. У предмета ресурсной эссенции MA есть getCrop(),
     * у культуры - getTier(), у тира - getValue(): так во всех версиях MA,
     * поэтому зовём их по имени и не держим MA в зависимостях сборки.
     * Тировые эссенции (инфериум и т.д.) сюда не попадают - у них нет getCrop().
     */
    static int resourceTier(Item item) {
        return RESOURCE_TIERS.computeIfAbsent(item, MysticalCrops::lookupTier);
    }

    private static int lookupTier(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !(MA.equals(id.getNamespace()) || "mysticalagradditions".equals(id.getNamespace()))
                || !id.getPath().endsWith("_essence")) {
            return 0;
        }
        try {
            Object crop = item.getClass().getMethod("getCrop").invoke(item);
            if (crop == null) {
                return 0;
            }
            Object tier = crop.getClass().getMethod("getTier").invoke(crop);
            if (tier == null) {
                return 0;
            }
            Object value = tier.getClass().getMethod("getValue").invoke(tier);
            return value instanceof Integer ? Math.max((Integer) value, 0) : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return 0;
        }
    }

    private static final Map<Block, Integer> CROP_TIERS = new ConcurrentHashMap<>();

    /** Тир культуры MA (или Agradditions) по её блоку; 0 - не узнали. Тем же путём getCrop().getTier().getValue(). */
    static int cropTier(BlockState state) {
        return CROP_TIERS.computeIfAbsent(state.getBlock(), MysticalCrops::lookupCropTier);
    }

    private static int lookupCropTier(Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null || !(MA.equals(id.getNamespace()) || "mysticalagradditions".equals(id.getNamespace()))) {
            return 0;
        }
        try {
            Object crop = block.getClass().getMethod("getCrop").invoke(block);
            if (crop == null) {
                return 0;
            }
            Object tier = crop.getClass().getMethod("getTier").invoke(crop);
            if (tier == null) {
                return 0;
            }
            Object value = tier.getClass().getMethod("getValue").invoke(tier);
            return value instanceof Integer ? Math.max((Integer) value, 0) : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return 0;
        }
    }

    /** Подрастить культуру MA на stages стадий, не выше спелой; true - подросла. */
    static boolean advance(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, int stages) {
        // По правилам MA: культуре может быть нужен крукс под ней или свой биом -
        // MA проверяет это в isValidBonemealTarget/canGrow, как и для Мистикарнации.
        if (!isCrop(state)) {
            return false;
        }
        CropBlock crop = (CropBlock) state.getBlock();
        if (!isGrowing(state) || !crop.isValidBonemealTarget(level, pos, state, false)) {
            return false;
        }
        int age = Math.min(crop.getMaxAge(), crop.getAge(state) + stages);
        return level.setBlock(pos, crop.getStateForAge(age), 2);
    }
}
