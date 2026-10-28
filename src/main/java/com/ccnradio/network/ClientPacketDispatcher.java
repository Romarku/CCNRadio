package com.ccnradio.network;

import com.ccnradio.CCNRadioMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Loads the actual client packet handler only on a physical client. Reflection is used
 * deliberately so a dedicated server never tries to resolve client-only GUI/audio classes.
 */
public final class ClientPacketDispatcher {
    private ClientPacketDispatcher() {
    }

    public static void dispatch(int type, Object message) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> handler = Class.forName("com.ccnradio.client.ClientPacketHandler");
            handler.getMethod("handle", int.class, Object.class).invoke(null, type, message);
        } catch (ReflectiveOperationException e) {
            CCNRadioMod.LOGGER.error("Unable to dispatch CCNR client packet {}", type, e);
        }
    }
}
