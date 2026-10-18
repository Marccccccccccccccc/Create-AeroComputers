package net.tui.aerocomputers.lang;

import java.util.ArrayList;
import java.util.List;

/**
 * Lossless lexer shared by the interpreter (server) and the code editor (client).
 * Every character of the input ends up in exactly one token, so concatenating all
 * token texts gives back the original string.
 *
 * Must stay free of any Minecraft classes.
 */
public final class Lexer {
    private Lexer() {}

    public static List<Token> lex(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        int n = s.length();

        while (i < n) {
            int start = i;
            char c = s.charAt(i);
            TokenType type;

            if (Character.isWhitespace(c)) {
                while (i < n && Character.isWhitespace(s.charAt(i))) i++;
                type = TokenType.WHITESPACE;

            } else if (c == '#') {
                i = n;
                type = TokenType.COMMENT;

            } else if (c == '"') {
                i++;
                while (i < n && s.charAt(i) != '"') i++; // no escapes, same as the interpreter
                if (i < n) i++;                            // closing quote (may be missing)
                type = TokenType.STRING;

            } else if (Character.isDigit(c)
                    || (c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1)))) {
                while (i < n && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                type = TokenType.NUMBER;

            } else if (Character.isLetter(c) || c == '_') {
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                type = TokenType.IDENT;

            } else if (c == '*' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                type = TokenType.OPERATOR;

            } else if ("<>=!".indexOf(c) != -1 && i + 1 < n && s.charAt(i + 1) == '=') {
                i += 2;
                type = TokenType.OPERATOR;

            } else if ("()[]{}".indexOf(c) != -1) {
                i++;
                type = TokenType.BRACKET;

            } else if ("=+-*/<>,%.".indexOf(c) != -1) {
                i++;
                type = TokenType.OPERATOR;

            } else {
                i++;
                type = TokenType.UNKNOWN;
            }

            out.add(new Token(type, s.substring(start, i), start, i));
        }

        return out;
    }

    /** Same tokens without whitespace and comments. */
    public static List<Token> significant(List<Token> all) {
        List<Token> result = new ArrayList<>();
        for (Token t : all) {
            if (t.type() != TokenType.WHITESPACE && t.type() != TokenType.COMMENT) {
                result.add(t);
            }
        }
        return result;
    }
}