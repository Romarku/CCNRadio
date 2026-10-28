package com.ccnradio.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;

public record S2CDeleteResult(boolean success, String messageKey) {
    public static void encode(S2CDeleteResult message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.success);
        buffer.writeUtf(message.messageKey, 128);
    }

    public static S2CDeleteResult decode(FriendlyByteBuf buffer) {
        return new S2CDeleteResult(buffer.readBoolean(), buffer.readUtf(128));
    }
}
