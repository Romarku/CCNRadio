package com.ccnradio.util;

import com.ccnradio.media.MediaEntry;
import com.ccnradio.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * NBT layout for burned tapes. A tape stores a media UUID plus a snapshot of the
 * display metadata so the tooltip still works while the media list is not loaded.
 */
public final class TapeData {
    public static final String MEDIA_ID = "MediaId";
    public static final String MEDIA_NAME = "MediaName";
    public static final String DURATION = "DurationSeconds";
    public static final String FORMAT = "MediaFormat";
    public static final String UPLOADER = "Uploader";
    public static final String UPLOADER_NAME = "UploaderName";

    private TapeData() {
    }

    public static ItemStack createBurnedTape(MediaEntry entry) {
        ItemStack stack = new ItemStack(ModItems.BURNED_TAPE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(MEDIA_ID, entry.getId());
        tag.putString(MEDIA_NAME, entry.getDisplayName());
        tag.putInt(DURATION, entry.getDurationSeconds());
        tag.putString(FORMAT, entry.getFormat());
        tag.putUUID(UPLOADER, entry.getUploader());
        tag.putString(UPLOADER_NAME, entry.getUploaderName());
        if (!entry.getDisplayName().isEmpty()) {
            stack.setHoverName(Component.literal(entry.getDisplayName()));
        }
        return stack;
    }

    public static boolean isBurnedTape(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(ModItems.BURNED_TAPE.get())
                && stack.hasTag()
                && stack.getTag().hasUUID(MEDIA_ID);
    }

    public static UUID getMediaId(ItemStack stack) {
        if (!isBurnedTape(stack)) {
            return null;
        }
        return stack.getTag().getUUID(MEDIA_ID);
    }

    public static String getDisplayName(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(MEDIA_NAME, Tag.TAG_STRING)) {
            return stack.getTag().getString(MEDIA_NAME);
        }
        return "";
    }

    public static int getDurationSeconds(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(DURATION, Tag.TAG_INT)) {
            return stack.getTag().getInt(DURATION);
        }
        return 0;
    }

    public static String getFormat(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(FORMAT, Tag.TAG_STRING)) {
            return stack.getTag().getString(FORMAT);
        }
        return "ogg";
    }

    public static String getUploaderName(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(UPLOADER_NAME, Tag.TAG_STRING)) {
            return stack.getTag().getString(UPLOADER_NAME);
        }
        return "";
    }

    public static String formatDuration(int seconds) {
        if (seconds <= 0) {
            return "0:00";
        }
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
