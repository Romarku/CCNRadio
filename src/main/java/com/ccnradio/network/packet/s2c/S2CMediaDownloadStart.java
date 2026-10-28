package com.ccnradio.network.packet.s2c;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

public record S2CMediaDownloadStart(UUID mediaId, String format, int totalBytes, int chunkSize, int totalChunks) {
    public static void encode(S2CMediaDownloadStart message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.mediaId);
        buffer.writeUtf(message.format, 8);
        buffer.writeInt(message.totalBytes);
        buffer.writeInt(message.chunkSize);
        buffer.writeInt(message.totalChunks);
    }

    public static S2CMediaDownloadStart decode(FriendlyByteBuf buffer) {
        return new S2CMediaDownloadStart(buffer.readUUID(), buffer.readUtf(8), buffer.readInt(),
                buffer.readInt(), buffer.readInt());
    }
}
