package com.frostfirebloom.manaessencebridge;

import com.frostfirebloom.manaessencebridge.client.ClientHudHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
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
 * Логика намеренно НЕ использует Mixin (учитывая опыт крашей в сборке
 * из-за конфликтов миксинов Create/Rubidium/Cold Sweat) - вся интеграция
 * идёт через официальный публичный API Botania (IManaPool, ISparkAttachable)
 * и обычные forge-события, без правки байткода сторонних модов.
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
        ModItems.ITEMS.register(modEventBus);

        // Регистрация обработчика способности (капабилити) для маны-пулов
        modEventBus.addListener(ModCapabilities::onCommonSetup);

        // Сетевой канал: досылаем клиенту тир пула для HUD
        ModNetwork.register();

        // Обработчики игровых событий
        MinecraftForge.EVENT_BUS.register(new CatalystInteractionHandler());
        MinecraftForge.EVENT_BUS.register(new CapabilityAttachHandler());
        MinecraftForge.EVENT_BUS.register(new ChunkSyncHandler());
        MinecraftForge.EVENT_BUS.register(new GateSyncHandler());
        MinecraftForge.EVENT_BUS.register(new PoolDropHandler());

        // HUD существует только на клиенте - на сервере класс даже не грузится
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> MinecraftForge.EVENT_BUS.register(new ClientHudHandler()));

        LOGGER.info("Mana Essence Bridge loaded - the bridge between Botania and Mystical Agriculture is ready");
    }
}
