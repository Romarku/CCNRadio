package com.ccnradio.client;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Client-side mirror of server media files. A tape played once is cached here, so
 * leaving and re-entering the playback range does not download it again.
 */
public final class AudioCache {
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

    public static Path find(UUID mediaId) {
        Path ogg = pathFor(mediaId, "ogg");
        if (Files.isRegularFile(ogg)) {
            return ogg;
        }
        Path wav = pathFor(mediaId, "wav");
        if (Files.isRegularFile(wav)) {
            return wav;
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
        return "wav".equalsIgnoreCase(format) ? "wav" : "ogg";
    }
}
