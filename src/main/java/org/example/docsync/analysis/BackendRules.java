package org.example.docsync.analysis;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.UnaryTree;
import org.example.docsync.error.UnsupportedConstructException;
import org.example.docsync.model.RuleSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts the backend validation rules from {@code StudentService.validateStudentInput} (plan section 6B, S4).
 * Only the three idioms N1, C1 and R1 are understood; any other shape is an unsupported construct.
 */
final class BackendRules {
    private static final String METHOD = "validateStudentInput";

    private final String file;

    private BackendRules(String file) {
        this.file = file;
    }

    static List<RuleSpec> extract(ClassTree service, List<String> formParameters, String file) {
        return new BackendRules(file).rules(service, formParameters);
    }

    private List<RuleSpec> rules(ClassTree service, List<String> formParameters) {
        List<MethodTree> full = new ArrayList<>();
        for (Tree member : service.getMembers()) {
            if (member instanceof MethodTree method && method.getName().contentEquals(METHOD)) {
                if (method.getParameters().size() == 4) {
                    full.add(method);
                } else if (method.getParameters().size() == 3) {
                    checkDelegation(method);
                } else {
                    throw unsupported(METHOD + " must have three or four parameters");
                }
            }
        }
        if (full.size() != 1) {
            throw unsupported("expected exactly one four-parameter " + METHOD + " but found " + full.size());
        }
        MethodTree method = full.get(0);
        List<String> parameters = method.getParameters().stream().map(p -> p.getName().toString()).toList();
        if (parameters.size() != formParameters.size()) {
            throw unsupported(METHOD + " has " + parameters.size() + " parameters but the create form has " + formParameters.size());
        }
        Map<String, RuleSpec> byParameter = new LinkedHashMap<>();
        for (StatementTree statement : method.getBody().getStatements()) {
            RuleSpec rule = rule(statement);
            if (!parameters.contains(rule.apiField())) {
                throw unsupported("a rule of " + METHOD + " names " + rule.apiField() + ", which is not a parameter");
            }
            if (byParameter.put(rule.apiField(), rule) != null) {
                throw unsupported(METHOD + " has more than one rule for " + rule.apiField());
            }
        }
        List<RuleSpec> result = new ArrayList<>();
        for (int i = 0; i < parameters.size(); i++) {
            RuleSpec rule = byParameter.get(parameters.get(i));
            if (rule == null) {
                throw unsupported(METHOD + " has no rule for the mapped field " + parameters.get(i));
            }
            result.add(new RuleSpec(formParameters.get(i), rule.required(), rule.kind(), rule.text()));
        }
        return result;
    }

    // The three-parameter overload is accepted only as one call that delegates and appends the empty string.
    private void checkDelegation(MethodTree method) {
        List<? extends StatementTree> statements = method.getBody() == null ? List.of() : method.getBody().getStatements();
        if (statements.size() == 1 && statements.get(0) instanceof ExpressionStatementTree statement
            && statement.getExpression() instanceof MethodInvocationTree call
            && call.getMethodSelect() instanceof IdentifierTree callee && callee.getName().contentEquals(METHOD)
            && call.getArguments().size() == 4 && call.getArguments().get(3) instanceof LiteralTree last
            && last.getKind() == Tree.Kind.STRING_LITERAL && "".equals(last.getValue())) {
            for (int i = 0; i < 3; i++) {
                if (!(call.getArguments().get(i) instanceof IdentifierTree argument)
                    || !argument.getName().contentEquals(method.getParameters().get(i).getName())) {
                    throw unsupported("the three-parameter " + METHOD + " must pass its own parameters in order");
                }
            }
            return;
        }
        throw unsupported("the three-parameter " + METHOD + " must be one delegating call that appends an empty string");
    }

