package com.ccnradio.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;

public record S2CBurnResult(boolean success, String messageKey) {
    public static void encode(S2CBurnResult message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.success);
        buffer.writeUtf(message.messageKey, 128);
    }

    public static S2CBurnResult decode(FriendlyByteBuf buffer) {
        return new S2CBurnResult(buffer.readBoolean(), buffer.readUtf(128));
    }
}
