package org.example.docsync.analysis;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import org.example.docsync.error.UnsupportedConstructException;
import org.example.docsync.io.ProjectFiles;
import org.example.docsync.io.RootPaths;
import org.example.docsync.model.SourceModel;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/** Extracts the SourceModel from the four Java sources (impl-plan 6A, N1 to N5) with a parse-only syntax tree. */
public final class JavaAnalyzer {
    private final RootPaths roots;
    private final ProjectFiles files;
    private final Supplier<JavaCompiler> compiler;

    public JavaAnalyzer(RootPaths roots, ProjectFiles files) {
        this(roots, files, ToolProvider::getSystemJavaCompiler);
    }

    JavaAnalyzer(RootPaths roots, ProjectFiles files, Supplier<JavaCompiler> compiler) {
        this.roots = roots;
        this.files = files;
        this.compiler = compiler;
    }

    public SourceModel analyze() {
        return analyze(false);
    }

    // The model of all four documents: the E2 model plus the HTML controls, the JS rules and the backend rules (E3).
    public SourceModel analyzeWithUi() {
        return analyze(true);
    }

    private SourceModel analyze(boolean withUi) {
        String apiText = files.readText(RootPaths.Name.API_ROUTES_SOURCE);
        String errorText = files.readText(RootPaths.Name.ERROR_CODE_SOURCE);
        String studentText = files.readText(RootPaths.Name.STUDENT_RECORD_SOURCE);
        String serviceText = files.readText(RootPaths.Name.SERVICE_SOURCE);

        Parsed api = parse(RootPaths.Name.API_ROUTES_SOURCE, apiText);
        ClassTree apiClass = topLevel(RootPaths.Name.API_ROUTES_SOURCE, api.unit(), Tree.Kind.CLASS);
        ClassTree errorEnum = topLevel(RootPaths.Name.ERROR_CODE_SOURCE,
            parse(RootPaths.Name.ERROR_CODE_SOURCE, errorText).unit(), Tree.Kind.ENUM);
        CompilationUnitTree studentUnit = parse(RootPaths.Name.STUDENT_RECORD_SOURCE, studentText).unit();
        ClassTree serviceClass = topLevel(RootPaths.Name.SERVICE_SOURCE,
            parse(RootPaths.Name.SERVICE_SOURCE, serviceText).unit(), Tree.Kind.CLASS);

        List<String> formParameters = formParameters(api, apiClass);
        SourceModel model = new SourceModel(
            routes(api, apiClass),
            errorCodes(errorEnum),
            studentFields(studentUnit),
            formParameters,
            serviceMethods(serviceClass),
            sha256Hex(serviceText));
        if (!withUi) {
            return model;
        }
        var form = new HtmlAnalyzer(roots, files).analyze();
        var script = new JsAnalyzer(roots, files).analyze();
        var backend = BackendRules.extract(serviceClass, formParameters, roots.display(RootPaths.Name.SERVICE_SOURCE));
        checkJoin(form, script, formParameters);
        return model.withUi(form, script.rules(), script.payload(), backend);
    }

    // S6: the validationRules keys, the payload formData keys and the HTML control names must agree.
    private void checkJoin(SourceModel.UiForm form, JsAnalyzer.Result script, List<String> formParameters) {
        RootPaths.Name js = RootPaths.Name.FRONTEND_JS;
        Set<String> payloadApi = new HashSet<>();
        Set<String> payloadUi = new HashSet<>();
        for (SourceModel.PayloadEntry entry : script.payload()) {
            payloadApi.add(entry.apiField());
            payloadUi.add(entry.uiField());
        }
        if (script.payload().size() != formParameters.size() || !payloadApi.equals(new HashSet<>(formParameters))) {
            throw unsupported(js, "the payload keys must equal the create form parameters " + formParameters);
        }
        Set<String> ruleKeys = new HashSet<>();
        script.rules().forEach(rule -> ruleKeys.add(rule.key()));
        Set<String> controlNames = new HashSet<>();
        form.controls().forEach(control -> controlNames.add(control.name()));
        if (!payloadUi.equals(ruleKeys) || !ruleKeys.equals(controlNames)) {
            throw unsupported(js, "the validationRules keys " + ruleKeys + ", the payload formData keys " + payloadUi
                + " and the form control names " + controlNames + " must agree");
        }
    }

    // ---- parsing ----

    private record Parsed(CompilationUnitTree unit, SourcePositions positions, String text) {
    }

