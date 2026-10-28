package com.ccnradio.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record S2CUploadResult(boolean success, String messageKey, UUID mediaId) {
    public static void encode(S2CUploadResult message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.success);
        buffer.writeUtf(message.messageKey, 128);
        buffer.writeBoolean(message.mediaId != null);
        if (message.mediaId != null) {
            buffer.writeUUID(message.mediaId);
        }
    }

    public static S2CUploadResult decode(FriendlyByteBuf buffer) {
        boolean success = buffer.readBoolean();
        String messageKey = buffer.readUtf(128);
        UUID mediaId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new S2CUploadResult(success, messageKey, mediaId);
    }
}
