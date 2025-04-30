package plc.project;

import java.util.Arrays;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println",
                List.of(Environment.Type.ANY), Environment.Type.NIL,
                args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        boolean mainPresent = false;

        // Visit all fields && methods.
        for (Ast.Field field : ast.getFields()) {
            visit(field);
        }

        for (Ast.Method method : ast.getMethods()) {
            visit(method);
        }

        for (Ast.Method method : ast.getMethods()) {
            if (method.getName().equals("main") &&
                    method.getReturnTypeName().isPresent() &&
                    method.getReturnTypeName().get().equals("Integer") &&
                    method.getParameters().isEmpty()) {
                mainPresent = true;
            }
        }
        if (!mainPresent) {
            throw new RuntimeException("Invalid or missing main method");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        try {
            if (ast.getValue().isPresent()) {
                visit(ast.getValue().get());

                requireAssignable(Environment.getType(ast.getTypeName()), ast.getValue().get().getType());

                scope.defineVariable(ast.getName(), ast.getName(),
                        Environment.getType(ast.getTypeName()), ast.getConstant(), Environment.NIL);
                ast.setVariable(scope.lookupVariable(ast.getName()));
            } else {
                scope.defineVariable(ast.getName(), ast.getName(),
                        Environment.getType(ast.getTypeName()), ast.getConstant(), Environment.NIL);
                ast.setVariable(scope.lookupVariable(ast.getName()));
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        try {
            Environment.Type returnType = ast.getReturnTypeName().isPresent()
                    ? Environment.getType(ast.getReturnTypeName().get())
                    : Environment.Type.NIL;
            scope.defineVariable("returnType", "returnType", returnType, false, Environment.NIL);

            List<String> paramTypeNames = ast.getParameterTypeNames();
            Environment.Type[] paramTypes = new Environment.Type[paramTypeNames.size()];
            for (int i = 0; i < paramTypeNames.size(); i++) {
                paramTypes[i] = Environment.getType(paramTypeNames.get(i));
            }

            scope.defineFunction(ast.getName(), ast.getName(),
                    Arrays.asList(paramTypes), returnType, args -> Environment.NIL);

            for (Ast.Statement stmt : ast.getStatements()) {
                scope = new Scope(scope);
                try {
                    visit(stmt);
                } finally {
                    scope = scope.getParent();
                }
            }
            ast.setFunction(scope.lookupFunction(ast.getName(), ast.getParameters().size()));
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        visit(ast.getExpression());
        if (!(ast.getExpression() instanceof Ast.Expression.Function)) {
            throw new RuntimeException("Expected function call");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        try {
            if (ast.getValue().isEmpty() && ast.getTypeName().isEmpty()) {
                throw new RuntimeException("Expected type");
            }
            if (ast.getValue().isPresent()) {
                visit(ast.getValue().get());

                scope.defineVariable(ast.getName(), ast.getName(),
                        ast.getValue().get().getType(), false, Environment.NIL);
                ast.setVariable(scope.lookupVariable(ast.getName()));
            } else {

                String typeName = ast.getTypeName().get();
                scope.defineVariable(ast.getName(), ast.getName(),
                        Environment.getType(typeName), false, Environment.NIL);
                ast.setVariable(scope.lookupVariable(ast.getName()));
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        try {
            if (!(ast.getReceiver() instanceof Ast.Expression.Access)) {
                throw new RuntimeException("Expected accessor");
            }
            visit(ast.getValue());
            visit(ast.getReceiver());
            requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        try {
            if (ast.getThenStatements().isEmpty()) {
                throw new RuntimeException("Expected then statement");
            }
            visit(ast.getCondition());
            requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
            for (Ast.Statement stmt : ast.getElseStatements()) {
                scope = new Scope(scope);
                try {
                    visit(stmt);
                } finally {
                    scope = scope.getParent();
                }
            }
            for (Ast.Statement stmt : ast.getThenStatements()) {
                scope = new Scope(scope);
                try {
                    visit(stmt);
                } finally {
                    scope = scope.getParent();
                }
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {
        try {
            visit(ast.getInitialization());
            visit(ast.getCondition());
            requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
            visit(ast.getIncrement());

            for (Ast.Statement stmt : ast.getStatements()) {
                scope = new Scope(scope);
                try {
                    visit(stmt);
                } finally {
                    scope = scope.getParent();
                }
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        try {
            visit(ast.getCondition());
            requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());
            scope = new Scope(scope);
            try {
                for (Ast.Statement stmt : ast.getStatements()) {
                    visit(stmt);
                }
            } finally {
                scope = scope.getParent();
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        try {
            visit(ast.getValue());
            Environment.Variable ret = scope.lookupVariable("returnType");
            requireAssignable(ret.getType(), ast.getValue().getType());
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        try {
            if (ast.getLiteral() instanceof String) {
                ast.setType(Environment.Type.STRING);
            } else if (ast.getLiteral() instanceof Character) {
                ast.setType(Environment.Type.CHARACTER);
            } else if (ast.getLiteral() == Environment.NIL) {
                ast.setType(Environment.Type.NIL);
            } else if (ast.getLiteral() instanceof Boolean) {
                ast.setType(Environment.Type.BOOLEAN);
            } else if (ast.getLiteral() instanceof BigInteger) {
                try {
                    BigInteger temp = (BigInteger) ast.getLiteral();
                    temp.intValueExact();
                    ast.setType(Environment.Type.INTEGER);
                } catch (ArithmeticException e) {
                    throw new RuntimeException("Invalid Integer");
                }
            } else if (ast.getLiteral() instanceof BigDecimal) {
                try {
                    BigDecimal temp = (BigDecimal) ast.getLiteral();
                    if ((temp.doubleValue() > Double.MAX_VALUE) || (temp.doubleValue() < -Double.MAX_VALUE))
                        throw new RuntimeException("Invalid Decimal");
                    ast.setType(Environment.Type.DECIMAL);
                } catch (RuntimeException r) {
                    throw new RuntimeException("Invalid Decimal");
                }
            } else {
                throw new RuntimeException("Invalid Literal Type");
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        visit(ast.getExpression());
        ast.setType(ast.getExpression().getType());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        try {
            String op = ast.getOperator();
            visit(ast.getLeft());
            visit(ast.getRight());
            switch (op) {
                case "AND":
                case "&&":
                case "OR":
                case "||":
                    requireAssignable(Environment.Type.BOOLEAN, ast.getLeft().getType());
                    requireAssignable(Environment.Type.BOOLEAN, ast.getRight().getType());
                    ast.setType(Environment.Type.BOOLEAN);
                    break;
                case "<":
                case "<=":
                case ">":
                case ">=":
                case "==":
                case "!=":
                    requireAssignable(Environment.Type.COMPARABLE, ast.getLeft().getType());
                    requireAssignable(Environment.Type.COMPARABLE, ast.getRight().getType());
                    ast.setType(Environment.Type.BOOLEAN);
                    break;
                case "+":
                    if (ast.getLeft().getType() == Environment.Type.STRING ||
                            ast.getRight().getType() == Environment.Type.STRING) {
                        ast.setType(Environment.Type.STRING);
                    } else if (ast.getLeft().getType() == Environment.Type.INTEGER ||
                            ast.getLeft().getType() == Environment.Type.DECIMAL) {
                        if (ast.getLeft().getType() != ast.getRight().getType()) {
                            throw new RuntimeException("Incorrect types for this operator");
                        }
                        ast.setType(ast.getLeft().getType());
                    } else {
                        throw new RuntimeException("Incorrect types for this operator");
                    }
                    break;
                case "-":
                case "*":
                case "/":
                    if (ast.getLeft().getType() == Environment.Type.INTEGER ||
                            ast.getLeft().getType() == Environment.Type.DECIMAL) {
                        if (ast.getLeft().getType() != ast.getRight().getType()) {
                            throw new RuntimeException("Incorrect types for these operators");
                        }
                        ast.setType(ast.getLeft().getType());
                    } else {
                        throw new RuntimeException("Incorrect types for these operators");
                    }
                    break;
                default:
                    throw new RuntimeException("Incorrect bin operator");
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        try {
            if (ast.getReceiver().isPresent()) {
                Ast.Expression.Access temp = (Ast.Expression.Access) ast.getReceiver().get();
                temp.setVariable(scope.lookupVariable(temp.getName()));
                scope = scope.lookupVariable(temp.getName()).getType().getScope();
                ast.setVariable(scope.lookupVariable(ast.getName()));
                scope = scope.getParent();
            } else {
                ast.setVariable(scope.lookupVariable(ast.getName()));
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        try {
            if (ast.getReceiver().isPresent()) {
                visit(ast.getReceiver().get());
                Ast.Expression.Access temp = (Ast.Expression.Access) ast.getReceiver().get();

                List<Environment.Type> params = scope.lookupVariable(temp.getName())
                        .getType().getFunction(ast.getName(), ast.getArguments().size())
                        .getParameterTypes();
                for (int i = 0; i < ast.getArguments().size(); i++) {
                    visit(ast.getArguments().get(i));
                    requireAssignable(params.get(i + 1), ast.getArguments().get(i).getType());
                }
                ast.setFunction(scope.lookupVariable(temp.getName())
                        .getType().getFunction(ast.getName(), ast.getArguments().size()));
            } else {
                List<Environment.Type> params = scope.lookupFunction(ast.getName(), ast.getArguments().size())
                        .getParameterTypes();
                for (int i = 0; i < ast.getArguments().size(); i++) {
                    visit(ast.getArguments().get(i));
                    requireAssignable(params.get(i), ast.getArguments().get(i).getType());
                }
                ast.setFunction(scope.lookupFunction(ast.getName(), ast.getArguments().size()));
            }
        } catch (RuntimeException r) {
            throw new RuntimeException(r);
        }
        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if (target != type && target != Environment.Type.ANY && target != Environment.Type.COMPARABLE) {
            throw new RuntimeException("Expected type: " + target);
        }
    }
}
