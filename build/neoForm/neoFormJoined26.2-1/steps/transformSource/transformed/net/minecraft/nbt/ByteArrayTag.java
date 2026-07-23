package net.minecraft.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.apache.commons.lang3.ArrayUtils;

public final class ByteArrayTag implements CollectionTag {
    private static final int SELF_SIZE_IN_BYTES = 24;
    public static final TagType<ByteArrayTag> TYPE = new TagType.VariableSize<ByteArrayTag>() {
        public ByteArrayTag load(DataInput input, NbtAccounter accounter) throws IOException {
            return new ByteArrayTag(readAccounted(input, accounter));
        }

        @Override
        public StreamTagVisitor.ValueResult parse(DataInput input, StreamTagVisitor output, NbtAccounter accounter) throws IOException {
            return output.visit(readAccounted(input, accounter));
        }

        private static byte[] readAccounted(DataInput input, NbtAccounter accounter) throws IOException {
            accounter.accountBytes(24L);
            int length = input.readInt();
            accounter.accountBytes(1L, length);
            byte[] data = new byte[length];
            input.readFully(data);
            return data;
        }

        @Override
        public void skip(DataInput input, NbtAccounter accounter) throws IOException {
            input.skipBytes(input.readInt() * 1);
        }

        @Override
        public String getName() {
            return "BYTE[]";
        }

        @Override
        public String getPrettyName() {
            return "TAG_Byte_Array";
        }
    };
    private byte[] data;

    public ByteArrayTag(byte[] data) {
        this.data = data;
    }

    @Override
    public void write(DataOutput output) throws IOException {
        output.writeInt(this.data.length);
        output.write(this.data);
    }

    @Override
    public int sizeInBytes() {
        return 24 + 1 * this.data.length;
    }

    @Override
    public byte getId() {
        return 7;
    }

    @Override
    public TagType<ByteArrayTag> getType() {
        return TYPE;
    }

    @Override
    public String toString() {
        StringTagVisitor visitor = new StringTagVisitor();
        visitor.visitByteArray(this);
        return visitor.build();
    }

    @Override
    public Tag copy() {
        byte[] cp = new byte[this.data.length];
        System.arraycopy(this.data, 0, cp, 0, this.data.length);
        return new ByteArrayTag(cp);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj ? true : obj instanceof ByteArrayTag byteArrayTag && Arrays.equals(this.data, byteArrayTag.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.data);
    }

    @Override
    public void accept(TagVisitor visitor) {
        visitor.visitByteArray(this);
    }

    public byte[] getAsByteArray() {
        return this.data;
    }

    @Override
    public int size() {
        return this.data.length;
    }

    public ByteTag get(int index) {
        return ByteTag.valueOf(this.data[index]);
    }

    @Override
    public boolean setTag(int index, Tag tag) {
        if (tag instanceof NumericTag numeric) {
            this.data[index] = numeric.byteValue();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean addTag(int index, Tag tag) {
        if (tag instanceof NumericTag numeric) {
            this.data = ArrayUtils.add(this.data, index, numeric.byteValue());
            return true;
        } else {
            return false;
        }
    }

    public ByteTag remove(int index) {
        byte prev = this.data[index];
        this.data = ArrayUtils.remove(this.data, index);
        return ByteTag.valueOf(prev);
    }

    @Override
    public void clear() {
        this.data = new byte[0];
    }

    @Override
    public Optional<byte[]> asByteArray() {
        return Optional.of(this.data);
    }

    @Override
    public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
        return visitor.visit(this.data);
    }
}
