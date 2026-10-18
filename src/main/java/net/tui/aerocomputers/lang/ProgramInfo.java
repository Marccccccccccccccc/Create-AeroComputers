package net.tui.aerocomputers.lang;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public record ProgramInfo(
        String[] lines,
        String[] firstWords,                  // first token of each line if it is an identifier, else ""
        Map<Integer, Integer> blockEnd,       // if/while/for/def/class line -> matching end line
        Map<Integer, Integer> loopStart,      // endwhile/endfor line -> matching start line
        Map<String, FunctionInfo> functions,
        Map<String, ClassInfo> classes,
        Set<String> variables
) {

    public record ParamInfo(String name, String defaultExpr) {}   // defaultExpr is null if no default

    public record FunctionInfo(int startLine, int endLine, List<ParamInfo> params) {}

    public record ClassInfo(String name, Map<String, FunctionInfo> methods) {}

    /** class name -> method names */
    public Map<String, List<String>> classMethods() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        classes.forEach((name, info) -> result.put(name, new ArrayList<>(info.methods().keySet())));
        return result;
    }

    /** function / method / class name -> parameter names (without "self") */
    public Map<String, List<String>> callableParams() {
        Map<String, List<String>> result = new LinkedHashMap<>();

        result.putAll(LanguageSpec.BUILTIN_FUNCTIONS);
        result.putAll(LanguageSpec.NATIVE_CLASSES);

        classes.forEach((className, classInfo) ->
                classInfo.methods().forEach((methodName, fn) -> {
                    result.put(methodName, paramNames(fn));
                    if (methodName.equals("__init__")) {
                        result.put(className, paramNames(fn));
                    }
                })
        );

        functions.forEach((name, fn) -> result.put(name, paramNames(fn)));

        return result;
    }

    private static List<String> paramNames(FunctionInfo fn) {
        return fn.params().stream()
                .map(ParamInfo::name)
                .filter(n -> !n.equals("self"))
                .toList();
    }
}