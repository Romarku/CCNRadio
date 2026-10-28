package com.ccnradio.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record S2CMediaDownloadComplete(UUID mediaId) {
    public static void encode(S2CMediaDownloadComplete message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.mediaId);
    }

    public static S2CMediaDownloadComplete decode(FriendlyByteBuf buffer) {
        return new S2CMediaDownloadComplete(buffer.readUUID());
    }
}
