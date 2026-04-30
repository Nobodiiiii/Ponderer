package com.nododiiiii.ponderer.ui;

import net.minecraft.core.Registry;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

public final class FormParsers {

    public record ParseResult<T>(@Nullable T value, @Nullable String errorMessage) {
        public boolean failed() {
            return errorMessage != null;
        }
    }

    public record Int3(int x, int y, int z) {
        public List<Integer> toList() {
            return List.of(x, y, z);
        }
    }

    public record Double3(double x, double y, double z) {
        public List<Double> toList() {
            return List.of(x, y, z);
        }
    }

    public record IntRange(Int3 from, @Nullable Int3 to) {
    }

    public record DoubleRange(Double3 from, @Nullable Double3 to) {
    }

    private FormParsers() {
    }

    public static ParseResult<String> requiredText(String raw, String errorMessage) {
        String value = raw == null ? "" : raw.trim();
        return value.isEmpty() ? error(errorMessage) : success(value);
    }

    public static ParseResult<Integer> requiredInt(String raw, String fieldName) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return error(UIText.of("ponderer.ui.error.required_field", fieldName));
        }
        try {
            return success(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return error(UIText.of("ponderer.ui.error.invalid_integer", fieldName));
        }
    }

    public static ParseResult<Double> requiredDouble(String raw, String fieldName) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return error(UIText.of("ponderer.ui.error.required_field", fieldName));
        }
        try {
            return success(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return error(UIText.of("ponderer.ui.error.invalid_number", fieldName));
        }
    }

    public static ParseResult<Int3> requiredInt3(String x, String y, String z) {
        ParseResult<Integer> px = requiredInt(x, "X");
        if (px.failed()) {
            return error(px.errorMessage());
        }
        ParseResult<Integer> py = requiredInt(y, "Y");
        if (py.failed()) {
            return error(py.errorMessage());
        }
        ParseResult<Integer> pz = requiredInt(z, "Z");
        if (pz.failed()) {
            return error(pz.errorMessage());
        }
        return success(new Int3(px.value(), py.value(), pz.value()));
    }

    public static ParseResult<Double3> requiredDouble3(String x, String y, String z) {
        ParseResult<Double> px = requiredDouble(x, "X");
        if (px.failed()) {
            return error(px.errorMessage());
        }
        ParseResult<Double> py = requiredDouble(y, "Y");
        if (py.failed()) {
            return error(py.errorMessage());
        }
        ParseResult<Double> pz = requiredDouble(z, "Z");
        if (pz.failed()) {
            return error(pz.errorMessage());
        }
        return success(new Double3(px.value(), py.value(), pz.value()));
    }

    public static ParseResult<IntRange> intRange(StepXyzFieldHandle from, StepXyzFieldHandle to, String partialErrorMessage) {
        ParseResult<Int3> fromResult = requiredInt3(from.x(), from.y(), from.z());
        if (fromResult.failed()) {
            return error(fromResult.errorMessage());
        }
        ParseResult<Int3> toResult = optionalInt3(to.x(), to.y(), to.z(), partialErrorMessage);
        if (toResult.failed()) {
            return error(toResult.errorMessage());
        }
        return success(new IntRange(fromResult.value(), toResult.value()));
    }

    public static ParseResult<DoubleRange> doubleRange(String fromX, String fromY, String fromZ,
                                                       String toX, String toY, String toZ,
                                                       String partialErrorMessage) {
        ParseResult<Double3> fromResult = requiredDouble3(fromX, fromY, fromZ);
        if (fromResult.failed()) {
            return error(fromResult.errorMessage());
        }
        ParseResult<Double3> toResult = optionalDouble3(toX, toY, toZ, partialErrorMessage);
        if (toResult.failed()) {
            return error(toResult.errorMessage());
        }
        return success(new DoubleRange(fromResult.value(), toResult.value()));
    }

    public static ParseResult<String> registryId(String raw, String requiredMessage, String invalidMessage,
                                                 Function<String, String> unknownMessage, Registry<?> registry) {
        ParseResult<String> text = requiredText(raw, requiredMessage);
        if (text.failed()) {
            return text;
        }
        ResourceLocation location = ResourceLocation.tryParse(text.value());
        if (location == null) {
            return error(invalidMessage);
        }
        if (!registry.containsKey(location)) {
            return error(unknownMessage.apply(text.value()));
        }
        return success(text.value());
    }

    public static ParseResult<String> optionalNbt(String raw, String invalidMessage) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return success(null);
        }
        try {
            TagParser.parseTag(value);
            return success(value);
        } catch (Exception e) {
            return error(invalidMessage);
        }
    }

    @Nullable
    public static Int3 parseInt3(String raw) {
        try {
            String[] parts = raw.split(",");
            if (parts.length < 3) {
                return null;
            }
            return new Int3(
                Integer.parseInt(parts[0].trim()),
                Integer.parseInt(parts[1].trim()),
                Integer.parseInt(parts[2].trim()));
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static Double3 parseDouble3(String raw) {
        try {
            String[] parts = raw.split(",");
            if (parts.length < 3) {
                return null;
            }
            return new Double3(
                Double.parseDouble(parts[0].trim()),
                Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim()));
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public static Boolean parseBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : Boolean.parseBoolean(value);
    }

    private static ParseResult<Int3> optionalInt3(String x, String y, String z, String partialErrorMessage) {
        boolean any = !x.trim().isEmpty() || !y.trim().isEmpty() || !z.trim().isEmpty();
        if (!any) {
            return success(null);
        }
        if (x.trim().isEmpty() || y.trim().isEmpty() || z.trim().isEmpty()) {
            return error(partialErrorMessage);
        }
        return requiredInt3(x, y, z);
    }

    private static ParseResult<Double3> optionalDouble3(String x, String y, String z, String partialErrorMessage) {
        boolean any = !x.trim().isEmpty() || !y.trim().isEmpty() || !z.trim().isEmpty();
        if (!any) {
            return success(null);
        }
        if (x.trim().isEmpty() || y.trim().isEmpty() || z.trim().isEmpty()) {
            return error(partialErrorMessage);
        }
        return requiredDouble3(x, y, z);
    }

    private static <T> ParseResult<T> success(@Nullable T value) {
        return new ParseResult<>(value, null);
    }

    private static <T> ParseResult<T> error(String message) {
        return new ParseResult<>(null, message);
    }
}
