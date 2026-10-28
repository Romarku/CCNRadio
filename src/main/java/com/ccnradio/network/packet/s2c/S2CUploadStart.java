package com.ccnradio.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record S2CUploadStart(boolean accepted, String messageKey, UUID sessionId) {
    public static void encode(S2CUploadStart message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.accepted);
        buffer.writeUtf(message.messageKey, 128);
        buffer.writeBoolean(message.sessionId != null);
        if (message.sessionId != null) {
            buffer.writeUUID(message.sessionId);
        }
    }

    public static S2CUploadStart decode(FriendlyByteBuf buffer) {
        boolean accepted = buffer.readBoolean();
        String messageKey = buffer.readUtf(128);
        UUID sessionId = buffer.readBoolean() ? buffer.readUUID() : null;
        return new S2CUploadStart(accepted, messageKey, sessionId);
    }
}