    private RuleSpec rule(StatementTree statement) {
        if (!(statement instanceof IfTree ifTree) || ifTree.getElseStatement() != null) {
            throw unsupported(METHOD + " may contain only 'if (...) throw ...' statements without else");
        }
        StatementTree then = ifTree.getThenStatement();
        if (then instanceof BlockTree block && block.getStatements().size() == 1) {
            then = block.getStatements().get(0);
        }
        if (!(then instanceof ThrowTree thrown) || !(thrown.getExpression() instanceof NewClassTree creation)
            || !(creation.getIdentifier() instanceof IdentifierTree type) || !type.getName().contentEquals("IllegalArgumentException")
            || creation.getArguments().size() != 1 || literal(creation.getArguments().get(0)) == null) {
            throw unsupported("every rule of " + METHOD + " must throw new IllegalArgumentException(\"<literal>\")");
        }
        return condition(strip(ifTree.getCondition()));
    }

    private RuleSpec condition(ExpressionTree condition) {
        if (!(condition instanceof BinaryTree or) || or.getKind() != Tree.Kind.CONDITIONAL_OR) {
            throw shape();
        }
        String parameter = nullCheck(strip(or.getLeftOperand()));
        ExpressionTree right = strip(or.getRightOperand());
        if (call(right, parameter, "isBlank", 0) != null) {
            return new RuleSpec(parameter, true, RuleSpec.Kind.NONE, null);
        }
        if (right instanceof UnaryTree not && not.getKind() == Tree.Kind.LOGICAL_COMPLEMENT) {
            MethodInvocationTree contains = call(strip(not.getExpression()), parameter, "contains", 1);
            String text = contains == null ? null : literal(contains.getArguments().get(0));
            if (text != null && !text.isEmpty()) {
                return new RuleSpec(parameter, true, RuleSpec.Kind.CONTAINS, text);
            }
            throw shape();
        }
        if (right instanceof BinaryTree and && and.getKind() == Tree.Kind.CONDITIONAL_AND) {
            ExpressionTree blank = negated(and.getLeftOperand());
            ExpressionTree matches = negated(and.getRightOperand());
            if (blank != null && call(blank, parameter, "isBlank", 0) != null && matches instanceof MethodInvocationTree call
                && call.getMethodSelect() instanceof MemberSelectTree select
                && select.getIdentifier().contentEquals("matches") && call.getArguments().size() == 1
                && literal(call.getArguments().get(0)) != null && call(strip(select.getExpression()), parameter, "trim", 0) != null) {
                return new RuleSpec(parameter, false, RuleSpec.Kind.REGEX, literal(call.getArguments().get(0)));
            }
        }
        throw shape();
    }

    private ExpressionTree negated(ExpressionTree expression) {
        ExpressionTree stripped = strip(expression);
        if (stripped instanceof UnaryTree not && not.getKind() == Tree.Kind.LOGICAL_COMPLEMENT) {
            return strip(not.getExpression());
        }
        return null;
    }

    private String nullCheck(ExpressionTree expression) {
        if (expression instanceof BinaryTree equal && equal.getKind() == Tree.Kind.EQUAL_TO
            && equal.getLeftOperand() instanceof IdentifierTree name
            && equal.getRightOperand() instanceof LiteralTree nothing && nothing.getKind() == Tree.Kind.NULL_LITERAL) {
            return name.getName().toString();
        }
        throw shape();
    }

    private MethodInvocationTree call(ExpressionTree expression, String parameter, String method, int arguments) {
        if (expression instanceof MethodInvocationTree call && call.getMethodSelect() instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals(method) && select.getExpression() instanceof IdentifierTree target
            && target.getName().contentEquals(parameter) && call.getArguments().size() == arguments) {
            return call;
        }
        return null;
    }

    private String literal(ExpressionTree expression) {
        return expression instanceof LiteralTree literal && literal.getKind() == Tree.Kind.STRING_LITERAL
            ? (String) literal.getValue() : null;
    }

    private ExpressionTree strip(ExpressionTree expression) {
        ExpressionTree current = expression;
        while (current instanceof ParenthesizedTree parenthesized) {
            current = parenthesized.getExpression();
        }
        return current;
    }

    private UnsupportedConstructException shape() {
        return unsupported("a rule of " + METHOD + " has a condition that is not one of the supported idioms "
            + "(null-or-isBlank, null-or-!contains(literal), null-or-(!isBlank && !trim().matches(literal)))");
    }

    private UnsupportedConstructException unsupported(String construct) {
        return new UnsupportedConstructException(file + ": " + construct);
    }
}
