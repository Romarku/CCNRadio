package com.ccnradio.media;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Metadata for one uploaded audio file. Audio bytes live next to a JSON sidecar in
 * {@code <game dir>/ccnradio/media/<uploader uuid>/}.
 */
public final class MediaEntry {
    private final UUID id;
    private final UUID uploader;
    private final String uploaderName;
    private final String displayName;
    private final int durationSeconds;
    private final long size;
    private final String format;
    private final long createdAt;

    public MediaEntry(UUID id, UUID uploader, String uploaderName, String displayName,
                      int durationSeconds, long size, String format, long createdAt) {
        this.id = id;
        this.uploader = uploader;
        this.uploaderName = uploaderName;
        this.displayName = displayName;
        this.durationSeconds = durationSeconds;
        this.size = size;
        this.format = format;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUploader() {
        return uploader;
    }

    public String getUploaderName() {
        return uploaderName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public long getSize() {
        return size;
    }

    public String getFormat() {
        return format;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("uploader", uploader.toString());
        json.addProperty("uploaderName", uploaderName);
        json.addProperty("displayName", displayName);
        json.addProperty("durationSeconds", durationSeconds);
        json.addProperty("size", size);
        json.addProperty("format", format);
        json.addProperty("createdAt", createdAt);
        return json;
    }

    /**
     * @return the parsed entry, or {@code null} when the JSON is incomplete/corrupt.
     */
    public static MediaEntry fromJson(JsonObject json) {
        try {
            UUID id = UUID.fromString(json.get("id").getAsString());
            UUID uploader = UUID.fromString(json.get("uploader").getAsString());
            String uploaderName = json.has("uploaderName") ? json.get("uploaderName").getAsString() : "Unknown";
            String displayName = json.has("displayName") ? json.get("displayName").getAsString() : "Audio";
            uploaderName = limit(uploaderName, 64);
            displayName = limit(displayName, 64);
            int duration = json.get("durationSeconds").getAsInt();
            long size = json.get("size").getAsLong();
            String format = json.get("format").getAsString();
            long createdAt = json.has("createdAt") ? json.get("createdAt").getAsLong() : 0L;
            if (duration <= 0 || size <= 0 || (!"ogg".equals(format) && !"wav".equals(format))) {
                return null;
            }
            return new MediaEntry(id, uploader, uploaderName, displayName, duration, size, format, createdAt);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String limit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
