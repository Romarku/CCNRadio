package com.ccnradio;

import com.ccnradio.registry.ModBlockEntities;
import com.ccnradio.registry.ModBlocks;
import com.ccnradio.registry.ModCreativeTabs;
import com.ccnradio.registry.ModItems;
import com.ccnradio.registry.ModMenus;
import com.ccnradio.network.ModNetwork;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Mod entry point. All feature code lives in dedicated packages; this class only wires
 * registries, configuration and the network channel together.
 */
@Mod(CCNRadioMod.MODID)
public final class CCNRadioMod {
    public static final String MODID = "ccnradio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CCNRadioMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);

        ModNetwork.register();
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        modBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("CCNR: Easy Tape Recorder initialized");
    }
}
