package net.minecraft.nbt.visitors;

import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.TagType;

public interface SkipAll extends StreamTagVisitor {
    SkipAll INSTANCE = new SkipAll() {};

    @Override
    default StreamTagVisitor.ValueResult visitEnd() {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(String value) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(byte value) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(short value) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(int value) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(long value) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(float value) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(double value) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(byte[] value) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(int[] value) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visit(long[] value) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visitList(TagType<?> elementType, int size) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.EntryResult visitElement(TagType<?> type, int index) {
        return StreamTagVisitor.EntryResult.SKIP;
    }

    @Override
    default StreamTagVisitor.EntryResult visitEntry(TagType<?> type) {
        return StreamTagVisitor.EntryResult.SKIP;
    }

    @Override
    default StreamTagVisitor.EntryResult visitEntry(TagType<?> type, String id) {
        return StreamTagVisitor.EntryResult.SKIP;
    }

    @Override
    default StreamTagVisitor.ValueResult visitContainerEnd() {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }

    @Override
    default StreamTagVisitor.ValueResult visitRootEntry(TagType<?> type) {
        return StreamTagVisitor.ValueResult.CONTINUE;
    }
}
