package net.tui.aerocomputers.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the board computer language.
 * Used by the interpreter AND the editor (highlighting + autocomplete).
 */
public final class LanguageSpec {
    private LanguageSpec() {}

    public static final Set<String> CONTROL_KEYWORDS = Set.of(
            "if", "endif",
            "while", "endwhile",
            "for", "endfor",
            "return"
    );

    public static final Set<String> DECLARATION_KEYWORDS = Set.of(
            "def", "enddef",
            "class", "endclass",
            "and", "or", "not", "in",
            "true", "false"
    );

    /** builtin function name -> parameter names (in positional order) */
    public static final Map<String, List<String>> BUILTIN_FUNCTIONS = Map.ofEntries(
            Map.entry("print", List.of("text")),
            Map.entry("sqrt", List.of("value")),
            Map.entry("sin", List.of("degrees")),
            Map.entry("cos", List.of("degrees")),
            Map.entry("asin", List.of("value")),
            Map.entry("acos", List.of("value")),
            Map.entry("abs", List.of("value")),
            Map.entry("round", List.of("value")),
            Map.entry("ceil", List.of("value")),
            Map.entry("floor", List.of("value")),
            Map.entry("min", List.of("a", "b")),
            Map.entry("max", List.of("a", "b")),
            Map.entry("getPos", List.of()),
            Map.entry("getRot", List.of()),
            Map.entry("getVel", List.of())
    );

    /** native class name -> constructor parameter names (in positional order) */
    public static final Map<String, List<String>> NATIVE_CLASSES = Map.of(
            "Label", List.of("x", "y", "text", "color", "font_size"),
            "Line", List.of("x1", "y1", "x2", "y2", "width", "color"),
            "Rect", List.of("x", "y", "w", "h", "border_width", "center_color", "border_color", "render_center", "render_border"),
            "Poly", List.of("xs", "ys", "border_width", "border_color")
    );

    // Sorted lists so autocomplete order is stable (Set.of / Map.of iteration order is not).
    public static final List<String> BUILTIN_FUNCTION_NAMES = sorted(BUILTIN_FUNCTIONS.keySet());
    public static final List<String> NATIVE_CLASS_NAMES = sorted(NATIVE_CLASSES.keySet());
    public static final List<String> KEYWORD_NAMES = sortedUnion(CONTROL_KEYWORDS, DECLARATION_KEYWORDS);

    private static List<String> sorted(Collection<String> c) {
        List<String> l = new ArrayList<>(c);
        Collections.sort(l);
        return List.copyOf(l);
    }

    private static List<String> sortedUnion(Collection<String> a, Collection<String> b) {
        List<String> l = new ArrayList<>(a);
        l.addAll(b);
        Collections.sort(l);
        return List.copyOf(l);
    }
}