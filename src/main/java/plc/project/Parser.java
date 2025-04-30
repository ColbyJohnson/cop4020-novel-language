package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Parser {
    private final List<Token> tokens;
    private int index = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Ast.Source parseSource() {
        List<Ast.Field> fields = new ArrayList<>();
        while (peekKeyword("LET")) fields.add(parseField());
        List<Ast.Method> methods = new ArrayList<>();
        while (peekKeyword("DEF")) methods.add(parseMethod());
        return new Ast.Source(fields, methods);
    }

    private Ast.Field parseField() {
        expectKeyword("LET");
        boolean constant = matchKeyword("CONST");
        expectType(Token.Type.IDENTIFIER, "Expected field name");
        String name = last().getLiteral();
        Optional<Ast.Expression> value = Optional.empty();
        if (matchOperator("=")) {
            value = Optional.of(parseExpression());
        }
        expectOperator(";", "Expected ';'");
        return new Ast.Field(name, constant, value);
    }

    private Ast.Method parseMethod() {
        expectKeyword("DEF");
        expectType(Token.Type.IDENTIFIER, "Expected method name");
        String name = last().getLiteral();
        expectOperator("(", "Expected '('");
        List<String> params = new ArrayList<>();
        if (!peekOperator(")")) {
            do {
                expectType(Token.Type.IDENTIFIER, "Expected parameter name");
                params.add(last().getLiteral());
            } while (matchOperator(","));
        }
        expectOperator(")", "Expected ')'");
        expectKeyword("DO");
        List<Ast.Statement> stmts = new ArrayList<>();
        while (!peekKeyword("END")) stmts.add(parseStatement());
        expectKeyword("END");
        return new Ast.Method(name, params, stmts);
    }

    public Ast.Statement parseStatement() {
        if (matchKeyword("LET")) {
            expectType(Token.Type.IDENTIFIER, "Expected variable name");
            String name = last().getLiteral();
            Optional<Ast.Expression> init = Optional.empty();
            if (matchOperator("=")) init = Optional.of(parseExpression());
            expectOperator(";", "Expected ';'");
            return new Ast.Statement.Declaration(name, init);
        }
        if (matchKeyword("IF")) {
            Ast.Expression cond = parseExpression();
            expectKeyword("DO");
            List<Ast.Statement> thenStmts = new ArrayList<>();
            while (!peekKeyword("ELSE") && !peekKeyword("END")) thenStmts.add(parseStatement());
            List<Ast.Statement> elseStmts = new ArrayList<>();
            if (matchKeyword("ELSE")) {
                while (!peekKeyword("END")) elseStmts.add(parseStatement());
            }
            expectKeyword("END");
            return new Ast.Statement.If(cond, thenStmts, elseStmts);
        }
        if (matchKeyword("FOR")) {
            expectOperator("(", "Expected '('");
            Ast.Statement init = null;
            if (!peekOperator(";")) init = parseAssignment();
            expectOperator(";", "Expected ';'");
            Ast.Expression cond = parseExpression();
            expectOperator(";", "Expected ';'");
            Ast.Statement incr = null;
            if (!peekOperator(")")) incr = parseAssignment();
            expectOperator(")", "Expected ')'");
            List<Ast.Statement> body = new ArrayList<>();
            while (!peekKeyword("END")) body.add(parseStatement());
            expectKeyword("END");
            return new Ast.Statement.For(init, cond, incr, body);
        }
        if (matchKeyword("WHILE")) {
            Ast.Expression cond = parseExpression();
            expectKeyword("DO");
            List<Ast.Statement> body = new ArrayList<>();
            while (!peekKeyword("END")) body.add(parseStatement());
            expectKeyword("END");
            return new Ast.Statement.While(cond, body);
        }
        if (matchKeyword("RETURN")) {
            Ast.Expression expr = parseExpression();
            expectOperator(";", "Expected ';'");
            return new Ast.Statement.Return(expr);
        }
        Ast.Expression left = parseExpression();
        if (matchOperator("=")) {
            Ast.Expression right = parseExpression();
            expectOperator(";", "Expected ';'");
            return new Ast.Statement.Assignment(left, right);
        }
        expectOperator(";", "Expected ';'");
        return new Ast.Statement.Expression(left);
    }

    private Ast.Statement parseAssignment() {
        Ast.Expression left = parseExpression();
        expectOperator("=", "Expected '='");
        Ast.Expression right = parseExpression();
        return new Ast.Statement.Assignment(left, right);
    }

    public Ast.Expression parseExpression() {
        return parseLogical();
    }

    private Ast.Expression parseLogical() {
        Ast.Expression expr = parseEquality();
        while (matchOperator("AND") || matchOperator("OR") || matchOperator("&&") || matchOperator("||")) {
            String op = last().getLiteral();
            Ast.Expression right = parseEquality();
            expr = new Ast.Expression.Binary(op, expr, right);
        }
        return expr;
    }

    private Ast.Expression parseEquality() {
        Ast.Expression expr = parseAdditive();
        while (matchOperator("==") || matchOperator("!=") || matchOperator("<") || matchOperator("<=") || matchOperator(">") || matchOperator(">=")) {
            String op = last().getLiteral();
            Ast.Expression right = parseAdditive();
            expr = new Ast.Expression.Binary(op, expr, right);
        }
        return expr;
    }

    private Ast.Expression parseAdditive() {
        Ast.Expression expr = parseMultiplicative();
        while (matchOperator("+") || matchOperator("-")) {
            String op = last().getLiteral();
            Ast.Expression right = parseMultiplicative();
            expr = new Ast.Expression.Binary(op, expr, right);
        }
        return expr;
    }

    private Ast.Expression parseMultiplicative() {
        Ast.Expression expr = parseSecondary();
        while (matchOperator("*") || matchOperator("/")) {
            String op = last().getLiteral();
            Ast.Expression right = parseSecondary();
            expr = new Ast.Expression.Binary(op, expr, right);
        }
        return expr;
    }

    private Ast.Expression parseSecondary() {
        Ast.Expression expr = parsePrimary();
        while (matchOperator(".")) {
            expectType(Token.Type.IDENTIFIER, "Expected name");
            String name = last().getLiteral();
            if (matchOperator("(")) {
                List<Ast.Expression> args = new ArrayList<>();
                if (!peekOperator(")")) {
                    args.add(parseExpression());
                    while (matchOperator(",")) args.add(parseExpression());
                }
                expectOperator(")", "Expected ')'");
                expr = new Ast.Expression.Function(Optional.of(expr), name, args);
            } else {
                expr = new Ast.Expression.Access(Optional.of(expr), name);
            }
        }
        return expr;
    }

    private Ast.Expression parsePrimary() {
        if (matchKeyword("TRUE"))   return new Ast.Expression.Literal(true);
        if (matchKeyword("FALSE"))  return new Ast.Expression.Literal(false);
        if (matchKeyword("NIL"))    return new Ast.Expression.Literal(null);
        if (matchType(Token.Type.INTEGER)) {
            return new Ast.Expression.Literal(new BigInteger(last().getLiteral()));
        }
        if (matchType(Token.Type.DECIMAL)) {
            return new Ast.Expression.Literal(new BigDecimal(last().getLiteral()));
        }
        if (matchType(Token.Type.CHARACTER)) {
            String lit = last().getLiteral();
            char c = lit.charAt(1) == '\\' ? lit.charAt(2) : lit.charAt(1);
            return new Ast.Expression.Literal(c);
        }
        if (matchType(Token.Type.STRING)) {
            String s = last().getLiteral();
            s = s.substring(1, s.length()-1)
                    .replace("\\n","\n")
                    .replace("\\r","\r")
                    .replace("\\t","\t")
                    .replace("\\b","\b")
                    .replace("\\\"","\"")
                    .replace("\\'", "'")
                    .replace("\\\\","\\");
            return new Ast.Expression.Literal(s);
        }
        if (matchOperator("(")) {
            Ast.Expression e = parseExpression();
            expectOperator(")", "Expected ')'");
            return new Ast.Expression.Group(e);
        }
        expectType(Token.Type.IDENTIFIER, "Expected identifier or literal");
        String name = last().getLiteral();
        if (matchOperator("(")) {
            List<Ast.Expression> args = new ArrayList<>();
            if (!peekOperator(")")) {
                args.add(parseExpression());
                while (matchOperator(",")) args.add(parseExpression());
            }
            expectOperator(")", "Expected ')'");
            return new Ast.Expression.Function(Optional.empty(), name, args);
        }
        return new Ast.Expression.Access(Optional.empty(), name);
    }

    // Helper methods for token navigation
    private boolean has() { return index < tokens.size(); }
    private Token peekToken() { return tokens.get(index); }
    private Token last() { return tokens.get(index - 1); }
    private boolean peekType(Token.Type type) { return has() && peekToken().getType() == type; }
    private boolean peekOperator(String op) { return peekType(Token.Type.OPERATOR) && peekToken().getLiteral().equals(op); }
    private boolean peekKeyword(String kw) { return peekType(Token.Type.IDENTIFIER) && peekToken().getLiteral().equals(kw); }
    private boolean matchType(Token.Type type) { if (peekType(type)) { index++; return true; } return false; }
    private boolean matchOperator(String op) { if (peekOperator(op)) { index++; return true; } return false; }
    private boolean matchKeyword(String kw) { if (peekKeyword(kw)) { index++; return true; } return false; }
    private Token expect(Token.Type type, String msg) { if (matchType(type)) return last(); error(msg); return null; }
    private Token expectOperator(String op, String msg) { if (matchOperator(op)) return last(); error(msg); return null; }
    private Token expectKeyword(String kw) { if (matchKeyword(kw)) return last(); error("Expected '" + kw + "'"); return null; }
    private void error(String msg) { throw new ParseException(msg, has() ? peekToken().getIndex() : -1); }
}
