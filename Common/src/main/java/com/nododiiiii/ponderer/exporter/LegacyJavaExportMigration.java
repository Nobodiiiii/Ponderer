package com.nododiiiii.ponderer.exporter;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nododiiiii.ponderer.scene.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Source migration never executes host code. Uncertain statements remain compiled Java. */
final class LegacyJavaExportMigration {
    private static final String ACTIONS = "com.nododiiiii.ponderer.runtime.api.PonderActions";
    private static final String SUPPORT = "GeneratedPonderSupport";
    private static final Gson GSON = new Gson();

    record Plan(Map<String, String> writes, Set<String> deletions,
                Map<String, JavaModuleExportManifest.SceneEntry> scenes,
                Set<String> oldSceneKeys, Map<String, String> acceptedSources, List<String> notes) {}

    private record Legacy(String key, JavaModuleExportManifest.SceneEntry entry, String path, String source) {}
    private record Board(MethodDeclaration method, String structure, String titlePath) {}

    private LegacyJavaExportMigration() {}

    static boolean detected(JavaModuleScanResult.TargetProject target, JavaModuleExportManifest manifest) throws IOException {
        return Files.isRegularFile(target.targetRoot.resolve(supportPath(target)))
            || !legacyScenes(target, manifest).isEmpty();
    }

