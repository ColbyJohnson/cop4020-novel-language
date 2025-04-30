// Interpreter.java
package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Walks the AST, carrying an Environment.Scope of PlcObjects, and
 * evaluates expressions/statements. Always returns a PlcObject.
 */
public final class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope;

    public Interpreter(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        // run all fields
        for (Ast.Field f : ast.getFields()) visit(f);
        // invoke main
        Environment.Function main = scope.lookupFunction("main", 0);
        return main.invoke(List.of());
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        Environment.PlcObject val = Environment.NIL;
        if (ast.getValue().isPresent()) {
            val = visit(ast.getValue().get());
        }
        Environment.Variable v = scope.defineVariable(
                ast.getName(), ast.getName(), Environment.Type.ANY, ast.getConstant(), val
        );
        ast.setVariable(v);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {
        // define method in this scope
        Environment.Function fn = scope.defineFunction(
                ast.getName(),
                ast.getName(),
                ast.getParameters().size(),
                args -> {
                    // each invocation gets a new local scope
                    Scope old = scope;
                    scope = new Scope(old);
                    for (int i = 0; i < ast.getParameters().size(); i++) {
                        scope.defineVariable(
                                ast.getParameters().get(i),
                                ast.getParameters().get(i),
                                Environment.Type.ANY,
                                false,
                                args.get(i)
                        );
                    }
                    Environment.PlcObject ret = Environment.NIL;
                    for (var stmt : ast.getStatements()) {
                        ret = visit(stmt);
                        if (stmt instanceof Ast.Statement.Return) break;
                    }
                    scope = old;
                    return ret;
                }
        );
        ast.setFunction(fn);
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        Environment.PlcObject val = visit(ast.getExpression());
        return val;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        Environment.PlcObject val = Environment.NIL;
        if (ast.getValue().isPresent()) {
            val = visit(ast.getValue().get());
        }
        Environment.Variable v = scope.defineVariable(
                ast.getName(), ast.getName(), Environment.Type.ANY, false, val
        );
        ast.setVariable(v);
        return val;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        Environment.PlcObject val = visit(ast.getValue());
        Environment.PlcObject recv = visit(ast.getReceiver());
        ast.getReceiver().getVariable().setValue(val);
        return val;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        boolean cond = (Boolean) visit(ast.getCondition()).getValue();
        if (cond) {
            for (var s : ast.getThenStatements()) visit(s);
        } else {
            for (var s : ast.getElseStatements()) visit(s);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.For ast) {
        if (ast.getInitialization()!=null) visit(ast.getInitialization());
        while ((Boolean) visit(ast.getCondition()).getValue()) {
            for (var s : ast.getStatements()) visit(s);
            if (ast.getIncrement()!=null) visit(ast.getIncrement());
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {
        while ((Boolean) visit(ast.getCondition()).getValue()) {
            for (var s : ast.getStatements()) visit(s);
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {
        return visit(ast.getValue());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        Object lit = ast.getLiteral();
        if (lit == null) {
            return Environment.NIL;
        } else {
            return Environment.create(lit);
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        String op = ast.getOperator();
        Object l = visit(ast.getLeft()).getValue();
        if ((op.equals("OR")||op.equals("&&")||op.equals("||")) && (Boolean)l && (op.contains("OR")||op.equals("||"))) {
            return Environment.create(true);
        }
        Object r = visit(ast.getRight()).getValue();
        return switch (op) {
            case "AND","&&"           -> Environment.create((Boolean)l && (Boolean)r);
            case "OR","||"            -> Environment.create((Boolean)l || (Boolean)r);
            case "<"                  -> Environment.create(((Comparable)l).compareTo(r) < 0);
            case "<="                 -> Environment.create(((Comparable)l).compareTo(r) <= 0);
            case ">"                  -> Environment.create(((Comparable)l).compareTo(r) > 0);
            case ">="                 -> Environment.create(((Comparable)l).compareTo(r) >= 0);
            case "=="                 -> Environment.create(l.equals(r));
            case "!="                 -> Environment.create(!l.equals(r));
            case "+"                  -> {
                if (l instanceof String || r instanceof String) yield Environment.create(l.toString() + r);
                if (l instanceof BigDecimal || r instanceof BigDecimal) yield Environment.create(
                        ((BigDecimal)l).add( (r instanceof BigDecimal ? (BigDecimal)r : new BigDecimal(r.toString())) )
                );
                yield Environment.create(((BigInteger)l).add((BigInteger)r));
            }
            case "-"                  -> {
                if (l instanceof BigDecimal) yield Environment.create(((BigDecimal)l).subtract((BigDecimal)r));
                yield Environment.create(((BigInteger)l).subtract((BigInteger)r));
            }
            case "*"                  -> {
                if (l instanceof BigDecimal) yield Environment.create(((BigDecimal)l).multiply((BigDecimal)r));
                yield Environment.create(((BigInteger)l).multiply((BigInteger)r));
            }
            case "/"                  -> {
                if (l instanceof BigDecimal) yield Environment.create(((BigDecimal)l).divide((BigDecimal)r));
                yield Environment.create(((BigInteger)l).divide((BigInteger)r));
            }
            default                   -> throw new RuntimeException("Unknown operator " + op);
        };
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {
        Environment.PlcObject obj = ast.getReceiver()
                .map(this::visit)
                .orElse(new Environment.PlcObject(scope, null));
        // top‐level var
        if (ast.getReceiver().isEmpty()) {
            Environment.Variable v = scope.lookupVariable(ast.getName());
            ast.setVariable(v);
            return v.getValue();
        } else {
            // field or method call on object
            Environment.Variable v = obj.getField(ast.getName());
            ast.setVariable(v);
            return v.getValue();
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        Environment.PlcObject target = ast.getReceiver()
                .map(this::visit)
                .orElse(null);
        List<Environment.PlcObject> args = ast.getArguments().stream()
                .map(this::visit).toList();
        Environment.Function fn = target==null
                ? scope.lookupFunction(ast.getName(), args.size())
                : target.callMethod(ast.getName(), args);
        ast.setFunction(fn);
        return fn.invoke(target==null ? args : args);
    }
}
