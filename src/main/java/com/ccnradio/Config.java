package com.ccnradio;

import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * Common configuration. The values are mirrored into plain static fields after the
 * config is loaded so the rest of the mod never has to touch the config spec directly.
 */
@Mod.EventBusSubscriber(modid = CCNRadioMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue MAX_UPLOAD_MB = BUILDER
            .comment("Maximum size of a single uploaded audio file, in megabytes.")
            .defineInRange("maxUploadSizeMb", 20, 1, 200);

    private static final ForgeConfigSpec.IntValue MAX_DURATION_SECONDS = BUILDER
            .comment("Maximum duration of a single uploaded audio file, in seconds.")
            .defineInRange("maxAudioDurationSeconds", 600, 1, 7200);

    private static final ForgeConfigSpec.IntValue MIN_PLAYBACK_RANGE = BUILDER
            .comment("Minimum playback range of a tape player, in blocks.")
            .defineInRange("minPlaybackRange", 4, 1, 64);

    private static final ForgeConfigSpec.IntValue MAX_PLAYBACK_RANGE = BUILDER
            .comment("Maximum playback range of a tape player, in blocks.")
            .defineInRange("maxPlaybackRange", 64, 4, 128);

    private static final ForgeConfigSpec.IntValue DEFAULT_PLAYBACK_RANGE = BUILDER
            .comment("Default playback range of a newly placed tape player, in blocks.")
            .defineInRange("defaultPlaybackRange", 16, 4, 128);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int maxUploadMb = 20;
    public static int maxUploadBytes = 20 * 1024 * 1024;
    public static int maxDurationSeconds = 600;
    public static int minPlaybackRange = 4;
    public static int maxPlaybackRange = 64;
    public static int defaultPlaybackRange = 16;

    private Config() {
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        maxUploadMb = MAX_UPLOAD_MB.get();
        maxUploadBytes = maxUploadMb * 1024 * 1024;
        maxDurationSeconds = MAX_DURATION_SECONDS.get();
        minPlaybackRange = MIN_PLAYBACK_RANGE.get();
        maxPlaybackRange = MAX_PLAYBACK_RANGE.get();
        if (maxPlaybackRange < minPlaybackRange) {
            maxPlaybackRange = minPlaybackRange;
        }
        defaultPlaybackRange = Mth.clamp(DEFAULT_PLAYBACK_RANGE.get(), minPlaybackRange, maxPlaybackRange);
    }
}
