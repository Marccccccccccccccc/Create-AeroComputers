package net.tui.aerocomputers.lang;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans a whole program and extracts block structure, functions, classes and variables.
 * Replaces the interpreter's compileBlocks/registerFunction/registerMethod AND the editor's
 * regex based rescanning, so both always agree.
 *
 * Must stay free of any Minecraft classes.
 */
public final class ProgramAnalyzer {
    private ProgramAnalyzer() {}

    private static final int IF = 0, LOOP = 1, DEF = 2, CLASS = 3;

    private static final class Frame {
        final int line;
        final int kind;
        final String name;                         // function or class name
        final List<ProgramInfo.ParamInfo> params;  // only for DEF

        Frame(int line, int kind, String name, List<ProgramInfo.ParamInfo> params) {
            this.line = line;
            this.kind = kind;
            this.name = name;
            this.params = params;
        }
    }

    public static ProgramInfo analyze(String program) {
        String[] lines = program.split("\n", -1);
        String[] firstWords = new String[lines.length];

        Map<Integer, Integer> blockEnd = new HashMap<>();
        Map<Integer, Integer> loopStart = new HashMap<>();
        Map<String, ProgramInfo.FunctionInfo> functions = new LinkedHashMap<>();
        Map<String, ProgramInfo.ClassInfo> classes = new LinkedHashMap<>();
        Set<String> variables = new LinkedHashSet<>();
        Deque<Frame> stack = new ArrayDeque<>();

        for (int i = 0; i < lines.length; i++) {
            List<Token> toks = Lexer.significant(Lexer.lex(lines[i]));

            String first = (!toks.isEmpty() && toks.get(0).type() == TokenType.IDENT)
                    ? toks.get(0).text()
                    : "";
            firstWords[i] = first;

            if (first.isEmpty()) {
                continue;
            }

            switch (first) {
                case "if" -> stack.push(new Frame(i, IF, null, null));

                case "while" -> stack.push(new Frame(i, LOOP, null, null));

                case "for" -> {
                    stack.push(new Frame(i, LOOP, null, null));
                    if (toks.size() > 1 && toks.get(1).type() == TokenType.IDENT) {
                        variables.add(toks.get(1).text());
                    }
                }

                case "class" -> {
                    String name = (toks.size() > 1 && toks.get(1).type() == TokenType.IDENT)
                            ? toks.get(1).text()
                            : "";
                    stack.push(new Frame(i, CLASS, name, null));

                    if (!name.isEmpty()) {
                        classes.computeIfAbsent(name,
                                k -> new ProgramInfo.ClassInfo(k, new LinkedHashMap<>()));
                    }
                }

                case "def" -> {
                    String name = (toks.size() > 1 && toks.get(1).type() == TokenType.IDENT)
                            ? toks.get(1).text()
                            : "";
                    List<ProgramInfo.ParamInfo> params = parseParams(lines[i], toks);

                    for (ProgramInfo.ParamInfo p : params) {
                        if (!p.name().equals("self")) {
                            variables.add(p.name());
                        }
                    }

                    stack.push(new Frame(i, DEF, name, params));
                }

                case "endif", "endwhile", "endfor", "enddef", "endclass" -> {
                    if (stack.isEmpty()) {
                        break;
                    }

                    Frame f = stack.pop();
                    blockEnd.put(f.line, i);

                    if (f.kind == LOOP) {
                        loopStart.put(i, f.line);
                    }

                    if (f.kind == DEF && !f.name.isEmpty()) {
                        ProgramInfo.FunctionInfo fn =
                                new ProgramInfo.FunctionInfo(f.line, i, f.params);

                        Frame parent = stack.peek();
                        if (parent != null && parent.kind == CLASS && !parent.name.isEmpty()) {
                            classes.get(parent.name).methods().put(f.name, fn);
                        } else {
                            functions.put(f.name, fn);
                        }
                    }
                }

                default -> collectAssignedVariables(toks, variables);
            }
        }

        return new ProgramInfo(lines, firstWords, blockEnd, loopStart, functions, classes, variables);
    }

    /**
     * Matches "a = ..." and "a, b, c = ...".
     * Does NOT match dummy(x=1), a.b = 1 or a[0] = 1.
     */
    private static void collectAssignedVariables(List<Token> toks, Set<String> out) {
        int k = 0;

        while (k + 1 < toks.size()
                && toks.get(k).type() == TokenType.IDENT
                && toks.get(k + 1).text().equals(",")) {
            k += 2;
        }

        if (k + 1 < toks.size()
                && toks.get(k).type() == TokenType.IDENT
                && toks.get(k + 1).text().equals("=")) {

            for (int j = 0; j <= k; j += 2) {
                out.add(toks.get(j).text());
            }
        }
    }

    /** Parses "def name(a, b=2, ...)" into parameter definitions. */
    private static List<ProgramInfo.ParamInfo> parseParams(String line, List<Token> toks) {
        int open = 2; // def NAME (
        if (toks.size() <= open || !toks.get(open).text().equals("(")) {
            return List.of();
        }

        List<ProgramInfo.ParamInfo> result = new ArrayList<>();
        int depth = 0;
        int segmentStart = open + 1;

        for (int i = open + 1; i < toks.size(); i++) {
            String s = toks.get(i).text();

            if (depth == 0 && (s.equals(",") || s.equals(")"))) {
                addParam(line, toks, segmentStart, i, result);
                segmentStart = i + 1;

                if (s.equals(")")) {
                    break;
                }
            } else if (s.equals("(") || s.equals("[")) {
                depth++;
            } else if (s.equals(")") || s.equals("]")) {
                depth--;
            }
        }

        return result;
    }

    private static void addParam(String line, List<Token> toks, int from, int to,
                                 List<ProgramInfo.ParamInfo> out) {
        if (from >= to || toks.get(from).type() != TokenType.IDENT) {
            return;
        }

        String name = toks.get(from).text();
        String defaultExpr = null;

        if (to - from >= 3 && toks.get(from + 1).text().equals("=")) {
            defaultExpr = line.substring(toks.get(from + 2).start(), toks.get(to - 1).end());
        }

        out.add(new ProgramInfo.ParamInfo(name, defaultExpr));
    }
}