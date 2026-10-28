package com.ccnradio.network.packet.s2c;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record S2CPlaybackStop(BlockPos pos) {
    public static void encode(S2CPlaybackStop message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
    }

    public static S2CPlaybackStop decode(FriendlyByteBuf buffer) {
        return new S2CPlaybackStop(buffer.readBlockPos());
    }
}
