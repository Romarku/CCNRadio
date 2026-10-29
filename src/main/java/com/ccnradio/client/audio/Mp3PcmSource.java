package com.ccnradio.client.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Streaming MP3 decoder backed by JLayer, which is unpacked into the mod jar so players do
 * not need an extra download. JLayer hands out one decoded frame at a time; frames are
 * buffered here so callers always receive whole, interleaved 16-bit frames.
 */
public final class Mp3PcmSource implements PcmSource {
    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private final int channels;
    private final int sampleRate;
    private short[] pending = new short[0];
    private int pendingOffset;
    private int pendingLength;
    private boolean endOfStream;

    public Mp3PcmSource(Path file) throws IOException {
        Bitstream stream = new Bitstream(new BufferedInputStream(Files.newInputStream(file), 8192));
        int channelCount;
        int rate;
        SampleBuffer firstFrame;
        try {
            Header header = stream.readFrame();
            if (header == null) {
                throw new IOException("No MPEG audio frames in " + file);
            }
            firstFrame = (SampleBuffer) decoder.decodeFrame(header, stream);
            stream.closeFrame();
            channelCount = decoder.getOutputChannels();
            rate = decoder.getOutputFrequency();
        } catch (IOException e) {
            closeQuietly(stream);
            throw e;
        } catch (Exception e) {
            closeQuietly(stream);
            throw new IOException("Unable to decode MP3 " + file, e);
        }
        if (channelCount <= 0 || channelCount > 2 || rate <= 0) {
            closeQuietly(stream);
            throw new IOException("Invalid MP3 stream: channels=" + channelCount + ", rate=" + rate);
        }
        this.bitstream = stream;
        this.channels = channelCount;
        this.sampleRate = rate;
        store(firstFrame);
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
        int written = 0;
        while (written + channels <= output.length) {
            if (pendingOffset >= pendingLength && !decodeNextFrame()) {
                break;
            }
            int availableFrames = (pendingLength - pendingOffset) / channels;
            int requestedFrames = (output.length - written) / channels;
            int frames = Math.min(availableFrames, requestedFrames);
            if (frames <= 0) {
                break;
            }
            System.arraycopy(pending, pendingOffset, output, written, frames * channels);
            pendingOffset += frames * channels;
            written += frames * channels;
        }
        return written / channels;
    }

    @Override
    public void skipSeconds(double seconds) {
        if (seconds <= 0.0D) {
            return;
        }
        long remaining = (long) Math.floor(seconds * sampleRate);
        while (remaining > 0) {
            if (pendingOffset >= pendingLength && !decodeNextFrame()) {
                return;
            }
            int availableFrames = (pendingLength - pendingOffset) / channels;
            if (availableFrames <= 0) {
                pendingOffset = pendingLength;
                continue;
            }
            int frames = (int) Math.min(availableFrames, remaining);
            pendingOffset += frames * channels;
            remaining -= frames;
        }
    }

    @Override
    public void close() {
        closeQuietly(bitstream);
    }

    private boolean decodeNextFrame() {
        if (endOfStream) {
            return false;
        }
        try {
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer frame = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                bitstream.closeFrame();
                if (frame.getBufferLength() > 0) {
                    store(frame);
                    return true;
                }
            }
        } catch (Exception e) {
            // A corrupt or truncated tail is treated as the end of the stream.
        }
        endOfStream = true;
        return false;
    }

    private void store(SampleBuffer frame) {
        int length = frame.getBufferLength();
        if (length <= 0) {
            pendingOffset = 0;
            pendingLength = 0;
            return;
        }
        if (pending.length < length) {
            pending = new short[length];
        }
        System.arraycopy(frame.getBuffer(), 0, pending, 0, length);
        pendingOffset = 0;
        pendingLength = length;
    }

    private static void closeQuietly(Bitstream stream) {
        try {
            stream.close();
        } catch (Exception ignored) {
        }
    }
}
