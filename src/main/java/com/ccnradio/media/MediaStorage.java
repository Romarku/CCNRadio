package com.ccnradio.media;

import com.ccnradio.CCNRadioMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side media library. All files are addressed by UUID, never by a user supplied
 * name, and every path is resolved from the library root.
 */
public final class MediaStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, MediaEntry> ENTRIES = new ConcurrentHashMap<>();

    private static Path root;
    private static Path mediaDir;
    private static Path tmpDir;

    private MediaStorage() {
    }

    public static synchronized void init(Path gameDir) {
        root = gameDir.resolve("ccnradio");
        mediaDir = root.resolve("media");
        tmpDir = root.resolve("tmp");
        ENTRIES.clear();
        try {
            Files.createDirectories(mediaDir);
            Files.createDirectories(tmpDir);
            cleanTempDirectory();
            loadEntries();
            CCNRadioMod.LOGGER.info("CCNR media library loaded from {} ({} entries)", mediaDir, ENTRIES.size());
        } catch (IOException e) {
            CCNRadioMod.LOGGER.error("Failed to initialize CCNR media library", e);
        }
    }

    public static synchronized void shutdown() {
        ENTRIES.clear();
    }

    public static Path getRoot() {
        return root;
    }

    public static Path getMediaDir() {
        return mediaDir;
    }

    public static Path getTmpDir() {
        return tmpDir;
    }

    public static List<MediaEntry> list() {
        List<MediaEntry> result = new ArrayList<>(ENTRIES.values());
        result.sort(Comparator.comparingLong(MediaEntry::getCreatedAt).reversed());
        return result;
    }

    public static MediaEntry get(UUID id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static Path pathFor(MediaEntry entry) {
        return mediaDir.resolve(entry.getUploader().toString()).resolve(entry.getId() + "." + entry.getFormat());
    }

    public static Path jsonPathFor(MediaEntry entry) {
        return mediaDir.resolve(entry.getUploader().toString()).resolve(entry.getId() + ".json");
    }

    /**
     * Moves a completed temporary upload into the library and writes its metadata.
     */
    public static synchronized void add(MediaEntry entry, Path source) throws IOException {
        Path target = pathFor(entry);
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        writeMetadata(entry);
        ENTRIES.put(entry.getId(), entry);
    }

    /**
     * Deletes an entry if the requester is its uploader or a server operator.
     */
    public static synchronized boolean delete(UUID id, UUID requester, boolean operator) {
        MediaEntry entry = ENTRIES.get(id);
        if (entry == null) {
            return false;
        }
        if (!entry.getUploader().equals(requester) && !operator) {
            return false;
        }
        try {
            Files.deleteIfExists(pathFor(entry));
            Files.deleteIfExists(jsonPathFor(entry));
            ENTRIES.remove(id);
            Path uploaderDir = mediaDir.resolve(entry.getUploader().toString());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(uploaderDir)) {
                if (!stream.iterator().hasNext()) {
                    Files.deleteIfExists(uploaderDir);
                }
            } catch (IOException ignored) {
                // The directory is not empty or disappeared; both are fine.
            }
            return true;
        } catch (IOException e) {
            CCNRadioMod.LOGGER.error("Failed to delete CCNR media {}", id, e);
            return false;
        }
    }

    private static void writeMetadata(MediaEntry entry) throws IOException {
        JsonObject json = entry.toJson();
        Path target = jsonPathFor(entry);
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(json), StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void loadEntries() {
        try (DirectoryStream<Path> uploaderDirs = Files.newDirectoryStream(mediaDir)) {
            for (Path uploaderDir : uploaderDirs) {
                if (!Files.isDirectory(uploaderDir)) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(uploaderDir, "*.json")) {
                    for (Path jsonPath : files) {
                        loadEntry(jsonPath);
                    }
                } catch (IOException e) {
                    CCNRadioMod.LOGGER.warn("Unable to read CCNR uploader directory {}", uploaderDir, e);
                }
            }
        } catch (IOException e) {
            CCNRadioMod.LOGGER.error("Unable to list CCNR media directory", e);
        }
    }

    private static void loadEntry(Path jsonPath) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(jsonPath, StandardCharsets.UTF_8)).getAsJsonObject();
            MediaEntry entry = MediaEntry.fromJson(json);
            if (entry == null) {
                CCNRadioMod.LOGGER.warn("Skipping invalid CCNR metadata {}", jsonPath);
                return;
            }
            Path audio = pathFor(entry);
            if (!Files.isRegularFile(audio)) {
                CCNRadioMod.LOGGER.warn("Skipping CCNR media {}: audio file is missing", entry.getId());
                return;
            }
            long actualSize = Files.size(audio);
            if (actualSize != entry.getSize()) {
                CCNRadioMod.LOGGER.warn("CCNR media {} size mismatch (metadata {}, file {}); keeping file",
                        entry.getId(), entry.getSize(), actualSize);
            }
            ENTRIES.put(entry.getId(), entry);
        } catch (Exception e) {
            CCNRadioMod.LOGGER.warn("Skipping unreadable CCNR metadata {}", jsonPath, e);
        }
    }

    private static void cleanTempDirectory() {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(tmpDir, "*.part")) {
            for (Path file : files) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            CCNRadioMod.LOGGER.warn("Unable to clean CCNR temporary upload directory", e);
        }
    }
}
