// Lexer.java
package plc.project;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java lexer with no ANTLR dependency.
 * Tokenizes input according to the grammar.
 */
public final class Lexer {
    private final String input;
    private final List<Token> tokens = new ArrayList<>();
    private int index = 0;
    private int start = 0;

    public Lexer(String input) {
        this.input = input;
    }

    public List<Token> lex() {
        while (hasNext()) {
            start = index;
            char c = peek();
            if (isWhitespace(c)) {
                advance();
            } else if (isIdentifierStart(c)) {
                lexIdentifier();
            } else if (isDigit(c) || ((c == '+' || c == '-') && lookAheadIsDigit())) {
                lexNumber();
            } else if (c == '\'') {
                lexCharacter();
            } else if (c == '"') {
                lexString();
            } else {
                lexOperator();
            }
        }
        return tokens;
    }

    public Token lexToken() {
        start = index;
        Token token;
        char c = peek();
        if (isIdentifierStart(c)) {
            token = lexIdentifier();
        } else if (isDigit(c) || ((c == '+' || c == '-') && lookAheadIsDigit())) {
            token = lexNumber();
        } else if (c == '\'') {
            token = lexCharacter();
        } else if (c == '"') {
            token = lexString();
        } else {
            token = lexOperator();
        }
        return token;
    }

    private Token lexIdentifier() {
        advance();
        while (hasNext() && isIdentifierPart(peek())) advance();
        String lit = input.substring(start, index);
        Token token = new Token(Token.Type.IDENTIFIER, lit, start);
        tokens.add(token);
        return token;
    }

    private Token lexNumber() {
        boolean isDecimal = false;
        if (peek() == '+' || peek() == '-') advance();
        if (peek() == '0') {
            advance();
        } else {
            if (!isDigit(peek())) error("Invalid number");
            while (hasNext() && isDigit(peek())) advance();
        }
        if (hasNext(2) && peek(1) == '.' && isDigit(peek(2))) {
            isDecimal = true;
            advance(); // '.'
            while (hasNext() && isDigit(peek())) advance();
        }
        Token.Type type = isDecimal ? Token.Type.DECIMAL : Token.Type.INTEGER;
        Token token = new Token(type, input.substring(start, index), start);
        tokens.add(token);
        return token;
    }

    private Token lexCharacter() {
        advance(); // opening '
        if (!hasNext()) error("Unterminated character literal");
        if (peek() == '\\') {
            advance();
            if (!hasNext()) error("Unterminated escape sequence");
            advance();
        } else {
            advance();
        }
        if (!hasNext() || peek() != '\'') error("Unterminated character literal");
        advance(); // closing '
        Token token = new Token(Token.Type.CHARACTER, input.substring(start, index), start);
        tokens.add(token);
        return token;
    }

    private Token lexString() {
        advance(); // opening "
        while (hasNext() && peek() != '"') {
            if (peek() == '\\') {
                advance();
                if (!hasNext()) error("Invalid escape sequence");
                advance();
            } else {
                advance();
            }
        }
        if (!hasNext() || peek() != '"') error("Unterminated string literal");
        advance(); // closing "
        Token token = new Token(Token.Type.STRING, input.substring(start, index), start);
        tokens.add(token);
        return token;
    }

    private Token lexOperator() {
        if (hasNext(2)) {
            String two = input.substring(index, index+2);
            if (two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=") || two.equals("&&") || two.equals("||")) {
                index += 2;
                Token token = new Token(Token.Type.OPERATOR, two, start);
                tokens.add(token);
                return token;
            }
        }
        String one = String.valueOf(peek());
        advance();
        Token token = new Token(Token.Type.OPERATOR, one, start);
        tokens.add(token);
        return token;
    }

    private boolean hasNext() {
        return index < input.length();
    }
    private boolean hasNext(int ahead) {
        return index + ahead <= input.length();
    }
    private char peek() {
        return input.charAt(index);
    }
    private char peek(int ahead) {
        return input.charAt(index + ahead - 1);
    }
    private void advance() { index++; }
    private boolean isWhitespace(char c) { return " \t\n\r".indexOf(c) >= 0; }
    private boolean isLetter(char c) { return Character.isLetter(c); }
    private boolean isDigit(char c) { return Character.isDigit(c); }
    private boolean isIdentifierStart(char c) { return isLetter(c) || c == '_'; }
    private boolean isIdentifierPart(char c) { return isLetter(c) || isDigit(c) || c == '_' || c == '-'; }
    private boolean lookAheadIsDigit() { return hasNext(2) && isDigit(peek(2)); }
    private void error(String msg) { throw new ParseException(msg, index); }
}
