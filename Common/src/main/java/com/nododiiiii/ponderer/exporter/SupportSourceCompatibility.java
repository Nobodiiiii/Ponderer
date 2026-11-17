package com.nododiiiii.ponderer.exporter;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Extend an installed Support without replacing its existing behavior or handwritten members. */
final class SupportSourceCompatibility {
    private SupportSourceCompatibility() {}

    static String extend(String existing, String latest) {
        CompilationUnit installed = parse(existing);
        CompilationUnit current = parse(latest);
        ClassOrInterfaceDeclaration target = installed.getClassByName("GeneratedPonderSupport").orElseThrow();
        ClassOrInterfaceDeclaration source = current.getClassByName("GeneratedPonderSupport").orElseThrow();
        LexicalPreservingPrinter.setup(installed);
        adaptLegacyMinecraftCalls(target);
        String compatible = LexicalPreservingPrinter.print(installed);
        Set<String> signatures = target.getMethods().stream().map(SupportSourceCompatibility::signature)
            .collect(java.util.stream.Collectors.toSet());
        List<MethodDeclaration> missing = source.getMethods().stream().filter(method -> method.isPublic() && method.isStatic())
            .filter(method -> !signatures.contains(signature(method))).toList();
        if (missing.isEmpty()) return compatible;

        Map<String, String> imports = new LinkedHashMap<>();
        for (var imported : current.getImports()) if (!imported.isStatic() && !imported.isAsterisk()) {
            String name = imported.getNameAsString();
            imports.put(name.substring(name.lastIndexOf('.') + 1), name);
        }
        for (MethodDeclaration method : missing) {
            MethodDeclaration addition;
            if (isStreamStructure(method)) addition = streamStructure(target, method);
            else if (Set.of("createPlayer", "spawnParticles").contains(method.getNameAsString())) addition = method.clone();
            else throw new IllegalArgumentException("Cannot extend existing Support without changing its implementation: " + signature(method));
            qualifyImports(addition, imports);
            // Fresh nodes prevent lexical token caches from undoing the qualified references.
            target.addMember(parse("class Addition { " + addition + " }").getClassByName("Addition").orElseThrow()
                .getMethods().getFirst().clone());
        }
        String result = LexicalPreservingPrinter.print(installed);
        var extended = parse(result).getClassByName("GeneratedPonderSupport").orElseThrow();
        for (var member : parse(compatible).getClassByName("GeneratedPonderSupport").orElseThrow().getMembers())
            if (extended.getMembers().stream().noneMatch(candidate -> candidate.equals(member)))
                throw new IllegalArgumentException("Support compatibility update would alter an existing member");
        return result;
    }

    private static void adaptLegacyMinecraftCalls(ClassOrInterfaceDeclaration installed) {
        for (ObjectCreationExpr creation : installed.findAll(ObjectCreationExpr.class)) {
            if (creation.getType().getNameAsString().equals("NbtAccounter") && creation.getArguments().size() == 1)
                creation.replace(parser().parseExpression("net.minecraft.nbt.NbtAccounter.create(" + creation.getArgument(0) + ")")
                    .getResult().orElseThrow());
        }
        // Early 1.21.1 exports still emitted the pre-component ItemStack NBT calls.
        for (MethodDeclaration method : installed.getMethods()) {
            if (!Set.of("parseItemStackSpec", "applyPatchToItemStack").contains(method.getNameAsString())) continue;
            for (MethodCallExpr call : method.findAll(MethodCallExpr.class)) {
                if (call.getNameAsString().equals("setTag") && call.getArguments().size() == 1
                    && call.getScope().filter(scope -> isItemStackLocal(method, scope)).isPresent()) {
                    var parent = call.getParentNode().orElseThrow();
                    if (!(parent instanceof com.github.javaparser.ast.stmt.ExpressionStmt))
                        throw new IllegalArgumentException("Unsupported legacy ItemStack NBT assignment");
                    String stack = call.getScope().orElseThrow().toString();
                    String tag = freeName(method, "ponderer$legacyTag");
                    parent.replace(parser().parseBlock("{ net.minecraft.nbt.CompoundTag " + tag + " = " + call.getArgument(0) + "; "
                        + "if (" + tag + " == null) " + stack + ".remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA); "
                        + "else " + stack + ".set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, "
                        + "net.minecraft.world.item.component.CustomData.of(" + tag + ")); }").getResult().orElseThrow());
                } else if (call.getNameAsString().equals("merge") && call.getArguments().size() == 1
                    && call.getScope().filter(Expression::isMethodCallExpr).isPresent()) {
                    var getter = call.getScope().orElseThrow().asMethodCallExpr();
                    if (!getter.getNameAsString().equals("getOrCreateTag") || !getter.getArguments().isEmpty()
                        || getter.getScope().filter(scope -> isItemStackLocal(method, scope)).isEmpty()) continue;
                    var parent = call.getParentNode().orElseThrow();
                    if (!(parent instanceof com.github.javaparser.ast.stmt.ExpressionStmt))
                        throw new IllegalArgumentException("Unsupported legacy ItemStack NBT merge");
                    String stack = getter.getScope().orElseThrow().toString();
                    String tag = freeName(method, "ponderer$legacyTag");
                    parent.replace(parser().parseBlock("{ net.minecraft.nbt.CompoundTag " + tag + " = " + stack
                        + ".getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, "
                        + "net.minecraft.world.item.component.CustomData.EMPTY).copyTag(); " + tag + ".merge(" + call.getArgument(0) + "); "
                        + stack + ".set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, "
                        + "net.minecraft.world.item.component.CustomData.of(" + tag + ")); }").getResult().orElseThrow());
                }
            }
        }
    }

