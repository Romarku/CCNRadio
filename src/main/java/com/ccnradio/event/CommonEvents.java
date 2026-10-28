package com.ccnradio.event;

import com.ccnradio.CCNRadioMod;
import com.ccnradio.block.TapePlayerBlock;
import com.ccnradio.command.CCNRadioCommands;
import com.ccnradio.media.DownloadManager;
import com.ccnradio.media.MediaStorage;
import com.ccnradio.media.UploadManager;
import com.ccnradio.media.UploadSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod.EventBusSubscriber(modid = CCNRadioMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CCNRadioCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MediaStorage.init(FMLPaths.GAMEDIR.get());
        UploadSettings.load(FMLPaths.GAMEDIR.get());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        UploadManager.shutdown();
        DownloadManager.shutdown();
        MediaStorage.shutdown();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            UploadManager.tick();
            DownloadManager.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UploadManager.cancel(player.getUUID());
            DownloadManager.cancel(player.getUUID());
        }
    }

    /**
     * Vanilla routes sneak-clicks with a non-empty hand to the held item before the block
     * sees the interaction, so the tape player's sneak controls only worked with both hands
     * empty. Force the block interaction for non-placeable items so sneak-clicking still
     * stops playback (or opens the settings GUI with the configurator) while holding
     * anything. Holding a block keeps the vanilla behaviour and places it.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) {
            return;
        }
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof TapePlayerBlock)) {
            return;
        }
        if (event.getItemStack().getItem() instanceof BlockItem) {
            return;
        }
        event.setUseBlock(Event.Result.ALLOW);
    }
}
