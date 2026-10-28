package com.ccnradio.client;

import com.ccnradio.media.MediaEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClientMediaList {
    private static final List<MediaEntry> ENTRIES = new ArrayList<>();
    private static boolean canUpload = true;
    private static String denyKey = "";
    private static int version;

    private ClientMediaList() {
    }

    public static void set(List<MediaEntry> entries, boolean uploadAllowed, String denyMessageKey) {
        ENTRIES.clear();
        ENTRIES.addAll(entries);
        canUpload = uploadAllowed;
        denyKey = denyMessageKey == null ? "" : denyMessageKey;
        version++;
    }

    public static List<MediaEntry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static boolean canUpload() {
        return canUpload;
    }

    public static String denyKey() {
        return denyKey;
    }

    public static int version() {
        return version;
    }

    public static void clear() {
        ENTRIES.clear();
        canUpload = true;
        denyKey = "";
        version++;
    }
}
