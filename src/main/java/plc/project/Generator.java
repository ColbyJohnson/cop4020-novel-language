package plc.project;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;
import java.util.List;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {");
        indent++;
        newline(0);

        if (!ast.getFields().isEmpty()) {
            for (Ast.Field field : ast.getFields()) {
                newline(indent);
                print(field);
            }
            newline(0);
        }

        newline(indent);
        print("public static void main(String[] args) {");
        indent++;
        newline(indent);
        print("System.exit(new Main().main());");
        indent--;
        newline(indent);
        print("}");
        newline(0);

        for (Ast.Method method : ast.getMethods()) {
            newline(indent);
            print(method);
        }
        newline(0);

        indent--;
        newline(indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        switch (ast.getTypeName()) {
            case "Integer": print("int"); break;
            case "Decimal": print("double"); break;
            case "Boolean": print("boolean"); break;
            case "Character": print("char"); break;
            case "String": print("String"); break;
            default: print(ast.getTypeName()); break;
        }
        print(" ", ast.getName());
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        print(ast.getFunction().getReturnType().getJvmName(), " ");
        print(ast.getName(), "(");
        List<String> params = ast.getParameters();
        for (int i = 0; i < params.size(); i++) {
            print(ast.getParameterTypeNames().get(i), " ", params.get(i));
            if (i != params.size() - 1) {
                print(", ");
            }
        }
        print(") {");
        if (!ast.getStatements().isEmpty()) {
            indent++;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                print(stmt);
            }
            indent--;
            newline(indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (", ast.getCondition(), ") {");
        indent++;
        for (Ast.Statement stmt : ast.getThenStatements()) {
            newline(indent);
            print(stmt);
        }
        indent--;
        newline(indent);
        print("}");
        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            indent++;
            for (Ast.Statement stmt : ast.getElseStatements()) {
                newline(indent);
                print(stmt);
            }
            indent--;
            newline(indent);
            print("}");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {
        Ast.Statement init = ast.getInitialization();
        Ast.Expression condition = ast.getCondition();
        Ast.Statement inc = ast.getIncrement();

        // Construct for-loop signature
        print("for ( ");
        if (init != null) {
            if (init instanceof Ast.Statement.Declaration) {
                Ast.Statement.Declaration decl = (Ast.Statement.Declaration) init;
                print(decl.getVariable().getJvmName(), " = ", decl.getValue().get());
            } else if (init instanceof Ast.Statement.Assignment) {
                Ast.Statement.Assignment assign = (Ast.Statement.Assignment) init;
                print(assign.getReceiver(), " = ", assign.getValue());
            } else if (init instanceof Ast.Statement.Expression) {
                Ast.Statement.Expression exprStmt = (Ast.Statement.Expression) init;
                print(exprStmt.getExpression());
            }
        }
        print("; ", condition, ";");
        if (inc != null) {
            print(" ");
            if (inc instanceof Ast.Statement.Declaration) {
                Ast.Statement.Declaration decl = (Ast.Statement.Declaration) inc;
                print(decl.getVariable().getJvmName(), " = ", decl.getValue().get());
            } else if (inc instanceof Ast.Statement.Assignment) {
                Ast.Statement.Assignment assign = (Ast.Statement.Assignment) inc;
                print(assign.getReceiver(), " = ", assign.getValue());
            } else if (inc instanceof Ast.Statement.Expression) {
                Ast.Statement.Expression exprStmt = (Ast.Statement.Expression) inc;
                print(exprStmt.getExpression());
            }
        }
        print(" ) {");
        if (!ast.getStatements().isEmpty()) {
            indent++;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                print(stmt);
            }
            indent--;
            newline(indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (", ast.getCondition(), ") {");
        if (!ast.getStatements().isEmpty()) {
            indent++;
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent);
                print(stmt);
            }
            indent--;
            newline(indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        if (ast.getType() == Environment.Type.CHARACTER) {
            print("'", ast.getLiteral(), "'");
        } else if (ast.getType() == Environment.Type.STRING) {
            print("\"", ast.getLiteral(), "\"");
        } else if (ast.getType() == Environment.Type.DECIMAL) {
            BigDecimal temp = BigDecimal.class.cast(ast.getLiteral());
            print(temp.doubleValue());
        } else if (ast.getType() == Environment.Type.INTEGER) {
            BigInteger temp = BigInteger.class.cast(ast.getLiteral());
            print(temp.intValue());
        } else {
            print(ast.getLiteral());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        print(ast.getLeft(), " ");
        if ("AND".equals(ast.getOperator())) {
            print("&&");
        } else if ("OR".equals(ast.getOperator())) {
            print("||");
        } else {
            print(ast.getOperator());
        }
        print(" ", ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        if (ast.getReceiver().isPresent()) {
            print(ast.getReceiver().get(), ".");
        }
        print(ast.getVariable().getJvmName());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        if (ast.getReceiver().isPresent()) {
            print(ast.getReceiver().get(), ".");
        }
        print(ast.getFunction().getJvmName(), "(");
        List<Ast.Expression> args = ast.getArguments();
        for (int i = 0; i < args.size(); i++) {
            print(args.get(i));
            if (i != args.size() - 1) {
                print(", ");
            }
        }
        print(")");
        return null;
    }
}