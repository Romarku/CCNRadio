package com.ccnradio.client.audio;

/** Minimal streaming PCM source abstraction used by the Java Sound playback thread. */
public interface PcmSource extends AutoCloseable {
    int sampleRate();

    int channels();

    /**
     * Fills {@code output} with interleaved 16-bit samples.
     *
     * @return number of frames read, or a negative value at end of stream
     */
    int read(short[] output);

    void skipSeconds(double seconds);

    @Override
    void close();
}
