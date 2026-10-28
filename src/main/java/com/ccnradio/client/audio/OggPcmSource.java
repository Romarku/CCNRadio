package com.ccnradio.client.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ogg Vorbis streaming decoder backed by LWJGL's stb_vorbis. Minecraft already ships
 * this native library on the client, so no extra dependency is required.
 */
public final class OggPcmSource implements PcmSource {
    private static final int FRAMES_PER_READ = 4096;

    private final long handle;
    private final int channels;
    private final int sampleRate;
    private final ShortBuffer buffer;
    @SuppressWarnings("unused")
    private final ByteBuffer memory;

    public OggPcmSource(Path file) throws IOException {
        IntBuffer error = BufferUtils.createIntBuffer(1);
        long opened = STBVorbis.stb_vorbis_open_filename(file.toString(), error, null);
        ByteBuffer fallbackMemory = null;
        if (opened == 0L) {
            // Windows' fopen does not always understand non-ASCII paths; retry from memory.
            byte[] bytes = Files.readAllBytes(file);
            fallbackMemory = BufferUtils.createByteBuffer(bytes.length);
            fallbackMemory.put(bytes).flip();
            error.clear();
            opened = STBVorbis.stb_vorbis_open_memory(fallbackMemory, error, null);
        }
        if (opened == 0L) {
            throw new IOException("stb_vorbis could not open " + file + " (error " + error.get(0) + ")");
        }
        this.handle = opened;
        this.memory = fallbackMemory;

        STBVorbisInfo info = STBVorbisInfo.malloc();
        int channelCount;
        int rate;
        try {
            STBVorbis.stb_vorbis_get_info(handle, info);
            channelCount = info.channels();
            rate = info.sample_rate();
        } finally {
            info.free();
        }
        if (channelCount <= 0 || channelCount > 8 || rate <= 0) {
            STBVorbis.stb_vorbis_close(handle);
            throw new IOException("Invalid Vorbis stream: channels=" + channelCount + ", rate=" + rate);
        }
        this.channels = channelCount;
        this.sampleRate = rate;
        this.buffer = BufferUtils.createShortBuffer(FRAMES_PER_READ * channelCount);
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public int channels() {
        return channels;
    }

    @Override
    public int read(short[] output) {
        int maxFrames = Math.min(FRAMES_PER_READ, output.length / channels);
        if (maxFrames <= 0) {
            return 0;
        }
        buffer.clear();
        buffer.limit(maxFrames * channels);
        int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, buffer);
        if (frames <= 0) {
            return frames;
        }
        int count = frames * channels;
        for (int i = 0; i < count; i++) {
            output[i] = buffer.get(i);
        }
        return frames;
    }

    @Override
    public void skipSeconds(double seconds) {
        if (seconds <= 0.0D) {
            return;
        }
        int sample = (int) Math.floor(seconds * sampleRate);
        if (sample <= 0) {
            return;
        }
        if (STBVorbis.stb_vorbis_seek(handle, sample)) {
            return;
        }
        // Seeking failed (e.g. stream without a known length): decode and discard.
        short[] discard = new short[FRAMES_PER_READ * channels];
        int remaining = sample;
        while (remaining > 0) {
            int frames = read(discard);
            if (frames <= 0) {
                return;
            }
            remaining -= frames;
        }
    }

    @Override
    public void close() {
        if (handle != 0L) {
            STBVorbis.stb_vorbis_close(handle);
        }
    }
}
