package com.ccnradio.client.audio;

import com.ccnradio.CCNRadioMod;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Path;

/**
 * Java Sound based playback. It intentionally stays independent of Minecraft's OpenAL
 * engine so positional attenuation can be applied to PCM samples directly.
 */
public final class AudioPlayback {
    private AudioPlayback() {
    }

    public static AudioHandle play(Path file, String format, double offsetSeconds) throws Exception {
        PcmSource source = "wav".equalsIgnoreCase(format) ? new WavPcmSource(file) : new OggPcmSource(file);
        Handle handle = new Handle(source, offsetSeconds);
        handle.start();
        return handle;
    }

    public interface AudioHandle {
        void setVolume(float volume);

        void setPan(float pan);

        void stop();

        boolean isStopped();
    }

    private static final class Handle implements AudioHandle, Runnable {
        private final PcmSource source;
        private final double offsetSeconds;
        private volatile float volume;
        private volatile float pan;
        private volatile boolean stopped;
        private volatile SourceDataLine line;
        private Thread thread;

        private Handle(PcmSource source, double offsetSeconds) {
            this.source = source;
            this.offsetSeconds = offsetSeconds;
        }

        private void start() {
            thread = new Thread(this, "CCNR-Audio");
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void run() {
            try {
                AudioFormat format = new AudioFormat(source.sampleRate(), 16, source.channels(), true, false);
                SourceDataLine opened = AudioSystem.getSourceDataLine(format);
                line = opened;
                int bufferSize = Math.max(4096, source.sampleRate() * source.channels() / 2);
                opened.open(format, bufferSize);
                source.skipSeconds(offsetSeconds);
                opened.start();

                short[] samples = new short[4096 * source.channels()];
                byte[] bytes = new byte[samples.length * 2];
                while (!stopped) {
                    int frames = source.read(samples);
                    if (frames <= 0) {
                        break;
                    }
                    int count = frames * source.channels();
                    float currentVolume = volume;
                    float currentPan = pan;
                    float left = currentVolume;
                    float right = currentVolume;
                    if (source.channels() == 2) {
                        float angle = (currentPan + 1.0F) * (float) (Math.PI / 4.0D);
                        left = currentVolume * (float) Math.cos(angle) * 1.4142135F;
                        right = currentVolume * (float) Math.sin(angle) * 1.4142135F;
                    }
                    for (int i = 0; i < count; i++) {
                        float gain = source.channels() == 2 && (i & 1) == 1 ? right : left;
                        int value = (int) (samples[i] * gain);
                        if (value > Short.MAX_VALUE) {
                            value = Short.MAX_VALUE;
                        } else if (value < Short.MIN_VALUE) {
                            value = Short.MIN_VALUE;
                        }
                        bytes[i * 2] = (byte) (value & 0xFF);
                        bytes[i * 2 + 1] = (byte) ((value >> 8) & 0xFF);
                    }
                    opened.write(bytes, 0, count * 2);
                }
            } catch (Exception e) {
                if (!stopped) {
                    CCNRadioMod.LOGGER.warn("CCNR audio playback failed", e);
                }
            } finally {
                stopped = true;
                closeLine();
                source.close();
            }
        }

        private void closeLine() {
            SourceDataLine current = line;
            if (current != null) {
                try {
                    current.stop();
                    current.close();
                } catch (RuntimeException ignored) {
                }
                line = null;
            }
        }

        @Override
        public void setVolume(float value) {
            volume = Math.max(0.0F, Math.min(1.0F, value));
        }

        @Override
        public void setPan(float value) {
            pan = Math.max(-1.0F, Math.min(1.0F, value));
        }

        @Override
        public void stop() {
            stopped = true;
            closeLine();
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }
    }
}
