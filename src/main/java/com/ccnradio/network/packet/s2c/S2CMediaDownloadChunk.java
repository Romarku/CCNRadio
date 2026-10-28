package com.ccnradio.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record S2CMediaDownloadChunk(UUID mediaId, int index, byte[] data) {
    public static void encode(S2CMediaDownloadChunk message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.mediaId);
        buffer.writeInt(message.index);
        buffer.writeByteArray(message.data);
    }

    public static S2CMediaDownloadChunk decode(FriendlyByteBuf buffer) {
        return new S2CMediaDownloadChunk(buffer.readUUID(), buffer.readInt(), buffer.readByteArray(262144));
    }
}
