// new file: net/tui/aerocomputers/client/ClientHudRegistry.java
package net.tui.aerocomputers.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientHudRegistry {
    public record DrawEntry(String type, double x, double y, double x2, double y2,
                            double w, double h, String text, double fontSize,
                            double width, double borderWidth, boolean renderCenter, boolean renderBorder,
                            List<Integer> color, List<Integer> centerColor, List<Integer> borderColor,
                            List<Double> xs, List<Double> ys) {}

    private static final Map<String, List<DrawEntry>> BY_NAME = new ConcurrentHashMap<>();

    public static void update(String name, List<DrawEntry> entries) {
        if (name == null || name.isBlank()) {
            return;
        }
        BY_NAME.put(name, List.copyOf(entries));
    }

    public static List<DrawEntry> get(String name) {
        List<DrawEntry> result = BY_NAME.get(name);
        return result != null ? result : List.of();
    }
}