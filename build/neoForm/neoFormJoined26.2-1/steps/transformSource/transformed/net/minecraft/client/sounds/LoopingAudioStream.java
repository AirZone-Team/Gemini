package net.minecraft.client.sounds;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LoopingAudioStream implements AudioStream {
    private final LoopingAudioStream.AudioStreamProvider provider;
    private AudioStream stream;
    private final BufferedInputStream bufferedInputStream;

    public LoopingAudioStream(LoopingAudioStream.AudioStreamProvider provider, InputStream originalInputStream) throws IOException {
        this.provider = provider;
        this.bufferedInputStream = new BufferedInputStream(originalInputStream);
        this.bufferedInputStream.mark(Integer.MAX_VALUE);
        this.stream = provider.create(new LoopingAudioStream.NoCloseBuffer(this.bufferedInputStream));
    }

    @Override
    public AudioFormat getFormat() {
        return this.stream.getFormat();
    }

    @Override
    public ByteBuffer read(int expectedSize) throws IOException {
        ByteBuffer result = this.stream.read(expectedSize);
        if (!result.hasRemaining()) {
            this.stream.close();
            this.bufferedInputStream.reset();
            this.stream = this.provider.create(new LoopingAudioStream.NoCloseBuffer(this.bufferedInputStream));
            result = this.stream.read(expectedSize);
        }

        return result;
    }

    @Override
    public void close() throws IOException {
        this.stream.close();
        this.bufferedInputStream.close();
    }

    @FunctionalInterface
    public interface AudioStreamProvider {
        AudioStream create(final InputStream inputStream) throws IOException;
    }

    private static class NoCloseBuffer extends FilterInputStream {
        private NoCloseBuffer(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
        }
    }
}
