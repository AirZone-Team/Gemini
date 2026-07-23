package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import java.util.zip.Deflater;

public class CompressionEncoder extends MessageToByteEncoder<ByteBuf> {
    private final byte[] encodeBuf = new byte[8192];
    private final Deflater deflater;
    private int threshold;
    private static final boolean DISABLE_PACKET_DEBUG = Boolean.parseBoolean(System.getProperty("neoforge.disablePacketCompressionDebug", "false"));
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger();

    public CompressionEncoder(int threshold) {
        this.threshold = threshold;
        this.deflater = new Deflater();
    }

    protected void encode(ChannelHandlerContext ctx, ByteBuf uncompressed, ByteBuf out) {
        int uncompressedLength = uncompressed.readableBytes();
        if (!DISABLE_PACKET_DEBUG && uncompressedLength > net.minecraft.network.CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH) {
            uncompressed.markReaderIndex();
            LOGGER.error("Attempted to send packet over maximum protocol size: {} > {}\nData:\n{}", uncompressedLength, net.minecraft.network.CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH,
                    net.neoforged.neoforge.logging.PacketDump.getContentDump(uncompressed));
            uncompressed.resetReaderIndex();
        }
        if (uncompressedLength > 8388608) {
            throw new IllegalArgumentException("Packet too big (is " + uncompressedLength + ", should be less than 8388608)");
        }

        if (uncompressedLength < this.threshold) {
            VarInt.write(out, 0);
            out.writeBytes(uncompressed);
        } else {
            byte[] input = new byte[uncompressedLength];
            uncompressed.readBytes(input);
            VarInt.write(out, input.length);
            this.deflater.setInput(input, 0, uncompressedLength);
            this.deflater.finish();

            while (!this.deflater.finished()) {
                int written = this.deflater.deflate(this.encodeBuf);
                out.writeBytes(this.encodeBuf, 0, written);
            }

            this.deflater.reset();
        }
    }

    public int getThreshold() {
        return this.threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
}
