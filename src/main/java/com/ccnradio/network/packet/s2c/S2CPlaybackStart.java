package com.ccnradio.network.packet.s2c;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record S2CPlaybackStart(BlockPos pos, UUID mediaId, String format, long startGameTime,
                               int range, int durationSeconds) {
    public static void encode(S2CPlaybackStart message, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(message.pos);
        buffer.writeUUID(message.mediaId);
        buffer.writeUtf(message.format, 8);
        buffer.writeLong(message.startGameTime);
        buffer.writeInt(message.range);
        buffer.writeInt(message.durationSeconds);
    }

    public static S2CPlaybackStart decode(FriendlyByteBuf buffer) {
        return new S2CPlaybackStart(buffer.readBlockPos(), buffer.readUUID(), buffer.readUtf(8),
                buffer.readLong(), buffer.readInt(), buffer.readInt());
    }
}