    private static boolean isItemStackLocal(MethodDeclaration method, Expression scope) {
        return scope.isNameExpr() && method.findAll(VariableDeclarator.class).stream().anyMatch(variable ->
            variable.getNameAsString().equals(scope.asNameExpr().getNameAsString()) && variable.getType().asString().endsWith("ItemStack"));
    }

    private static String freeName(MethodDeclaration method, String prefix) {
        String name = prefix;
        var names = method.findAll(com.github.javaparser.ast.expr.SimpleName.class).stream().map(Object::toString).toList();
        while (names.contains(name)) name += "_";
        return name;
    }

    private static MethodDeclaration streamStructure(ClassOrInterfaceDeclaration installed, MethodDeclaration stream) {
        var resource = installed.getMethodsByName("showExtraStructure").stream()
            .filter(method -> method.isPublic() && method.isStatic() && method.getParameters().size() == stream.getParameters().size())
            .filter(method -> method.getParameter(2).getType().asString().endsWith("ResourceLocation"))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Existing Support has no resource structure loader"));
        var statements = resource.getBody().orElseThrow().getStatements();
        int loaded = -1;
        for (int index = 0; index < statements.size(); index++) {
            if (statements.get(index).isTryStmt() && statements.get(index).findAll(AssignExpr.class).stream()
                .anyMatch(assignment -> assignment.getTarget().toString().equals("root"))) {
                loaded = index;
                break;
            }
        }
        if (loaded < 0) throw new IllegalArgumentException("Cannot identify the existing Support structure loader; original source preserved");

        MethodDeclaration result = resource.clone();
        result.removeComment();
        result.getParameter(2).setType("java.io.InputStream");
        result.getThrownExceptions().clear();
        stream.getThrownExceptions().forEach(exception -> result.addThrownException(exception.clone()));
        var body = stream.getBody().orElseThrow().clone();
        body.getStatements().remove(body.getStatements().size() - 1);
        Map<String, String> parameters = new LinkedHashMap<>();
        for (int i = 0; i < stream.getParameters().size(); i++)
            parameters.put(stream.getParameter(i).getNameAsString(), resource.getParameter(i).getNameAsString());
        for (NameExpr name : body.findAll(NameExpr.class))
            if (parameters.containsKey(name.getNameAsString())) name.setName(parameters.get(name.getNameAsString()));
        // Reuse the installed renderer so stream loading observes the same section ownership and customizations.
        for (int index = loaded + 1; index < statements.size(); index++) body.addStatement(statements.get(index).clone());
        result.setBody(body);
        return result;
    }

    private static boolean isStreamStructure(MethodDeclaration method) {
        return method.getNameAsString().equals("showExtraStructure") && method.getParameters().size() > 2
            && method.getParameter(2).getType().asString().endsWith("InputStream");
    }

    private static String signature(MethodDeclaration method) {
        return method.getNameAsString() + "(" + method.getParameters().stream()
            .map(parameter -> typeName(parameter.getType()) + (parameter.isVarArgs() ? "..." : ""))
            .collect(java.util.stream.Collectors.joining(",")) + ")";
    }

    private static String typeName(com.github.javaparser.ast.type.Type type) {
        var copy = type.clone();
        copy.findAll(ClassOrInterfaceType.class).forEach(ClassOrInterfaceType::removeScope);
        return copy.asString();
    }

    private static void qualifyImports(MethodDeclaration method, Map<String, String> imports) {
        for (ClassOrInterfaceType type : method.findAll(ClassOrInterfaceType.class)) {
            if (type.getScope().isPresent()) continue;
            String name = imports.get(type.getNameAsString());
            if (name != null) type.setScope(parser().parseClassOrInterfaceType(name.substring(0, name.lastIndexOf('.'))).getResult().orElseThrow());
        }
        for (NameExpr name : method.findAll(NameExpr.class)) {
            String qualified = imports.get(name.getNameAsString());
            if (qualified != null) name.replace(parser().parseExpression(qualified).getResult().orElseThrow());
        }
    }

    private static CompilationUnit parse(String source) {
        var result = parser().parse(source);
        if (!result.isSuccessful()) throw new IllegalArgumentException("Cannot inspect Support source: " + result.getProblems());
        return result.getResult().orElseThrow();
    }

    private static JavaParser parser() {
        return new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    }
}
