package com.ccnradio.server;

import com.ccnradio.media.MediaStorage;
import com.ccnradio.media.UploadSettings;
import com.ccnradio.network.ModNetwork;
import com.ccnradio.network.packet.s2c.S2CMediaList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class MediaListService {
    private static final int MAX_ENTRIES = 2000;

    private MediaListService() {
    }

    public static void send(ServerPlayer player) {
        List<com.ccnradio.media.MediaEntry> entries = MediaStorage.list();
        if (entries.size() > MAX_ENTRIES) {
            entries = entries.subList(0, MAX_ENTRIES);
        }
        ModNetwork.sendToPlayer(player, new S2CMediaList(entries,
                UploadSettings.canUpload(player), UploadSettings.denyMessageKey()));
    }

    public static void broadcastToOpenRecorders(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof com.ccnradio.menu.TapeRecorderMenu) {
                send(player);
            }
        }
    }
}
