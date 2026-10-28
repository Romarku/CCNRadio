package com.ccnradio.client.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.file.Path;

/**
 * WAV playback through the JDK's built-in PCM conversion, streamed instead of loading
 * the whole file into memory.
 */
public final class WavPcmSource implements PcmSource {
    private final AudioInputStream stream;
    private final int channels;
    private final int sampleRate;
    private byte[] bytes = new byte[8192];

    public WavPcmSource(Path file) throws Exception {
        AudioInputStream raw = AudioSystem.getAudioInputStream(file.toFile());
        AudioFormat base = raw.getFormat();
        AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(), 16, base.getChannels(), base.getChannels() * 2,
                base.getSampleRate(), false);
        if (!AudioSystem.isConversionSupported(target, base)) {
            raw.close();
            throw new IOException("WAV format cannot be converted to 16-bit PCM: " + base);
        }
        this.stream = AudioSystem.getAudioInputStream(target, raw);
        this.channels = target.getChannels();
        this.sampleRate = Math.round(target.getSampleRate());
        if (channels <= 0 || sampleRate <= 0) {
            stream.close();
            throw new IOException("Invalid WAV format: " + target);
        }
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
        int wanted = output.length * 2;
        if (bytes.length < wanted) {
            bytes = new byte[wanted];
        }
        int read;
        try {
            read = stream.read(bytes, 0, wanted);
        } catch (IOException e) {
            return -1;
        }
        if (read <= 0) {
            return read;
        }
        int samples = read / 2;
        for (int i = 0; i < samples; i++) {
            output[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
        }
        return samples / channels;
    }

    @Override
    public void skipSeconds(double seconds) {
        long remaining = (long) Math.floor(seconds * sampleRate * channels * 2.0D);
        try {
            while (remaining > 0) {
                long skipped = stream.skip(remaining);
                if (skipped <= 0) {
                    if (stream.read() < 0) {
                        return;
                    }
                    skipped = 1;
                }
                remaining -= skipped;
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }
}
