package com.ccnradio.media;

import com.ccnradio.CCNRadioMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent server-side upload permission mode, toggled with the {@code /ccnradio}
 * commands. The default is that everyone may upload.
 */
public final class UploadSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Mode {
        ANYONE,
        ADMIN_ONLY
    }

    private static Mode mode = Mode.ANYONE;
    private static Path settingsFile;

    private UploadSettings() {
    }

    public static synchronized void load(Path gameDir) {
        settingsFile = gameDir.resolve("ccnradio").resolve("settings.json");
        mode = Mode.ANYONE;
        if (!Files.isRegularFile(settingsFile)) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(settingsFile, StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("uploadMode")) {
                mode = Mode.valueOf(json.get("uploadMode").getAsString());
            }
        } catch (Exception e) {
            CCNRadioMod.LOGGER.warn("Unable to read CCNR settings; defaulting to anyone-can-upload", e);
        }
    }

    public static synchronized Mode getMode() {
        return mode;
    }

    public static synchronized void setMode(Mode newMode) {
        mode = newMode;
        save();
    }

    public static boolean canUpload(ServerPlayer player) {
        Mode current = getMode();
        return current == Mode.ANYONE || player.hasPermissions(2);
    }

    public static String denyMessageKey() {
        return getMode() == Mode.ADMIN_ONLY ? "msg.ccnradio.admin_only" : "";
    }

    private static void save() {
        if (settingsFile == null) {
            return;
        }
        try {
            Files.createDirectories(settingsFile.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("uploadMode", mode.name());
            Path temp = settingsFile.resolveSibling("settings.json.tmp");
            Files.writeString(temp, GSON.toJson(json), StandardCharsets.UTF_8);
            Files.move(temp, settingsFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            CCNRadioMod.LOGGER.error("Unable to save CCNR settings", e);
        }
    }
}
