package com.ccnradio.media;

import com.ccnradio.CCNRadioMod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * Small, dependency-free audio header parser used for upload validation. Only the
 * duration and container format are needed; decoding happens on the client.
 */
public final class AudioMetadata {
    private AudioMetadata() {
    }

    public record AudioInfo(String format, int durationSeconds, long size) {
    }

    public static boolean isSupportedFileName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ogg") || lower.endsWith(".wav");
    }

    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    public static Optional<AudioInfo> inspect(Path path) {
        try {
            return inspect(Files.readAllBytes(path), path.getFileName().toString());
        } catch (IOException e) {
            CCNRadioMod.LOGGER.warn("Unable to read audio file for inspection: {}", path, e);
            return Optional.empty();
        }
    }

    public static Optional<AudioInfo> inspect(byte[] data, String fileName) {
        if (data == null || data.length < 12) {
            return Optional.empty();
        }
        if (startsWith(data, 0, "OggS")) {
            return parseOgg(data);
        }
        if (startsWith(data, 0, "RIFF") && startsWith(data, 8, "WAVE")) {
            return parseWav(data);
        }
        // Unknown container: use the extension only to produce a useful error later.
        return Optional.empty();
    }

    private static Optional<AudioInfo> parseOgg(byte[] data) {
        int sampleRate = -1;
        long lastGranule = -1;
        int position = 0;
        while (position + 27 <= data.length) {
            if (!startsWith(data, position, "OggS")) {
                position++;
                continue;
            }
            long granule = readLongLE(data, position + 6);
            int segments = data[position + 26] & 0xFF;
            if (position + 27 + segments > data.length) {
                break;
            }
            int bodyLength = 0;
            for (int i = 0; i < segments; i++) {
                bodyLength += data[position + 27 + i] & 0xFF;
            }
            int bodyStart = position + 27 + segments;
            if (bodyLength < 0 || bodyStart + bodyLength > data.length) {
                break;
            }
            if (sampleRate < 0 && bodyLength >= 16 && (data[bodyStart] & 0xFF) == 1) {
                if (startsWith(data, bodyStart + 1, "vorbis")) {
                    int channels = data[bodyStart + 11] & 0xFF;
                    int rate = readIntLE(data, bodyStart + 12);
                    if (channels > 0 && rate > 0) {
                        sampleRate = rate;
                    }
                }
            }
            // Granule -1 means "no packet finishes on this page".
            if (granule > 0) {
                lastGranule = granule;
            }
            position = bodyStart + bodyLength;
        }
        if (sampleRate <= 0 || lastGranule <= 0) {
            return Optional.empty();
        }
        long seconds = lastGranule / sampleRate;
        if (seconds <= 0 || seconds > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        return Optional.of(new AudioInfo("ogg", (int) seconds, data.length));
    }

    private static Optional<AudioInfo> parseWav(byte[] data) {
        int position = 12;
        int sampleRate = 0;
        int byteRate = 0;
        long dataSize = -1;
        while (position + 8 <= data.length) {
            String chunkId = new String(data, position, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long chunkSize = readIntLE(data, position + 4) & 0xFFFFFFFFL;
            int bodyStart = position + 8;
            if ("fmt ".equals(chunkId) && chunkSize >= 16 && bodyStart + 16 <= data.length) {
                int audioFormat = readShortLE(data, bodyStart);
                int channels = readShortLE(data, bodyStart + 2);
                sampleRate = readIntLE(data, bodyStart + 4);
                byteRate = readIntLE(data, bodyStart + 8);
                int bits = readShortLE(data, bodyStart + 14);
                if (byteRate <= 0 && channels > 0 && sampleRate > 0 && bits > 0) {
                    byteRate = sampleRate * channels * bits / 8;
                }
                if (audioFormat <= 0) {
                    return Optional.empty();
                }
            } else if ("data".equals(chunkId)) {
                long remaining = data.length - (long) bodyStart;
                dataSize = chunkSize <= remaining && chunkSize > 0 ? chunkSize : remaining;
                break;
            }
            long next = bodyStart + chunkSize + (chunkSize & 1L);
            if (next <= position || next > data.length) {
                break;
            }
            position = (int) next;
        }
        if (sampleRate <= 0 || byteRate <= 0 || dataSize <= 0) {
            return Optional.empty();
        }
        long seconds = dataSize / byteRate;
        if (seconds <= 0 || seconds > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        return Optional.of(new AudioInfo("wav", (int) seconds, data.length));
    }

    private static boolean startsWith(byte[] data, int offset, String text) {
        if (offset < 0 || offset + text.length() > data.length) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if ((data[offset + i] & 0xFF) != text.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int readShortLE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static long readLongLE(byte[] data, int offset) {
        return (readIntLE(data, offset) & 0xFFFFFFFFL)
                | ((long) readIntLE(data, offset + 4) << 32);
    }
}
