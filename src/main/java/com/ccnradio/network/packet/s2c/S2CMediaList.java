package com.ccnradio.network.packet.s2c;

import com.ccnradio.media.MediaEntry;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record S2CMediaList(List<MediaEntry> entries, boolean canUpload, String denyKey) {
    public static void encode(S2CMediaList message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.entries.size());
        for (MediaEntry entry : message.entries) {
            buffer.writeUUID(entry.getId());
            buffer.writeUUID(entry.getUploader());
            buffer.writeUtf(entry.getUploaderName(), 64);
            buffer.writeUtf(entry.getDisplayName(), 64);
            buffer.writeInt(entry.getDurationSeconds());
            buffer.writeLong(entry.getSize());
            buffer.writeUtf(entry.getFormat(), 8);
            buffer.writeLong(entry.getCreatedAt());
        }
        buffer.writeBoolean(message.canUpload);
        buffer.writeUtf(message.denyKey, 128);
    }

    public static S2CMediaList decode(FriendlyByteBuf buffer) {
        int count = buffer.readInt();
        if (count < 0 || count > 10000) {
            throw new DecoderException("Invalid CCNR media list size: " + count);
        }
        List<MediaEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = buffer.readUUID();
            UUID uploader = buffer.readUUID();
            String uploaderName = buffer.readUtf(64);
            String displayName = buffer.readUtf(64);
            int duration = buffer.readInt();
            long size = buffer.readLong();
            String format = buffer.readUtf(8);
            long createdAt = buffer.readLong();
            entries.add(new MediaEntry(id, uploader, uploaderName, displayName, duration, size, format, createdAt));
        }
        return new S2CMediaList(entries, buffer.readBoolean(), buffer.readUtf(128));
    }
}
