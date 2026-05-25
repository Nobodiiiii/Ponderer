package com.nododiiiii.ponderer.nbt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NbtCoordinateDetector {

    private NbtCoordinateDetector() {
    }

    public enum Kind {
        COMPOUND,
        LIST,
        INT_ARRAY,
        LONG_ARRAY,
        BYTE_ARRAY
    }

    public enum NumericPolicy {
        BYTE,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE
    }

    public record Candidate(NbtPath path, Kind kind, int lineNumber, List<String> axisKeys,
                            List<NumericPolicy> policies) {
        public Candidate {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(kind, "kind");
            axisKeys = axisKeys == null ? List.of() : List.copyOf(axisKeys);
            policies = List.copyOf(policies);
            if (policies.size() != 3) {
                throw new IllegalArgumentException("coordinate candidate must have three numeric policies");
            }
        }

        public boolean usesFloatingPoint() {
            return policies.stream().anyMatch(policy -> policy == NumericPolicy.FLOAT || policy == NumericPolicy.DOUBLE);
        }
    }

    public static List<Candidate> detect(CompoundTag root) {
        return detect(root, null);
    }

    public static List<Candidate> detect(CompoundTag root, @Nullable NbtPrettyPrinter.FormattedText formatted) {
        List<Candidate> candidates = new ArrayList<>();
        visit(root, NbtPath.ROOT, formatted, candidates);
        return List.copyOf(candidates);
    }

    @Nullable
    public static Candidate findCandidate(CompoundTag root, NbtPath path) {
        for (Candidate candidate : detect(root)) {
            if (candidate.path().equals(path)) {
                return candidate;
            }
        }
        return null;
    }

    public static boolean rewriteCoordinate(CompoundTag root, Candidate candidate, BlockPos pos,
                                            @Nullable Direction face) {
        Tag tag = candidate.path().resolve(root);
        if (tag == null) {
            return false;
        }

        double[] values = pickedValues(candidate, pos, face);
        return switch (candidate.kind()) {
            case COMPOUND -> rewriteCompound((CompoundTag) tag, candidate, values);
            case LIST -> rewriteList((ListTag) tag, candidate, values);
            case INT_ARRAY -> rewriteIntArray((IntArrayTag) tag, values);
            case LONG_ARRAY -> rewriteLongArray((LongArrayTag) tag, values);
            case BYTE_ARRAY -> rewriteByteArray((ByteArrayTag) tag, values);
        };
    }

    private static void visit(Tag tag, NbtPath path, @Nullable NbtPrettyPrinter.FormattedText formatted,
                              List<Candidate> out) {
        if (tag instanceof CompoundTag compound) {
            detectCompoundCandidate(compound, path, formatted, out);
            for (String key : compound.getAllKeys()) {
                Tag child = compound.get(key);
                if (child != null) {
                    visit(child, path.child(key), formatted, out);
                }
            }
            return;
        }
        if (tag instanceof ListTag list) {
            detectListCandidate(list, path, formatted, out);
            for (int i = 0; i < list.size(); i++) {
                visit(list.get(i), path.child(i), formatted, out);
            }
            return;
        }
        if (tag instanceof IntArrayTag array && array.size() == 3) {
            out.add(new Candidate(path, Kind.INT_ARRAY, lineNumber(path, formatted), List.of(),
                List.of(NumericPolicy.INT, NumericPolicy.INT, NumericPolicy.INT)));
        } else if (tag instanceof LongArrayTag array && array.size() == 3) {
            out.add(new Candidate(path, Kind.LONG_ARRAY, lineNumber(path, formatted), List.of(),
                List.of(NumericPolicy.LONG, NumericPolicy.LONG, NumericPolicy.LONG)));
        } else if (tag instanceof ByteArrayTag array && array.size() == 3) {
            out.add(new Candidate(path, Kind.BYTE_ARRAY, lineNumber(path, formatted), List.of(),
                List.of(NumericPolicy.BYTE, NumericPolicy.BYTE, NumericPolicy.BYTE)));
        }
    }

    private static void detectCompoundCandidate(CompoundTag compound, NbtPath path,
                                                @Nullable NbtPrettyPrinter.FormattedText formatted,
                                                List<Candidate> out) {
        detectCompoundKeys(compound, path, formatted, out, "x", "y", "z");
        detectCompoundKeys(compound, path, formatted, out, "X", "Y", "Z");
    }

    private static void detectCompoundKeys(CompoundTag compound, NbtPath path,
                                           @Nullable NbtPrettyPrinter.FormattedText formatted,
                                           List<Candidate> out, String xKey, String yKey, String zKey) {
        Tag x = compound.get(xKey);
        Tag y = compound.get(yKey);
        Tag z = compound.get(zKey);
        if (x instanceof NumericTag && y instanceof NumericTag && z instanceof NumericTag) {
            out.add(new Candidate(path, Kind.COMPOUND, lineNumber(path, formatted),
                List.of(xKey, yKey, zKey),
                List.of(policyOf(x), policyOf(y), policyOf(z))));
        }
    }

    private static void detectListCandidate(ListTag list, NbtPath path,
                                            @Nullable NbtPrettyPrinter.FormattedText formatted,
                                            List<Candidate> out) {
        if (list.size() != 3) {
            return;
        }
        List<NumericPolicy> policies = new ArrayList<>(3);
        for (int i = 0; i < list.size(); i++) {
            Tag child = list.get(i);
            if (!(child instanceof NumericTag)) {
                return;
            }
            policies.add(policyOf(child));
        }
        out.add(new Candidate(path, Kind.LIST, lineNumber(path, formatted), List.of(), policies));
    }

    private static boolean rewriteCompound(CompoundTag compound, Candidate candidate, double[] values) {
        List<String> keys = candidate.axisKeys();
        if (keys.size() != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            compound.put(keys.get(i), tagFor(candidate.policies().get(i), values[i]));
        }
        return true;
    }

    private static boolean rewriteList(ListTag list, Candidate candidate, double[] values) {
        if (list.size() != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            list.setTag(i, tagFor(candidate.policies().get(i), values[i]));
        }
        return true;
    }

    private static boolean rewriteIntArray(IntArrayTag array, double[] values) {
        if (array.size() != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            array.setTag(i, IntTag.valueOf((int) Math.round(values[i])));
        }
        return true;
    }

    private static boolean rewriteLongArray(LongArrayTag array, double[] values) {
        if (array.size() != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            array.setTag(i, LongTag.valueOf(Math.round(values[i])));
        }
        return true;
    }

    private static boolean rewriteByteArray(ByteArrayTag array, double[] values) {
        if (array.size() != 3) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            array.setTag(i, ByteTag.valueOf((byte) Math.round(values[i])));
        }
        return true;
    }

    private static double[] pickedValues(Candidate candidate, BlockPos pos, @Nullable Direction face) {
        double[] values = {pos.getX(), pos.getY(), pos.getZ()};
        if (candidate.usesFloatingPoint() && face != null) {
            Direction.Axis axis = face.getAxis();
            if (axis != Direction.Axis.X) {
                values[0] += 0.5;
            }
            if (axis != Direction.Axis.Y) {
                values[1] += 0.5;
            }
            if (axis != Direction.Axis.Z) {
                values[2] += 0.5;
            }
        }
        return values;
    }

    private static Tag tagFor(NumericPolicy policy, double value) {
        return switch (policy) {
            case BYTE -> ByteTag.valueOf((byte) Math.round(value));
            case SHORT -> ShortTag.valueOf((short) Math.round(value));
            case INT -> IntTag.valueOf((int) Math.round(value));
            case LONG -> LongTag.valueOf(Math.round(value));
            case FLOAT -> FloatTag.valueOf((float) value);
            case DOUBLE -> DoubleTag.valueOf(value);
        };
    }

    private static NumericPolicy policyOf(Tag tag) {
        return switch (tag.getId()) {
            case Tag.TAG_BYTE -> NumericPolicy.BYTE;
            case Tag.TAG_SHORT -> NumericPolicy.SHORT;
            case Tag.TAG_LONG -> NumericPolicy.LONG;
            case Tag.TAG_FLOAT -> NumericPolicy.FLOAT;
            case Tag.TAG_DOUBLE -> NumericPolicy.DOUBLE;
            default -> NumericPolicy.INT;
        };
    }

    private static int lineNumber(NbtPath path, @Nullable NbtPrettyPrinter.FormattedText formatted) {
        if (formatted == null) {
            return -1;
        }
        NbtPrettyPrinter.Line line = formatted.lineForPath(path);
        return line == null ? -1 : line.number();
    }
}
