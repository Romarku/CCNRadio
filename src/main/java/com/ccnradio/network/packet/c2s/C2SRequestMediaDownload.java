package com.ccnradio.network.packet.c2s;

import com.ccnradio.media.DownloadManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record C2SRequestMediaDownload(UUID mediaId) {
    public static void encode(C2SRequestMediaDownload message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.mediaId);
    }

    public static C2SRequestMediaDownload decode(FriendlyByteBuf buffer) {
        return new C2SRequestMediaDownload(buffer.readUUID());
    }

    public static void handle(C2SRequestMediaDownload message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            DownloadManager.enqueue(player, message.mediaId);
        }
    }
}
