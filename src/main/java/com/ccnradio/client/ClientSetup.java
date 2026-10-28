package com.ccnradio.client;

import com.ccnradio.CCNRadioMod;
import com.ccnradio.client.screen.TapePlayerScreen;
import com.ccnradio.client.screen.TapeRecorderScreen;
import com.ccnradio.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CCNRadioMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.TAPE_RECORDER.get(), TapeRecorderScreen::new);
            MenuScreens.register(ModMenus.TAPE_PLAYER.get(), TapePlayerScreen::new);
        });
    }
}
