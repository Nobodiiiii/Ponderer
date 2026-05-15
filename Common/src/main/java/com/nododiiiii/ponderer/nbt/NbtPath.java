package com.nododiiiii.ponderer.nbt;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NbtPath {

    public sealed interface Segment permits KeySegment, IndexSegment {
    }

    public record KeySegment(String key) implements Segment {
        public KeySegment {
            Objects.requireNonNull(key, "key");
        }
    }

    public record IndexSegment(int index) implements Segment {
        public IndexSegment {
            if (index < 0) {
                throw new IllegalArgumentException("index must be non-negative");
            }
        }
    }

    public static final NbtPath ROOT = new NbtPath(List.of());

    private final List<Segment> segments;

    public NbtPath(List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    public List<Segment> segments() {
        return segments;
    }

    public NbtPath child(String key) {
        List<Segment> next = new ArrayList<>(segments);
        next.add(new KeySegment(key));
        return new NbtPath(next);
    }

    public NbtPath child(int index) {
        List<Segment> next = new ArrayList<>(segments);
        next.add(new IndexSegment(index));
        return new NbtPath(next);
    }

    @Nullable
    public Tag resolve(CompoundTag root) {
        Tag current = root;
        for (Segment segment : segments) {
            if (segment instanceof KeySegment key) {
                if (!(current instanceof CompoundTag compound)) {
                    return null;
                }
                current = compound.get(key.key());
            } else if (segment instanceof IndexSegment index) {
                if (!(current instanceof ListTag list) || index.index() >= list.size()) {
                    return null;
                }
                current = list.get(index.index());
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof NbtPath other && segments.equals(other.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    @Override
    public String toString() {
        if (segments.isEmpty()) {
            return "$";
        }
        StringBuilder out = new StringBuilder("$");
        for (Segment segment : segments) {
            if (segment instanceof KeySegment key) {
                out.append('.').append(key.key());
            } else if (segment instanceof IndexSegment index) {
                out.append('[').append(index.index()).append(']');
            }
        }
        return out.toString();
    }
}
