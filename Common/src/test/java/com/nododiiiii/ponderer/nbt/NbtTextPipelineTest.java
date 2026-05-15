package com.nododiiiii.ponderer.nbt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtTextPipelineTest {

    private static final String USER_NBT = "{EnergyContainers:[],ForgeCaps:{},Items:[],activeState:0b,componentConfig:{config0:{side0:1,side1:1,side2:1,side3:1,side4:1,side5:1},config6:{side0:1,side1:1,side2:4,side3:8,side4:1,side5:1},eject0:0b,eject6:0b},componentEjector:{color0:-1,color1:-1,color2:-1,color3:-1,color4:-1,color5:-1,strictInput:0b},componentFrequency:{Security:{name:\"Security\",owner:[I;-1520936433,-167885378,-1212611829,-795432156],publicFreq:1b}},componentSecurity:{owner:[I;-1520936433,-167885378,-1212611829,-795432156],securityMode:0},componentUpgrade:{Items:[],upgrades:[]},controlType:0,currentRedstone:0,progress:[I;0,0,0],redstone:0b,sorting:0b,updateDelay:0}";

    @Test
    void realUserCaseParsesFormatsCompactsAndDetectsProgressArray() {
        CompoundTag parsed = parse(USER_NBT);
        NbtPrettyPrinter.FormattedText formatted = NbtPrettyPrinter.format(parsed);
        assertFalse(formatted.text().isBlank());
        assertTrue(formatted.text().contains("\n"));

        String compact = NbtTextCodec.compact(parsed);
        CompoundTag reparsed = parse(compact);
        assertEquals(parsed, reparsed);
        assertEquals(parsed.getAllKeys(), reparsed.getAllKeys(), "root fields should not be lost");

        List<NbtCoordinateDetector.Candidate> candidates = NbtCoordinateDetector.detect(parsed, formatted);
        assertTrue(candidates.stream().anyMatch(candidate ->
            candidate.path().toString().equals("$.progress")
                && candidate.kind() == NbtCoordinateDetector.Kind.INT_ARRAY));
    }

    @Test
    void basicNestedCompoundRoundTrips() {
        assertRoundTrip("{foo:1b,bar:{baz:\"ok\",list:[1,2,3]}}");
    }

    @Test
    void mixedNumericSuffixesRoundTripSemantically() {
        CompoundTag parsed = assertRoundTrip("{a:1b,b:2s,c:3,d:4L,e:5.0f,f:6.25d}");
        CompoundTag reparsed = parse(NbtTextCodec.compact(parsed));
        for (String key : parsed.getAllKeys()) {
            assertEquals(parsed.get(key).getId(), reparsed.get(key).getId(), key + " suffix type should be preserved");
        }
    }

    @Test
    void escapedStringAndUnicodeRoundTrip() {
        assertRoundTrip("{text:\"line1\\\\nline2\",name:\"测试\",quoted:\"\\\"demo\\\"\"}");
    }

    @Test
    void emptyStructuresRoundTrip() {
        assertRoundTrip("{emptyCompound:{},emptyList:[],emptyIntArray:[I;],emptyLongArray:[L;]}");
    }

    @Test
    void explicitLowercaseXyzCompoundIsDetectedAndRewritten() {
        CompoundTag parsed = assertRoundTrip("{x:12,y:64,z:-3}");
        NbtCoordinateDetector.Candidate candidate = onlyCandidate(parsed);
        assertEquals(NbtCoordinateDetector.Kind.COMPOUND, candidate.kind());

        assertTrue(NbtCoordinateDetector.rewriteCoordinate(parsed, candidate, new BlockPos(1, 2, 3), Direction.UP));
        assertEquals(1, parsed.getInt("x"));
        assertEquals(2, parsed.getInt("y"));
        assertEquals(3, parsed.getInt("z"));
    }

    @Test
    void uppercaseXyzCompoundIsDetected() {
        CompoundTag parsed = assertRoundTrip("{X:1,Y:2,Z:3,Name:\"Upper\"}");
        assertTrue(NbtCoordinateDetector.detect(parsed).stream().anyMatch(candidate ->
            candidate.kind() == NbtCoordinateDetector.Kind.COMPOUND
                && candidate.axisKeys().equals(List.of("X", "Y", "Z"))));
    }

    @Test
    void floatingPointPositionListIsDetectedAndRewriteKeepsFloatingPolicy() {
        CompoundTag parsed = assertRoundTrip("{Pos:[1.5d,64.0d,-8.25d]}");
        NbtCoordinateDetector.Candidate candidate = onlyCandidate(parsed);
        assertEquals(NbtCoordinateDetector.Kind.LIST, candidate.kind());
        assertTrue(candidate.usesFloatingPoint());

        assertTrue(NbtCoordinateDetector.rewriteCoordinate(parsed, candidate, new BlockPos(10, 20, 30), Direction.UP));
        String compact = NbtTextCodec.compact(parsed);
        assertTrue(compact.contains("10.5d"));
        assertTrue(compact.contains("20.0d"));
        assertTrue(compact.contains("30.5d"));
        assertEquals(parsed, parse(compact));
    }

    @Test
    void intArrayCoordinateIsDetected() {
        CompoundTag parsed = assertRoundTrip("{spawn:[I;100,64,-20]}");
        assertTrue(NbtCoordinateDetector.detect(parsed).stream().anyMatch(candidate ->
            candidate.kind() == NbtCoordinateDetector.Kind.INT_ARRAY
                && candidate.path().toString().equals("$.spawn")));
    }

    @Test
    void nestedCoordinateCandidatesKeepStableFormattedRows() {
        CompoundTag parsed = assertRoundTrip("{outer:{inner:{x:7,y:8,z:9}},path:[{pos:[0,1,2]},{pos:[3,4,5]}]}");
        NbtPrettyPrinter.FormattedText formatted = NbtPrettyPrinter.format(parsed);
        List<NbtCoordinateDetector.Candidate> candidates = NbtCoordinateDetector.detect(parsed, formatted);
        Set<String> paths = candidates.stream().map(candidate -> candidate.path().toString()).collect(Collectors.toSet());

        assertTrue(paths.contains("$.outer.inner"));
        assertTrue(paths.contains("$.path[0].pos"));
        assertTrue(paths.contains("$.path[1].pos"));
        assertTrue(candidates.stream().allMatch(candidate -> candidate.lineNumber() >= 0));
        assertEquals(candidates.size(), candidates.stream().map(NbtCoordinateDetector.Candidate::lineNumber).distinct().count());
    }

    @Test
    void aggressiveThreeValueListDetectionDoesNotCorruptOtherSyntax() {
        CompoundTag parsed = assertRoundTrip("{Rotation:[0f,90f],Color:[255,128,64]}");
        assertTrue(NbtCoordinateDetector.detect(parsed).stream().anyMatch(candidate ->
            candidate.path().toString().equals("$.Color")));
        assertEquals(parsed, parse(NbtTextCodec.compact(parsed)));
    }

    @Test
    void invalidInputsFailCleanly() {
        assertInvalid("{foo:}");
        assertInvalid("{bar:[1,2,}");
        assertInvalid("{unterminated:\"abc}");
    }

    private static CompoundTag assertRoundTrip(String snbt) {
        CompoundTag parsed = parse(snbt);
        NbtPrettyPrinter.FormattedText formatted = NbtPrettyPrinter.format(parsed);
        assertFalse(formatted.text().isBlank());
        CompoundTag formattedParsed = parse(formatted.text());
        assertEquals(parsed, formattedParsed);
        String compact = NbtTextCodec.compact(formattedParsed);
        assertFalse(compact.contains("\n"));
        CompoundTag reparsed = parse(compact);
        assertEquals(parsed, reparsed);
        return parsed;
    }

    private static CompoundTag parse(String snbt) {
        NbtTextCodec.ParseResult result = NbtTextCodec.parse(snbt);
        assertTrue(result.success(), () -> "expected parse success: " + result.errorMessage());
        assertNotNull(result.tag());
        return result.tag();
    }

    private static void assertInvalid(String snbt) {
        NbtTextCodec.ParseResult result = NbtTextCodec.parse(snbt);
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        assertNotEquals("", result.errorMessage().trim());
    }

    private static NbtCoordinateDetector.Candidate onlyCandidate(CompoundTag tag) {
        List<NbtCoordinateDetector.Candidate> candidates = NbtCoordinateDetector.detect(tag);
        assertEquals(1, candidates.size());
        return candidates.get(0);
    }
}