    static Plan prepare(JavaModuleScanResult.TargetProject target, JavaModuleExportManifest manifest,
                        JavaModuleExportRequest.LegacyAction action) throws IOException {
        Map<String, String> writes = new LinkedHashMap<>();
        Set<String> deletions = new LinkedHashSet<>();
        Map<String, JavaModuleExportManifest.SceneEntry> scenes = new LinkedHashMap<>();
        Set<String> oldKeys = new LinkedHashSet<>();
        Map<String, String> accepted = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        for (Legacy legacy : legacyScenes(target, manifest)) {
            oldKeys.add(legacy.key);
            if (legacy.source != null) accepted.put(legacy.path, legacy.source);
            if (action == JavaModuleExportRequest.LegacyAction.DISCARD) {
                deletions.add(legacy.path);
                if (legacy.entry.resourceFiles != null) deletions.addAll(legacy.entry.resourceFiles);
                continue;
            }
            if (legacy.source == null) throw new IOException("Missing legacy Java source: " + legacy.path);
            try {
                Converted converted = convert(target, legacy, notes);
                writes.put(legacy.path, converted.source);
                var entry = GSON.fromJson(GSON.toJson(legacy.entry), JavaModuleExportManifest.SceneEntry.class);
                entry.sceneId = converted.document.id;
                entry.sceneKey = legacy.entry.sceneKey == null ? legacy.key : legacy.entry.sceneKey;
                entry.className = converted.className;
                entry.javaFiles = new ArrayList<>(List.of(legacy.path));
                entry.resourceId = null;
                entry.contentHash = null;
                entry.hasPrepare = true;
                entry.hasClose = true;
                entry.sourceApiResolved = true;
                entry.runtimeFeatures = SceneRuntimeRequirements.features(converted.document).stream().sorted().toList();
                entry.status = SceneExportOutcome.Status.COMPLETE.name();
                scenes.put(legacy.key, entry);
            } catch (RuntimeException error) {
                throw new IOException("Cannot migrate " + legacy.path + ": " + error.getMessage(), error);
            }
        }
        String support = supportPath(target);
        if (Files.isRegularFile(target.targetRoot.resolve(support))) {
            accepted.put(support, Files.readString(target.targetRoot.resolve(support)));
            deletions.add(support);
        }
        // Refuse dangling references, including helpers outside the exported scene directory.
        try (var paths = Files.walk(target.javaRoot)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = relative(target, path);
                if (deletions.contains(relative) || writes.containsKey(relative)) continue;
                String source = Files.readString(path);
                if (!source.contains(SUPPORT)) continue;
                CompilationUnit unit = parse(source);
                boolean references = unit.getImports().stream().anyMatch(i -> i.getNameAsString().contains(SUPPORT))
                    || unit.findAll(ClassOrInterfaceType.class).stream().anyMatch(t -> t.getNameAsString().equals(SUPPORT))
                    || unit.findAll(NameExpr.class).stream().anyMatch(n -> n.getNameAsString().equals(SUPPORT));
                if (!references) continue;
                if (action == JavaModuleExportRequest.LegacyAction.DISCARD)
                    throw new IOException("Cannot discard Support: remaining Java source depends on it: " + relative);
                LexicalPreservingPrinter.setup(unit);
                replaceSupport(unit, target);
                writes.put(relative, LexicalPreservingPrinter.print(unit));
                accepted.put(relative, source);
            }
        }
        return new Plan(writes, deletions, scenes, oldKeys, accepted, notes);
    }

    private static List<Legacy> legacyScenes(JavaModuleScanResult.TargetProject target,
                                            JavaModuleExportManifest manifest) throws IOException {
        Map<String, Legacy> found = new LinkedHashMap<>();
        if (manifest.scenes != null) for (var item : manifest.scenes.entrySet()) {
            var entry = item.getValue();
            if (entry.javaFiles == null) continue;
            for (String relative : entry.javaFiles) {
                Path path = inside(target, relative);
                if (!Files.isRegularFile(path)) {
                    if (JavaModuleExportRequest.Mode.JAVA_SUPPORT.name().equals(manifest.mode))
                        found.put(relative, new Legacy(item.getKey(), entry, relative, null));
                    continue;
                }
                String source = Files.readString(path);
                if (isLegacy(source)) found.put(relative, new Legacy(item.getKey(), entry, relative, source));
            }
        }
        Path directory = target.javaRoot.resolve(target.scenePackage.replace('.', '/'));
        if (Files.isDirectory(directory)) try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                String relative = relative(target, path);
                if (found.containsKey(relative)) continue;
                String source = Files.readString(path);
                if (!isLegacy(source)) continue;
                var entry = new JavaModuleExportManifest.SceneEntry();
                String name = path.getFileName().toString().replaceFirst("\\.java$", "");
                entry.sceneId = target.modId + ":legacy/" + name.toLowerCase(Locale.ROOT);
                entry.sceneKey = entry.sceneId;
                entry.javaFiles.add(relative);
                found.put(relative, new Legacy(entry.sceneId, entry, relative, source));
            }
        }
        return new ArrayList<>(found.values());
    }

    private static boolean isLegacy(String source) {
        if (!source.contains("addStoryBoard") || !source.contains("SceneBuilder")) return false;
        if (!source.contains("EditableScenes")) return true;
        try {
            return parse(source).findAll(MethodCallExpr.class).stream().noneMatch(call -> call.getNameAsString().equals("register")
                && call.getScope().map(Object::toString).orElse("").endsWith("EditableScenes"));
        } catch (IllegalArgumentException invalidSource) {
            return true;
        }
    }

    private record Converted(String source, String className, DslScene document) {}

    private static Converted convert(JavaModuleScanResult.TargetProject target, Legacy legacy, List<String> notes) {
        CompilationUnit unit = parse(legacy.source);
        LexicalPreservingPrinter.setup(unit);
        var type = unit.getPrimaryType().orElseGet(() -> unit.getType(0)).asClassOrInterfaceDeclaration();
        String className = type.getNameAsString();
        if (!type.getMethodsByName("loadDocument").isEmpty() || type.getFields().stream()
            .flatMap(f -> f.getVariables().stream()).anyMatch(v -> Set.of("RUNTIME", "SOURCE", "DOCUMENT").contains(v.getNameAsString())))
            throw new IllegalArgumentException("Legacy source already declares reserved Java + Lib members");
        MethodDeclaration register = method(type, "register");
        List<Board> boards = new ArrayList<>();
        for (MethodCallExpr call : register.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("addStoryBoard")) continue;
            if (call.getArguments().size() < 2 || !call.getArgument(1).isMethodReferenceExpr())
                throw new IllegalArgumentException("Expected an exported storyboard method reference at " + call.getBegin().orElseThrow());
            var reference = call.getArgument(1).asMethodReferenceExpr();
            if (!Set.of(className, unit.getPackageDeclaration().orElseThrow().getNameAsString() + "." + className)
                .contains(reference.getScope().toString()))
                throw new IllegalArgumentException("External storyboard reference requires manual migration: " + reference);
            MethodDeclaration body = method(type, reference.getIdentifier());
            if (!body.isStatic() || body.getParameters().size() != 2 || body.getBody().isEmpty())
                throw new IllegalArgumentException("Unsupported storyboard signature: " + body.getNameAsString());
            if (boards.stream().anyMatch(b -> b.method == body))
                throw new IllegalArgumentException("Repeated storyboard method requires manual migration: " + body.getNameAsString());
            boards.add(new Board(body, resourceId(call.getArgument(0)), titlePath(body)));
        }
        if (boards.isEmpty()) throw new IllegalArgumentException("No exported storyboards found");

        DslScene document = embeddedDocument(type);
        if (document == null) document = new DslScene();
        String originalId = document.id == null ? legacy.key : document.id;
        document.id = target.modId + ":" + originalId.substring(originalId.indexOf(':') + 1);
        if (boards.size() == 1 && boards.getFirst().titlePath != null) document.id = target.modId + ":" + boards.getFirst().titlePath;
        document.internalId = document.internalId == null ? legacy.key : document.internalId;
        document.runtimeSource = document.id;
        document.exported = true;
        document.editable = true;
        document.pack = null;
        document.requiredCapabilities = List.of("pondererlib:core", "pondererlib:text", "pondererlib:view");
        List<String> items = new ArrayList<>();
        for (MethodCallExpr call : register.findAll(MethodCallExpr.class)) {
            if (!call.getNameAsString().equals("forComponents") || call.getArguments().size() != 1) continue;
            for (MethodCallExpr id : call.getArgument(0).findAll(MethodCallExpr.class)) {
                if (id.getNameAsString().equals("fromNamespaceAndPath") || id.getNameAsString().equals("parse"))
                    items.add(resourceId(id));
            }
        }
        if (!items.isEmpty()) document.items = List.copyOf(new LinkedHashSet<>(items));
        if (document.items == null || document.items.isEmpty()) throw new IllegalArgumentException("Cannot recover carrier items");
        var oldSegments = document.scenes;
        var segments = new ArrayList<DslScene.SceneSegment>();
        var callbacks = new StringBuilder("private static void installLegacySteps(com.nododiiiii.ponderer.runtime.api.RuntimeEngine engine) {\n");
        var helpers = new ArrayList<MethodDeclaration>();
        int[] fragmentIndex = {0};
        for (int index = 0; index < boards.size(); index++) {
            Board board = boards.get(index);
            var segment = new DslScene.SceneSegment();
            segment.id = oldSegments != null && index < oldSegments.size() && oldSegments.get(index).id != null
                ? oldSegments.get(index).id : "part" + (index + 1);
            String prefix = document.id.substring(document.id.indexOf(':') + 1) + "_";
            if (boards.size() > 1 && board.titlePath != null && board.titlePath.startsWith(prefix))
                segment.id = board.titlePath.substring(prefix.length());
            segment.title = oldSegments != null && index < oldSegments.size() ? oldSegments.get(index).title : null;
            segment.explicitInitialization = true;
            segment.structure = board.structure;
            segment.steps = new ArrayList<>();
            convertBody(target, legacy, document, segment, board, boards.size(), callbacks, helpers, fragmentIndex, notes);
            segments.add(segment);
            String scene = board.method.getParameter(0).getNameAsString();
            String util = board.method.getParameter(1).getNameAsString();
            board.method.setBody(parseBlock("{ RUNTIME.storyboard(com.nododiiiii.ponderer.scene.SceneSnapshot.of(DOCUMENT), "
                + index + ").program(" + scene + ", " + util + "); }"));
        }
        callbacks.append("}\n");
        document.scenes = segments;
        document.structures = boards.stream().map(Board::structure).distinct().toList();
        document.structure = null;
        if (document.title == null) document.title = segments.getFirst().title;

        replaceSupport(unit, target);
        for (String imported : List.of("com.nododiiiii.ponderer.runtime.api.JavaScene", "com.nododiiiii.ponderer.runtime.api.EditableScenes",
            "com.nododiiiii.ponderer.runtime.api.RuntimeEngine", "com.nododiiiii.ponderer.runtime.spi.AssetStructureSource",
            "com.nododiiiii.ponderer.runtime.spi.IngredientResolver", "com.nododiiiii.ponderer.runtime.spi.RegistrationContribution",
            "com.nododiiiii.ponderer.scene.DslScene", "com.nododiiiii.ponderer.scene.LocalizedText", "com.google.gson.JsonParser",
            "com.google.gson.JsonObject", "java.util.List", "java.util.Arrays", "java.util.Map")) unit.addImport(imported);

        var plan = new JavaModuleScanResult.ScenePlan();
        plan.className = className;
        String members = LibExportSources.members(target, plan, document)
            .replace("return engine;", "installLegacySteps(engine);\n        return engine;");
        for (var member : parse("class Members {\n" + members + callbacks + "\n}").getClassByName("Members").orElseThrow().getMembers()) {
            if (member.isMethodDeclaration() && Set.of("prepare", "close")
                .contains(member.asMethodDeclaration().getNameAsString())) continue;
            if (member.isMethodDeclaration() && !type.getMethodsByName(member.asMethodDeclaration().getNameAsString()).isEmpty())
                throw new IllegalArgumentException("Reserved Java + Lib method already exists: " + member.asMethodDeclaration().getNameAsString());
            type.addMember(member.clone());
        }
        for (MethodDeclaration helper : helpers) {
            replaceSupport(helper, target);
            // Detached statements retain their original lexical tokens; reparse the rewritten AST before insertion.
            addMethod(type, helper.toString());
        }
        if (type.getMethodsByName("prepare").isEmpty()) addMethod(type, "public static void prepare() { reload(); }");
        else method(type, "prepare").getBody().orElseThrow().addStatement(0, statement("reload();"));
        if (!type.getMethodsByName("active").isEmpty())
            method(type, "active").setBody(parseBlock("{ return !RUNTIME.catalog().getActiveScenes().isEmpty(); }"));
        if (type.getMethodsByName("close").isEmpty()) addMethod(type, "public static void close() { RUNTIME.close(); }");
        else method(type, "close").getBody().orElseThrow().addStatement(0, statement("RUNTIME.close();"));
        register.getBody().orElseThrow().addStatement(0, statement("if (!reload() || RUNTIME.catalog().getActiveScenes().isEmpty()) return;"));
        if (!type.getMethodsByName("registerTags").isEmpty()) method(type, "registerTags").getBody().orElseThrow()
            .addStatement(0, statement("if (DOCUMENT == null && !reload() || RUNTIME.catalog().getActiveScenes().isEmpty()) return;"));
        register.getBody().orElseThrow().addStatement(1, statement("RUNTIME.catalog().clearPonderIdMapping();"));
        for (int i = 0; i < segments.size(); i++) {
            String id = boards.get(i).titlePath == null ? document.id + (segments.size() > 1 ? "_" + segments.get(i).id : "")
                : target.modId + ":" + boards.get(i).titlePath;
            register.getBody().orElseThrow().addStatement(i + 2, statement("RUNTIME.catalog().registerPonderIdMapping("
                + quote(id) + ", DOCUMENT.sceneKey(), " + i + ");"));
        }
        String source = LexicalPreservingPrinter.print(unit);
        JavaSceneSource.read(source, document.id);
        return new Converted(source, unit.getPackageDeclaration().orElseThrow().getNameAsString() + "." + className, document);
    }

    private static void convertBody(JavaModuleScanResult.TargetProject target, Legacy legacy, DslScene document,
                                    DslScene.SceneSegment segment, Board board, int boardCount,
                                    StringBuilder callbacks, List<MethodDeclaration> helpers, int[] fragmentIndex, List<String> notes) {
        String scene = board.method.getParameter(0).getNameAsString();
        String context = "legacyActions";
        List<Statement> statements = new ArrayList<>(board.method.getBody().orElseThrow().getStatements());
        Set<String> identifiers = new HashSet<>();
        board.method.findAll(SimpleName.class).forEach(name -> identifiers.add(name.asString()));
        while (identifiers.contains(context)) context += "_";
        if (statements.stream().filter(LegacyJavaExportMigration::contextDeclaration).count() > 1) {
            addFragment(target, legacy, segment, board, context, statements, callbacks, helpers, fragmentIndex, notes);
            return;
        }
        for (Statement statement : statements) if (contextDeclaration(statement))
            context = statement.asExpressionStmt().getExpression().asVariableDeclarationExpr().getVariable(0).getNameAsString();
        boolean tail = false;
        boolean keyframe = false;
        boolean uncertainTextIndex = false;
        int textIndex = 0;
        String expectedTitlePath = document.id.substring(document.id.indexOf(':') + 1)
            + (boardCount > 1 ? "_" + segment.id : "");
        String titlePath = board.titlePath == null ? expectedTitlePath : board.titlePath;
        List<Statement> nativeStatements = new ArrayList<>();
        for (int i = 0; i < statements.size(); i++) {
            Statement current = statements.get(i);
            if (contextDeclaration(current)) continue;
            MethodCallExpr call = current.isExpressionStmt() && current.asExpressionStmt().getExpression().isMethodCallExpr()
                ? current.asExpressionStmt().getExpression().asMethodCallExpr() : null;
            if (call != null && call.getScope().map(Object::toString).orElse("").equals(scene)) {
                if (call.getNameAsString().equals("title") && i == 0 && call.getArguments().size() == 2
                    && call.getArgument(1).isStringLiteralExpr()) {
                    segment.title = LocalizedText.of(call.getArgument(1).asStringLiteralExpr().asString());
                    if (call.getArgument(0).isStringLiteralExpr()) {
                        titlePath = call.getArgument(0).asStringLiteralExpr().asString();
                        segment.title = translated(target, titlePath, "header", segment.title);
                    }
                    if (titlePath.equals(expectedTitlePath)) continue;
                }
                if (!tail && call.getNameAsString().equals("addKeyframe") && call.getArguments().isEmpty()) {
                    keyframe = true;
                    continue;
                }
            }
            // Keep declarations, early returns and parameter mutations with the entire remaining scope.
            tail |= !current.findAll(VariableDeclarationExpr.class).isEmpty() || !current.findAll(ReturnStmt.class).isEmpty()
                || current.isLocalClassDeclarationStmt() || current.isLocalRecordDeclarationStmt()
                || !current.findAll(AssignExpr.class).isEmpty() || current.findAll(UnaryExpr.class).stream()
                    .anyMatch(u -> Set.of(UnaryExpr.Operator.POSTFIX_INCREMENT, UnaryExpr.Operator.POSTFIX_DECREMENT,
                        UnaryExpr.Operator.PREFIX_INCREMENT, UnaryExpr.Operator.PREFIX_DECREMENT).contains(u.getOperator()));
            DslScene.DslStep known = tail || call == null ? null : knownStep(call, scene, context, board.structure);
            if (known != null && "text".equals(known.type)) {
                textIndex++;
                if (uncertainTextIndex) known = null;
                else known.text = translated(target, titlePath, "text_" + textIndex, known.text);
            }
            if (known != null) {
                if (!nativeStatements.isEmpty()) {
                    addFragment(target, legacy, segment, board, context, nativeStatements, callbacks, helpers, fragmentIndex, notes);
                    nativeStatements.clear();
                }
                known.attachKeyFrame = keyframe ? true : null;
                known.id = "migrated_" + segment.steps.size();
                segment.steps.add(known);
                keyframe = false;
            } else {
                if (keyframe) nativeStatements.add(statement(scene + ".addKeyframe();"));
                keyframe = false;
                nativeStatements.add(current.clone());
                if (call == null || !call.getNameAsString().equals("preScanBounds")) uncertainTextIndex = true;
            }
        }
        if (keyframe) nativeStatements.add(statement(scene + ".addKeyframe();"));
        if (!nativeStatements.isEmpty()) addFragment(target, legacy, segment, board, context, nativeStatements,
            callbacks, helpers, fragmentIndex, notes);
    }

    private static void addFragment(JavaModuleScanResult.TargetProject target, Legacy legacy, DslScene.SceneSegment segment,
                                    Board board, String context, List<Statement> statements, StringBuilder callbacks,
                                    List<MethodDeclaration> helpers, int[] fragmentIndex, List<String> notes) {
        int index = fragmentIndex[0]++;
        String helperName = "legacyFragment$" + index;
        String id = target.modId + ":legacy/" + board.method.findAncestor(ClassOrInterfaceDeclaration.class).orElseThrow()
            .getNameAsString().toLowerCase(Locale.ROOT) + "/" + index;
        var helper = parse("class Fragment { private static void " + helperName + "("
            + board.method.getParameter(0) + ", " + board.method.getParameter(1) + ", "
            + ACTIONS + ".Context " + context + ") {} }").getClassByName("Fragment").orElseThrow().getMethods().getFirst();
        for (Statement item : statements) helper.getBody().orElseThrow().addStatement(item.clone());
        helpers.add(helper);
        callbacks.append("    engine.steps().registerJava(").append(quote(id)).append(", ")
            .append(quote("Java: " + board.method.getNameAsString() + " #" + (index + 1)))
            .append(", legacyContext -> com.nododiiiii.ponderer.runtime.api.SceneTexts.withNativeText(legacyContext.builder().getScene(), () -> ")
            .append(helperName).append("(legacyContext.builder(), legacyContext.util(), legacyContext.actions())));\n");
        var step = new DslScene.DslStep();
        step.id = "migrated_" + segment.steps.size();
        step.type = id;
        step.parameters = new JsonObject();
        segment.steps.add(step);
        notes.add(legacy.path + ": " + board.method.getNameAsString() + " preserved as " + id);
    }

    private static DslScene.DslStep knownStep(MethodCallExpr call, String scene, String context, String structure) {
        try {
            String scope = call.getScope().map(Object::toString).orElse("");
            List<String> args = call.getArguments().stream().map(Object::toString).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            String name = call.getNameAsString();
            if (scope.equals(scene)) {
                if (!name.equals("idle") || args.size() != 1) return null;
            } else {
                if (!(scope.equals(SUPPORT) || scope.endsWith("." + SUPPORT))) return null;
                if (args.isEmpty() || !args.removeFirst().equals(scene)) return null;
                if (!args.isEmpty() && args.getFirst().equals(context)) args.removeFirst();
                if (name.equals("preScanBounds") || name.equals("createPlayer")) return null;
                if (name.equals("showStructure")) args.addFirst(quote(structure));
                if (name.equals("showText")) args.set(0, "LocalizedText.of(" + args.getFirst() + ")");
                if (name.equals("rotateCameraY") && args.size() == 2) args.add(1, "0.0f");
                if (name.equals("showExtraStructure")) {
                    String asset = resourceId(call.getArgument(2));
                    int colon = asset.indexOf(':');
                    String path = asset.substring(colon + 1);
                    if (!path.startsWith("ponder/") || !path.endsWith(".nbt")) return null;
                    args.set(0, quote(asset.substring(0, colon + 1) + path.substring(7, path.length() - 4)));
                }
            }
            String source = "class Known { private static DslScene loadDocument() { var document = new DslScene(); document.id = \"migration:known\"; "
                + "document.scenes = List.of(segment()); return document; } private static DslScene.SceneSegment segment() { "
                + "var scene = new JavaScene(\"known\", null); scene." + name + "(" + String.join(", ", args) + "); return scene.build(); } }";
            return JavaSceneSource.read(source, "migration:known").scenes.getFirst().steps.getFirst();
        } catch (RuntimeException uncertain) {
            return null;
        }
    }

    private static boolean contextDeclaration(Statement statement) {
        if (!statement.isExpressionStmt() || !statement.asExpressionStmt().getExpression().isVariableDeclarationExpr()) return false;
        var variables = statement.asExpressionStmt().getExpression().asVariableDeclarationExpr().getVariables();
        if (variables.size() != 1) return false;
        return variables.get(0).getInitializer().filter(Expression::isObjectCreationExpr)
            .map(Expression::asObjectCreationExpr).filter(c -> c.getArguments().isEmpty())
            .map(c -> c.getType().toString().endsWith(SUPPORT + ".Context")).orElse(false);
    }

    private static String titlePath(MethodDeclaration method) {
        var statements = method.getBody().orElseThrow().getStatements();
        if (statements.isEmpty() || !statements.get(0).isExpressionStmt()
            || !statements.get(0).asExpressionStmt().getExpression().isMethodCallExpr()) return null;
        var call = statements.get(0).asExpressionStmt().getExpression().asMethodCallExpr();
        return call.getNameAsString().equals("title") && call.getArguments().size() == 2 && call.getArgument(0).isStringLiteralExpr()
            ? call.getArgument(0).asStringLiteralExpr().asString() : null;
    }

    private static LocalizedText translated(JavaModuleScanResult.TargetProject target, String title, String key, LocalizedText fallback) {
        Map<String, String> values = new TreeMap<>(fallback.getAllTranslations());
        Path directory = target.resourcesRoot.resolve("assets/" + target.modId + "/lang");
        String translationKey = target.modId + ".ponder." + title + "." + key;
        if (Files.isDirectory(directory)) try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                try (var reader = Files.newBufferedReader(path)) {
                    var entries = JsonParser.parseReader(reader).getAsJsonObject();
                    if (entries.has(translationKey) && entries.get(translationKey).isJsonPrimitive())
                        values.put(path.getFileName().toString().replaceFirst("\\.json$", ""), entries.get(translationKey).getAsString());
                }
            }
        } catch (IOException error) { throw new IllegalArgumentException("Cannot read legacy translations", error); }
        return LocalizedText.ofMap(values);
    }

    private static DslScene embeddedDocument(ClassOrInterfaceDeclaration type) {
        for (VariableDeclarator variable : type.getFields().stream().flatMap(f -> f.getVariables().stream()).toList()) {
            if (!variable.getNameAsString().equals("VARIANT") || variable.getInitializer().isEmpty()) continue;
            try { return SceneCodec.decode(stringValue(variable.getInitializer().orElseThrow())); }
            catch (RuntimeException ignored) { return null; }
        }
        return null;
    }

    private static String stringValue(Expression expression) {
        if (expression.isStringLiteralExpr()) return expression.asStringLiteralExpr().asString();
        if (expression.isEnclosedExpr()) return stringValue(expression.asEnclosedExpr().getInner());
        if (expression.isBinaryExpr() && expression.asBinaryExpr().getOperator() == BinaryExpr.Operator.PLUS)
            return stringValue(expression.asBinaryExpr().getLeft()) + stringValue(expression.asBinaryExpr().getRight());
        if (expression.isMethodCallExpr()) {
            var call = expression.asMethodCallExpr();
            if (call.getScope().map(Object::toString).orElse("").equals("String") && call.getNameAsString().equals("join")) {
                var values = call.getArguments().stream().map(LegacyJavaExportMigration::stringValue).toList();
                return String.join(values.getFirst(), values.subList(1, values.size()));
            }
        }
        throw new IllegalArgumentException("Expected literal string: " + expression);
    }

    private static String resourceId(Expression expression) {
        if (expression.isStringLiteralExpr()) return stringValue(expression);
        List<Expression> args;
        if (expression.isMethodCallExpr()) args = expression.asMethodCallExpr().getArguments();
        else if (expression.isObjectCreationExpr()) args = expression.asObjectCreationExpr().getArguments();
        else throw new IllegalArgumentException("Expected literal resource ID: " + expression);
        if (args.size() == 1) return stringValue(args.getFirst());
        if (args.size() == 2) return stringValue(args.getFirst()) + ":" + stringValue(args.get(1));
        throw new IllegalArgumentException("Expected literal resource ID: " + expression);
    }

    private static void replaceSupport(Node node, JavaModuleScanResult.TargetProject target) {
        if (node instanceof CompilationUnit unit) {
            for (var imported : unit.getImports()) {
                String name = imported.getNameAsString();
                String old = target.generatedPackage + "." + SUPPORT;
                if (name.equals(old) || name.startsWith(old + ".")) imported.setName(ACTIONS + name.substring(old.length()));
            }
            unit.addImport(ACTIONS);
        }
        for (NameExpr name : node.findAll(NameExpr.class)) if (name.getNameAsString().equals(SUPPORT)) name.setName("PonderActions");
        for (ClassOrInterfaceType type : node.findAll(ClassOrInterfaceType.class))
            if (type.getNameAsString().equals(SUPPORT)) {
                if (type.getScope().isPresent()) type.setScope(parser().parseClassOrInterfaceType("com.nododiiiii.ponderer.runtime.api").getResult().orElseThrow());
                type.setName("PonderActions");
            }
        for (FieldAccessExpr field : node.findAll(FieldAccessExpr.class))
            if (field.getNameAsString().equals(SUPPORT)) {
                field.setScope(parser().parseExpression("com.nododiiiii.ponderer.runtime.api").getResult().orElseThrow());
                field.setName("PonderActions");
            }
    }

    private static MethodDeclaration method(ClassOrInterfaceDeclaration type, String name) {
        var methods = type.getMethodsByName(name);
        if (methods.size() != 1) throw new IllegalArgumentException("Expected one method: " + name);
        return methods.getFirst();
    }

    private static void addMethod(ClassOrInterfaceDeclaration type, String source) {
        type.addMember(parse("class Added { " + source + " }").getClassByName("Added").orElseThrow().getMethods().getFirst().clone());
    }

    private static CompilationUnit parse(String source) {
        var result = parser().parse(source);
        if (!result.isSuccessful()) throw new IllegalArgumentException(result.getProblems().toString());
        return result.getResult().orElseThrow();
    }

    private static JavaParser parser() {
        return new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    }

    private static com.github.javaparser.ast.stmt.BlockStmt parseBlock(String source) {
        return parser().parseBlock(source).getResult().orElseThrow();
    }

    private static Statement statement(String source) {
        return parser().parseStatement(source).getResult().orElseThrow();
    }

    private static String supportPath(JavaModuleScanResult.TargetProject target) {
        return "src/main/java/" + target.generatedPackage.replace('.', '/') + "/" + SUPPORT + ".java";
    }

    private static Path inside(JavaModuleScanResult.TargetProject target, String relative) throws IOException {
        Path path = target.targetRoot.resolve(relative).normalize();
        if (!path.startsWith(target.targetRoot) || Files.isSymbolicLink(path)) throw new IOException("Unsafe legacy export path: " + path);
        return path;
    }

    private static String relative(JavaModuleScanResult.TargetProject target, Path path) {
        return target.targetRoot.relativize(path).toString().replace('\\', '/');
    }

    private static String quote(String value) { return GSON.toJson(value); }
}