    private Parsed parse(RootPaths.Name name, String text) {
        JavaCompiler javac = compiler.get();
        if (javac == null) {
            throw new UnsupportedConstructException("No Java compiler is available; run the engine on a JDK.");
        }
        String file = roots.display(name);
        String simpleName = file.substring(file.lastIndexOf('/') + 1);
        JavaFileObject source = new SimpleJavaFileObject(URI.create("string:///" + simpleName), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        };
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) javac.getTask(null, null, diagnostics, List.of("-proc:none"), null, List.of(source));
        Iterable<? extends CompilationUnitTree> units;
        try {
            units = task.parse();
        } catch (IOException e) {
            throw new UnsupportedConstructException(file + ": the source could not be parsed", e);
        }
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                throw new UnsupportedConstructException(file + ": the source could not be parsed (line "
                    + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null) + ")");
            }
        }
        return new Parsed(units.iterator().next(), Trees.instance(task).getSourcePositions(), text);
    }

    private ClassTree topLevel(RootPaths.Name name, CompilationUnitTree unit, Tree.Kind kind) {
        List<ClassTree> found = new ArrayList<>();
        for (Tree declaration : unit.getTypeDecls()) {
            if (declaration.getKind() == kind) {
                found.add((ClassTree) declaration);
            }
        }
        if (found.size() != 1) {
            throw unsupported(name, "expected exactly one top-level " + kind.toString().toLowerCase() + " but found " + found.size());
        }
        return found.get(0);
    }

    private UnsupportedConstructException unsupported(RootPaths.Name name, String construct) {
        return new UnsupportedConstructException(roots.display(name) + ": " + construct);
    }

    // ---- N1 routes ----

    private List<SourceModel.Route> routes(Parsed api, ClassTree apiClass) {
        RootPaths.Name name = RootPaths.Name.API_ROUTES_SOURCE;
        List<MethodTree> candidates = new ArrayList<>();
        for (Tree member : apiClass.getMembers()) {
            if (member instanceof MethodTree method && method.getName().contentEquals("routes") && method.getParameters().isEmpty()) {
                candidates.add(method);
            }
        }
        if (candidates.size() != 1) {
            throw unsupported(name, "expected exactly one routes() method but found " + candidates.size());
        }
        BlockTree body = candidates.get(0).getBody();
        String shape = "routes() must consist of exactly one 'return List.of(new Route(...), ...)'";
        if (body == null || body.getStatements().size() != 1
            || !(body.getStatements().get(0) instanceof ReturnTree returned)
            || !(returned.getExpression() instanceof MethodInvocationTree listOf)
            || !isCall(listOf, "List", "of")) {
            throw unsupported(name, shape);
        }
        List<SourceModel.Route> routes = new ArrayList<>();
        int index = 0;
        for (ExpressionTree element : listOf.getArguments()) {
            index++;
            if (!(element instanceof NewClassTree route) || !(route.getIdentifier() instanceof IdentifierTree type)
                || !type.getName().contentEquals("Route") || route.getClassBody() != null) {
                throw unsupported(name, "routes() element " + index + " is not a 'new Route(...)' expression");
            }
            if (route.getArguments().size() != 6) {
                throw unsupported(name, "Route (route " + index + ") must have six arguments but has " + route.getArguments().size());
            }
            String[] values = new String[5];
            for (int i = 0; i < 5; i++) {
                values[i] = stringLiteral(api, route.getArguments().get(i));
                if (values[i] == null) {
                    throw unsupported(name, "Route argument " + (i + 1) + " is not a string literal (route " + index + ")");
                }
            }
            routes.add(new SourceModel.Route(values[0], values[1], values[2], values[3], values[4]));
        }
        if (routes.isEmpty()) {
            throw unsupported(name, "routes() contains no Route elements");
        }
        return routes;
    }

    // ---- N2 create handler lookup ----

    private List<String> formParameters(Parsed api, ClassTree apiClass) {
        RootPaths.Name name = RootPaths.Name.API_ROUTES_SOURCE;
        List<MethodInvocationTree> calls = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                if (isCall(node, "service", "createStudent") && node.getArguments().size() == 4) {
                    calls.add(node);
                }
                return super.visitMethodInvocation(node, unused);
            }
        }.scan(apiClass, null);
        if (calls.size() != 1) {
            throw unsupported(name, "expected exactly one service.createStudent(a1, a2, a3, a4) call but found " + calls.size());
        }
        List<String> keys = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int position = 0;
        for (ExpressionTree argument : calls.get(0).getArguments()) {
            position++;
            String key = null;
            if (argument instanceof MethodInvocationTree call && call.getMethodSelect() instanceof MemberSelectTree select
                && select.getIdentifier().contentEquals("getOrDefault") && call.getArguments().size() == 2
                && "".equals(stringLiteral(api, call.getArguments().get(1)))) {
                key = stringLiteral(api, call.getArguments().get(0));
            }
            if (key == null) {
                throw unsupported(name, "service.createStudent argument " + position
                    + " is not of the form <map>.getOrDefault(\"<key>\", \"\")");
            }
            if (!seen.add(key)) {
                throw unsupported(name, "service.createStudent has a duplicate form key \"" + key + "\" at argument " + position);
            }
            keys.add(key);
        }
        return keys;
    }

    // ---- N3 Student record ----

    private List<SourceModel.Field> studentFields(CompilationUnitTree unit) {
        RootPaths.Name name = RootPaths.Name.STUDENT_RECORD_SOURCE;
        List<ClassTree> records = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitClass(ClassTree node, Void unused) {
                if (node.getKind() == Tree.Kind.RECORD && node.getSimpleName().contentEquals("Student")) {
                    records.add(node);
                }
                return super.visitClass(node, unused);
            }
        }.scan(unit, null);
        if (records.size() != 1) {
            throw unsupported(name, "expected exactly one record Student but found " + records.size());
        }
        // In parse-only mode the record components are the non-static variable members.
        List<SourceModel.Field> fields = new ArrayList<>();
        for (Tree member : records.get(0).getMembers()) {
            if (member instanceof VariableTree variable && !variable.getModifiers().getFlags().contains(Modifier.STATIC)) {
                fields.add(new SourceModel.Field(variable.getName().toString(), variable.getType().toString()));
            }
        }
        if (fields.isEmpty()) {
            throw unsupported(name, "record Student has no components");
        }
        return fields;
    }

    // ---- N4 service methods and digest ----

    private List<SourceModel.ServiceMethod> serviceMethods(ClassTree serviceClass) {
        List<SourceModel.ServiceMethod> methods = new ArrayList<>();
        for (Tree member : serviceClass.getMembers()) {
            if (member instanceof MethodTree method && method.getReturnType() != null
                && method.getModifiers().getFlags().contains(Modifier.PUBLIC)) {
                List<String> parameters = new ArrayList<>();
                for (VariableTree parameter : method.getParameters()) {
                    parameters.add(parameter.getType() + " " + parameter.getName());
                }
                methods.add(new SourceModel.ServiceMethod(method.getName().toString(), parameters, method.getReturnType().toString()));
            }
        }
        if (methods.isEmpty()) {
            throw unsupported(RootPaths.Name.SERVICE_SOURCE, "no public service methods were found");
        }
        return methods;
    }

    private static String sha256Hex(String text) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // ---- N5 ErrorCode constants ----

    private List<SourceModel.ErrorCodeInfo> errorCodes(ClassTree errorEnum) {
        RootPaths.Name name = RootPaths.Name.ERROR_CODE_SOURCE;
        List<SourceModel.ErrorCodeInfo> codes = new ArrayList<>();
        for (Tree member : errorEnum.getMembers()) {
            // An enum constant is the only variable member whose initializer is "new <EnumName>(...)".
            if (member instanceof VariableTree constant && constant.getInitializer() instanceof NewClassTree creation
                && creation.getIdentifier() instanceof IdentifierTree type && type.getName().equals(errorEnum.getSimpleName())) {
                if (creation.getArguments().size() != 1
                    || !(creation.getArguments().get(0) instanceof LiteralTree literal)
                    || literal.getKind() != Tree.Kind.INT_LITERAL) {
                    throw unsupported(name, "enum constant " + constant.getName() + " must have exactly one integer literal argument");
                }
                codes.add(new SourceModel.ErrorCodeInfo(constant.getName().toString(), (Integer) literal.getValue()));
            }
        }
        if (codes.isEmpty()) {
            throw unsupported(name, "no enum constants were found");
        }
        return codes;
    }

    // ---- shared syntax helpers ----

    private static boolean isCall(MethodInvocationTree call, String receiver, String method) {
        return call.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals(method)
            && select.getExpression() instanceof IdentifierTree target
            && target.getName().contentEquals(receiver);
    }

    // The parser folds "a" + "b" into one literal node, so the source span must be a single quoted token.
    private static String stringLiteral(Parsed source, ExpressionTree expression) {
        if (!(expression instanceof LiteralTree literal) || literal.getKind() != Tree.Kind.STRING_LITERAL) {
            return null;
        }
        long start = source.positions().getStartPosition(source.unit(), expression);
        long end = source.positions().getEndPosition(source.unit(), expression);
        if (start < 0 || end <= start) {
            return null;
        }
        return isSingleQuotedToken(source.text().substring((int) start, (int) end)) ? (String) literal.getValue() : null;
    }

    private static boolean isSingleQuotedToken(String slice) {
        if (slice.length() < 2 || slice.charAt(0) != '"' || slice.charAt(slice.length() - 1) != '"') {
            return false;
        }
        for (int i = 1; i < slice.length() - 1; i++) {
            if (slice.charAt(i) == '\\') {
                i++;
            } else if (slice.charAt(i) == '"') {
                return false;
            }
        }
        return true;
    }
}
