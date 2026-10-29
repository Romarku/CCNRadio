package com.ccnradio.client;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Client-side mirror of server media files. A tape played once is cached here, so
 * leaving and re-entering the playback range does not download it again.
 */
public final class AudioCache {
    private static final String[] KNOWN_FORMATS = {"ogg", "wav", "mp3"};

    private static Path directory;

    private AudioCache() {
    }

    public static Path directory() {
        if (directory == null) {
            directory = FMLPaths.GAMEDIR.get().resolve("ccnradio").resolve("client_media");
            try {
                Files.createDirectories(directory);
            } catch (IOException ignored) {
            }
        }
        return directory;
    }

    public static Path find(UUID mediaId, String format) {
        String normalized = normalizeFormat(format);
        Path direct = pathFor(mediaId, normalized);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        // Older cache entries may use a different extension if the server format changed.
        for (String candidate : KNOWN_FORMATS) {
            if (candidate.equals(normalized)) {
                continue;
            }
            Path path = pathFor(mediaId, candidate);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    public static Path pathFor(UUID mediaId, String format) {
        return directory().resolve(mediaId + "." + normalizeFormat(format));
    }

    public static Path partFor(UUID mediaId) {
        return directory().resolve(mediaId + ".part");
    }

    public static void finishDownload(UUID mediaId, String format, Path part) throws IOException {
        Path target = pathFor(mediaId, format);
        try {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String normalizeFormat(String format) {
        String lower = format == null ? "" : format.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "wav", "mp3" -> lower;
            default -> "ogg";
        };
    }
}
