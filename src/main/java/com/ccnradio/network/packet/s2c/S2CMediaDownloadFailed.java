package com.ccnradio.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record S2CMediaDownloadFailed(UUID mediaId, String messageKey) {
    public static void encode(S2CMediaDownloadFailed message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.mediaId);
        buffer.writeUtf(message.messageKey, 128);
    }

    public static S2CMediaDownloadFailed decode(FriendlyByteBuf buffer) {
        return new S2CMediaDownloadFailed(buffer.readUUID(), buffer.readUtf(128));
    }
}
