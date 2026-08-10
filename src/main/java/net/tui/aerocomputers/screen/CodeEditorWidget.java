package net.tui.aerocomputers.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.tui.aerocomputers.AeroComputers;
import net.tui.aerocomputers.block.entity.BoardComputerBlockEntity;

import java.util.*;

import static net.minecraft.client.gui.screens.Screen.hasControlDown;
import static net.minecraft.client.gui.screens.Screen.hasShiftDown;

public class CodeEditorWidget extends AbstractWidget {

    private final List<String> lines = new ArrayList<>();

    private int cursorLine = 0;
    private int cursorColumn = 0;
    private int selectionLine = -1;
    private int selectionColumn = -1;

    private int timer;
    private final int timer_max = 120;
    private boolean draw_cursor = false;

    private final int lineNumberWidth = 30;

    private int scrollX = 0;
    private int scrollY = 0;

    private final int lineHeight = 10;

    private static final String INDENT = "   "; // 3 spaces, matches existing convention
    private boolean isDragging = false;

    private static final int COLOR_NORMAL = 0xFFFFFFFF;
    private static final int COLOR_CONTROL_KEYWORD = 0xFFC586C0; // purple
    private static final int COLOR_DECLARATION_KEYWORD = 0xFF317CD6; // blue
    private static final int COLOR_FUNCTION = 0xFFDCDC8B;
    private static final int COLOR_CLASS = 0xFF4EBB7D;
    private static final int COLOR_VARIABLE = 0xFF9CDCFE;
    private static final int COLOR_STRING = 0xFFC39178;
    private static final int COLOR_NUMBER = 0xFFB5C078;
    private static final int COLOR_BRACKET = 0xFFF1D70B;
    private static final int COLOR_COMMENT = 0xFF6A9955;

    private static final Set<String> CONTROL_KEYWORDS = Set.of(
            "if",
            "endif",
            "while",
            "endwhile",
            "for",
            "endfor"
    );

    private static final Set<String> DECLARATION_KEYWORDS = Set.of(
            "def",
            "enddef",
            "class",
            "endclass",
            "and",
            "or",
            "true",
            "false"
    );

    private static final Set<String> BUILTIN_FUNCTIONS = Set.of(
            "print",
            "getPos",
            "getRot",
            "getVel",
            "sqrt",
            "sin",
            "cos",
            "asin",
            "acos",
            "abs",
            "min",
            "max",
            "round",
            "ceil",
            "floor"
    );

    private static final Set<String> BUILTIN_CLASSES = Set.of(
            "Label",
            "Line",
            "Rect",
            "Poly"
    );

    private boolean autocompleteOpen = false;
    private final List<String> autocompleteSuggestions = new ArrayList<>();
    private int autocompleteIndex = 0;

    private String autocompletePrefix = "";
    private int autocompleteStartColumn = 0;

    private static final int AUTOCOMPLETE_MAX_RESULTS = 8;
    private static final int AUTOCOMPLETE_WIDTH = 140;
    private static final int AUTOCOMPLETE_ITEM_HEIGHT = 12;

    private List<String> variables;
    private List<String> functions;
    private List<String> classes;
    private List<String> builtins;
    private List<String> nativeClasses;
    private Map<String, List<String>> classMethods;
    private Map<String, List<String>> callableParams;

    // Server-provided baseline, refreshed only via updateCompletions(...) from the real block entity state
    private List<String> serverVariables = List.of();
    private List<String> serverFunctions = List.of();
    private List<String> serverClasses = List.of();
    private Map<String, List<String>> serverClassMethods = Map.of();
    private Map<String, List<String>> serverCallableParams = Map.of();

    private enum AutocompleteMode { NORMAL, METHOD, PARAM }
    private AutocompleteMode autocompleteMode = AutocompleteMode.NORMAL;

    private static final Map<String, List<String>> BUILTIN_FUNCTION_PARAMS = Map.ofEntries(
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

    private static final Map<String, List<String>> BUILTIN_CLASS_PARAMS = Map.of(
            "Label", List.of("x", "y", "text", "color", "font_size"),
            "Line", List.of("x1", "y1", "x2", "y2", "width", "color"),
            "Rect", List.of("x", "y", "width", "height", "border_width", "center_color", "border_color", "render_center", "render_border"),
            "Poly", List.of("xs", "ys", "width", "color")
    );

    public CodeEditorWidget(int x, int y, int width, int height, String text,
                            BoardComputerBlockEntity.EditorCompletions completions) {
        super(x, y, width, height, Component.empty());
        lines.addAll(List.of(text.split("\n", -1)));

        this.serverVariables = completions.variables();
        this.serverFunctions = completions.functions();
        this.serverClasses = completions.classes();
        this.serverClassMethods = completions.classMethods();
        this.serverCallableParams = completions.callableParams();

        this.builtins = completions.builtins();
        this.nativeClasses = completions.nativeClasses();

        rescanLocalCompletions();
    }

    public void updateCompletions(BoardComputerBlockEntity.EditorCompletions completions) {
        this.serverVariables = completions.variables();
        this.serverFunctions = completions.functions();
        this.serverClasses = completions.classes();
        this.serverClassMethods = completions.classMethods();
        this.serverCallableParams = completions.callableParams();

        this.builtins = completions.builtins();
        this.nativeClasses = completions.nativeClasses();

        rescanLocalCompletions();
        updateAutocomplete();
    }


    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        Minecraft mc = Minecraft.getInstance();

        // Background
        guiGraphics.fill(
                getX(),
                getY(),
                getX() + width,
                getY() + height,
                0xFF101010
        );
        

        guiGraphics.fill(
                getX(),
                getY(),
                getX() + lineNumberWidth,
                getY() + height,
                0xFF181818
        );
        

        guiGraphics.enableScissor(
                getX(),  // left
                getY(),                    // top
                getX() + width,            // right
                getY() + height            // bottom
        );

        //Draw numbers
        for (int i = 0; i < lines.size(); i++) {

            String number = String.valueOf(i + 1);

            guiGraphics.drawString(
                    mc.font,
                    number,
                    getX() + lineNumberWidth - mc.font.width(number) - 5,
                    getY() + 5 + i * lineHeight - scrollY,
                    0xFF888888
            );
        }

        guiGraphics.disableScissor();
        



        guiGraphics.enableScissor(
                getX() + lineNumberWidth,  // left
                getY(),                    // top
                getX() + width,            // right
                getY() + height            // bottom
        );

        // Draw selection
        if (hasSelection()) {

            int[] sel = normalizedSelection();
            int startLine = sel[0], startColumn = sel[1], endLine = sel[2], endColumn = sel[3];

            for (int line = startLine; line <= endLine; line++) {

                String text = lines.get(line);

                int from = 0;
                int to = text.length();


                if (line == startLine) {
                    from = startColumn;
                }

                if (line == endLine) {
                    to = endColumn;
                }


                int selectionX =
                        getX()
                                + lineNumberWidth
                                + 5
                                + mc.font.width(text.substring(0, from));


                int selectionWidth =
                        mc.font.width(text.substring(from, to));


                int selectionY =
                        getY()
                                + 5
                                + line * lineHeight;


                guiGraphics.fill(
                        selectionX - scrollX,
                        selectionY - scrollY,
                        selectionX + selectionWidth - scrollX,
                        selectionY + lineHeight - scrollY,
                        0xFF3366AA
                );
                
            }
        }


        // Draw text
        // Draw syntax-highlighted text
        
        for (int i = 0; i < lines.size(); i++) {

            drawHighlightedLine(
                    guiGraphics,
                    mc,
                    lines.get(i),
                    getX() + lineNumberWidth + 5 - scrollX,
                    getY() + 5 + i * lineHeight - scrollY
            );
        }

        guiGraphics.disableScissor();
        

        // Draw cursor
        if (isFocused()) {
            if (timer == timer_max) {
                timer = 0;
                draw_cursor = !draw_cursor;
            } else {
                timer += 1;
            }

            if (draw_cursor) {
                String currentLine = lines.get(cursorLine);
                int safeColumn = Math.min(cursorColumn, currentLine.length());

                int cursorX = getX() + lineNumberWidth + 5 + mc.font.width(currentLine.substring(0, safeColumn));
                int cursorY = getY() + 5 + cursorLine * lineHeight;

                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 200);

                guiGraphics.fill(
                        cursorX - scrollX,
                        cursorY - scrollY,
                        cursorX + 1 - scrollX,
                        cursorY + 8 - scrollY,
                        0xFFFFFFFF
                );

                guiGraphics.pose().popPose();
            }
        }

// =========================================================
// AUTOCOMPLETE POPUP
// =========================================================

        if (autocompleteOpen && !autocompleteSuggestions.isEmpty()) {

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 400); // above cursor (200) and all code text

            String currentLine = lines.get(cursorLine);
            int safeColumn = Math.min(cursorColumn, currentLine.length());
            int cursorTextWidth = mc.font.width(currentLine.substring(0, safeColumn));

            int popupX = getX() + lineNumberWidth + 5 + cursorTextWidth - scrollX;
            int popupY = getY() + 5 + (cursorLine + 1) * lineHeight - scrollY;
            int popupHeight = autocompleteSuggestions.size() * AUTOCOMPLETE_ITEM_HEIGHT;

            guiGraphics.fill(popupX, popupY, popupX + AUTOCOMPLETE_WIDTH, popupY + popupHeight, 0xFF252526);
            guiGraphics.fill(popupX, popupY, popupX + AUTOCOMPLETE_WIDTH, popupY + 1, 0xFF555555);
            guiGraphics.fill(popupX, popupY + popupHeight - 1, popupX + AUTOCOMPLETE_WIDTH, popupY + popupHeight, 0xFF555555);

            for (int i = 0; i < autocompleteSuggestions.size(); i++) {
                String suggestion = autocompleteSuggestions.get(i);
                int itemY = popupY + i * AUTOCOMPLETE_ITEM_HEIGHT;

                boolean hovered = (getAutocompleteIndexAt(mouseX, mouseY) == i);
                if (i == autocompleteIndex || hovered) {
                    guiGraphics.fill(popupX, itemY, popupX + AUTOCOMPLETE_WIDTH, itemY + AUTOCOMPLETE_ITEM_HEIGHT,
                            hovered ? 0xFF2A2D2E : 0xFF094771);
                }

                int color = COLOR_NORMAL;
                if (CONTROL_KEYWORDS.contains(suggestion)) {
                    color = COLOR_CONTROL_KEYWORD;
                } else if (DECLARATION_KEYWORDS.contains(suggestion)) {
                    color = COLOR_DECLARATION_KEYWORD;
                } else if (BUILTIN_FUNCTIONS.contains(suggestion) || functions.contains(suggestion) || builtins.contains(suggestion)) {
                    color = COLOR_FUNCTION;
                } else if (BUILTIN_CLASSES.contains(suggestion) || classes.contains(suggestion) || nativeClasses.contains(suggestion)) {
                    color = COLOR_CLASS;
                } else if (variables.contains(suggestion)) {
                    color = COLOR_VARIABLE;
                }

                guiGraphics.drawString(mc.font, suggestion, popupX + 5, itemY + 2, color);
            }

            guiGraphics.pose().popPose();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        if (autocompleteOpen && !autocompleteSuggestions.isEmpty() && button == 0) {
            int clickedIndex = getAutocompleteIndexAt(mouseX, mouseY);
            if (clickedIndex >= 0) {
                autocompleteIndex = clickedIndex;
                acceptAutocomplete();
                return true;
            }
        }

        if (!isMouseOver(mouseX, mouseY)) {
            setFocused(false);
            clearSelection();
            return false; // Let the inventory handle it
        }

        setFocused(true);

        if (button == 0) {

            int[] pos = getLineColumnAt(mouseX, mouseY);

            autocompleteOpen = false;
            autocompleteSuggestions.clear();

            if (hasShiftDown()) {
                startSelection();
            } else {
                clearSelection();
            }

            cursorLine = pos[0];
            cursorColumn = pos[1];

            ensureCursorVisible();

            isDragging = true;

            return true;
        }

        return false;
    }

    private int getAutocompleteIndexAt(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();

        String currentLine = lines.get(cursorLine);
        int safeColumn = Math.min(cursorColumn, currentLine.length());
        int cursorTextWidth = mc.font.width(currentLine.substring(0, safeColumn));

        int popupX = getX() + lineNumberWidth + 5 + cursorTextWidth - scrollX;
        int popupY = getY() + 5 + (cursorLine + 1) * lineHeight - scrollY;
        int popupHeight = autocompleteSuggestions.size() * AUTOCOMPLETE_ITEM_HEIGHT;

        if (mouseX < popupX || mouseX > popupX + AUTOCOMPLETE_WIDTH
                || mouseY < popupY || mouseY > popupY + popupHeight) {
            return -1;
        }

        int index = (int) ((mouseY - popupY) / AUTOCOMPLETE_ITEM_HEIGHT);
        return (index >= 0 && index < autocompleteSuggestions.size()) ? index : -1;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        AeroComputers.LOGGER.info("mouseDragged called, isDragging={}", isDragging);

        if (!isDragging || button != 0) {
            return false;
        }

        if (!hasSelection()) {
            startSelection();
        }

        int[] pos = getLineColumnAt(mouseX, mouseY);
        cursorLine = pos[0];
        cursorColumn = pos[1];

        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;

            // a click-and-release at the exact same spot with no movement
            // shouldn't leave a zero-width "selection" active
            if (hasSelection() && selectionLine == cursorLine && selectionColumn == cursorColumn) {
                clearSelection();
            }
        }
        return true;
    }


    @Override
    public boolean charTyped(char c, int modifiers) {

        if (!isFocused()) {
            return false;
        }

        // Opening brackets
        if (isOpeningBracket(c)) {

            char closing = matchingBracket(c);

            // Replace selection with the pair
            if (hasSelection()) {
                String selected = getSelectedText();
                deleteSelection();

                insertText(c + selected + closing);

                // Put cursor before closing bracket
                cursorColumn -= 1;

                return true;
            }

            // Insert both brackets
            insertText("" + c + closing);

            // Put cursor between them
            cursorColumn--;

            return true;
        }

        // Closing brackets
        if (isClosingBracket(c)) {

            // If the character immediately after the cursor
            // is already the bracket we're typing, just move over it.
            if (isNextChar(c)) {
                cursorColumn++;
                clearSelection();
                return true;
            }

            insertText(String.valueOf(c));
            return true;
        }

        // Normal character
        insertText(String.valueOf(c));

        maybeAutoOutdent();
        updateAutocomplete();

        return true;
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {

        if (!isFocused()) {
            return false;
        }

        // =========================================================
// AUTOCOMPLETE NAVIGATION
// =========================================================

        if (autocompleteOpen) {

            // Up
            if (keyCode == 265) {

                autocompleteIndex--;

                if (autocompleteIndex < 0) {
                    autocompleteIndex =
                            autocompleteSuggestions.size() - 1;
                }

                return true;
            }

            // Down
            if (keyCode == 264) {

                autocompleteIndex++;

                if (autocompleteIndex >= autocompleteSuggestions.size()) {
                    autocompleteIndex = 0;
                }

                return true;
            }

            // Escape
            if (keyCode == 256) {

                autocompleteOpen = false;
                return true;
            }

            // Tab
            if (keyCode == 258) {

                acceptAutocomplete();
                return true;
            }

            // Enter
            if (keyCode == 257) {
                acceptAutocomplete();
                return true;
            }
        }

        if (hasControlDown()) {

            // Select all
            if (keyCode == 65) {
                selectionLine = 0;
                selectionColumn = 0;

                cursorLine = lines.size() - 1;
                cursorColumn = lines.get(cursorLine).length();

                return true;
            }

            // Copy
            if (keyCode == 67) {
                if (hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
                }
                return true;
            }

            // Cut
            if (keyCode == 88) {
                if (hasSelection()) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(getSelectedText());
                    deleteSelection();
                }
                return true;
            }

            // Paste
            if (keyCode == 86) {
                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clipboard != null && !clipboard.isEmpty()) {
                    insertText(clipboard);
                }
                return true;
            }
        }

        // Tab / Shift+Tab
        if (keyCode == 258) {

            if (hasSelection()) {
                int[] sel = normalizedSelection();
                int startLine = sel[0], endLine = sel[2];

                for (int i = startLine; i <= endLine; i++) {
                    if (hasShiftDown()) {
                        dedentLine(i);
                    } else {
                        indentLine(i);
                    }
                }

                // keep selection spanning the same lines after re-indenting
                selectionLine = startLine;
                selectionColumn = 0;
                cursorLine = endLine;
                cursorColumn = lines.get(endLine).length();

            } else if (hasShiftDown()) {
                int removed = dedentLine(cursorLine);
                cursorColumn = Math.max(0, cursorColumn - removed);
            } else {
                insertText(INDENT);
            }

            return true;
        }


        //BackSpace
        if (keyCode == 259) {

            if (hasSelection()) {
                deleteSelection();
                return true;
            }

            // Delete matching bracket pair: ()
            if (cursorColumn > 0 && cursorColumn < lines.get(cursorLine).length()) {

                String line = lines.get(cursorLine);

                char left = line.charAt(cursorColumn - 1);
                char right = line.charAt(cursorColumn);

                if (matchingBracket(left) == right) {

                    lines.set(
                            cursorLine,
                            line.substring(0, cursorColumn - 1)
                                    + line.substring(cursorColumn + 1)
                    );

                    cursorColumn--;

                    return true;
                }
            }

            // Existing backspace behavior
            if (cursorColumn > 0) {

                String line = lines.get(cursorLine);
                cursorColumn = Math.min(cursorColumn, line.length());

                if (line.substring(0, cursorColumn).isBlank()) {

                    int re = Math.min(INDENT.length(), cursorColumn);

                    lines.set(
                            cursorLine,
                            line.substring(0, cursorColumn - re)
                                    + line.substring(cursorColumn)
                    );

                    cursorColumn -= re;

                } else {

                    lines.set(
                            cursorLine,
                            line.substring(0, cursorColumn - 1)
                                    + line.substring(cursorColumn)
                    );

                    cursorColumn--;
                }

            } else if (cursorLine > 0) {

                String prevLine = lines.get(cursorLine - 1);
                String currentLine = lines.remove(cursorLine);

                cursorColumn = prevLine.length();

                lines.set(
                        cursorLine - 1,
                        prevLine + currentLine
                );

                cursorLine--;
            }

            ensureCursorVisible();
            rescanLocalCompletions();
            closeAutocomplete();

            return true;
        }

        // Delete (forward)
        if (keyCode == 261) {

            if (hasSelection()) {
                deleteSelection();
            } else if (cursorColumn < lines.get(cursorLine).length()) {

                String line = lines.get(cursorLine);
                cursorColumn = Math.min(cursorColumn, line.length());

                lines.set(
                        cursorLine,
                        line.substring(0, cursorColumn)
                                + line.substring(cursorColumn + 1)
                );

            } else if (cursorLine < lines.size() - 1) {
                // Merge next line into the current one
                String nextLine = lines.remove(cursorLine + 1);
                lines.set(cursorLine, lines.get(cursorLine) + nextLine);
            }

            ensureCursorVisible();
            rescanLocalCompletions();
            closeAutocomplete();

            return true;
        }

        // Enter
        if (keyCode == 257) {

            if (hasSelection()) {
                deleteSelection();
            }

            String line = lines.get(cursorLine);
            cursorColumn = Math.min(cursorColumn, line.length());

            String before = line.substring(0, cursorColumn);
            String after = line.substring(cursorColumn);

            String indent = leadingWhitespace(line);
            if (isBlockOpener(before.trim())) {
                indent += INDENT;
            }

            lines.set(cursorLine, before);
            lines.add(cursorLine + 1, indent + after);

            cursorLine++;
            cursorColumn = indent.length();
            ensureCursorVisible();
            rescanLocalCompletions();

            return true;
        }


        if (keyCode == 263) {

            if (hasShiftDown())
                startSelection();
            else
                clearSelection();


            if (cursorColumn > 0) {
                cursorColumn--;
                ensureCursorVisible();

                closeAutocomplete();
            }

            return true;
        }

        if (keyCode == 262) {

            if (hasShiftDown())
                startSelection();
            else
                clearSelection();


            if (cursorColumn < lines.get(cursorLine).length()) {
                cursorColumn++;
                ensureCursorVisible();

                closeAutocomplete();
            }

            return true;
        }

        if (keyCode == 265) {

            if (hasShiftDown())
                startSelection();
            else
                clearSelection();


            if (cursorLine > 0) {

                cursorLine--;

                cursorColumn = Math.min(
                        cursorColumn,
                        lines.get(cursorLine).length()
                );

                ensureCursorVisible();

                closeAutocomplete();
            }

            return true;
        }

        if (keyCode == 264) {

            if (hasShiftDown())
                startSelection();
            else
                clearSelection();


            if (cursorLine < lines.size() - 1) {

                cursorLine++;

                cursorColumn = Math.min(
                        cursorColumn,
                        lines.get(cursorLine).length()
                );

                ensureCursorVisible();

                closeAutocomplete();
            }

            return true;
        }


        return true;
    }

    private boolean hasSelection() {
        return selectionLine != -1;
    }


    private void clearSelection() {
        selectionLine = -1;
        selectionColumn = -1;
    }


    private void startSelection() {
        if (!hasSelection()) {
            selectionLine = cursorLine;
            selectionColumn = cursorColumn;
        }
    }

    /**
     * Returns {startLine, startColumn, endLine, endColumn} with the selection
     * normalized so that the start point always comes before the end point.
     */
    private int[] normalizedSelection() {
        int startLine = selectionLine;
        int startColumn = selectionColumn;

        int endLine = cursorLine;
        int endColumn = cursorColumn;

        if (startLine > endLine ||
                (startLine == endLine && startColumn > endColumn)) {

            int tempLine = startLine;
            int tempColumn = startColumn;

            startLine = endLine;
            startColumn = endColumn;

            endLine = tempLine;
            endColumn = tempColumn;
        }

        return new int[]{startLine, startColumn, endLine, endColumn};
    }


    private String getSelectedText() {
        if (!hasSelection()) {
            return "";
        }

        int[] sel = normalizedSelection();
        int startLine = sel[0], startColumn = sel[1], endLine = sel[2], endColumn = sel[3];

        if (startLine == endLine) {
            return lines.get(startLine).substring(startColumn, endColumn);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(lines.get(startLine).substring(startColumn));

        for (int i = startLine + 1; i < endLine; i++) {
            sb.append('\n').append(lines.get(i));
        }

        sb.append('\n').append(lines.get(endLine).substring(0, endColumn));

        return sb.toString();
    }


    private void deleteSelection() {

        if (!hasSelection())
            return;


        int[] sel = normalizedSelection();
        int startLine = sel[0], startColumn = sel[1], endLine = sel[2], endColumn = sel[3];


        if (startLine == endLine) {

            String line = lines.get(startLine);

            lines.set(
                    startLine,
                    line.substring(0, startColumn)
                            + line.substring(endColumn)
            );

        } else {

            String first =
                    lines.get(startLine).substring(0, startColumn);

            String last =
                    lines.get(endLine).substring(endColumn);


            lines.set(startLine, first + last);


            for (int i = endLine; i > startLine; i--) {
                lines.remove(i);
            }
        }


        cursorLine = startLine;
        cursorColumn = startColumn;

        clearSelection();
    }


    /**
     * Inserts arbitrary (possibly multi-line) text at the cursor, replacing
     * any active selection first. Used by typing, and by Ctrl+V paste.
     */
    private void insertText(String text) {
        timer = 0;
        draw_cursor = true;


        if (hasSelection()) {
            deleteSelection();
        }

        String[] parts = text.split("\n", -1);

        String line = lines.get(cursorLine);
        cursorColumn = Math.min(cursorColumn, line.length());
        String before = line.substring(0, cursorColumn);
        String after = line.substring(cursorColumn);

        if (parts.length == 1) {
            lines.set(cursorLine, before + parts[0] + after);
            cursorColumn += parts[0].length();
        } else {
            lines.set(cursorLine, before + parts[0]);

            for (int i = 1; i < parts.length - 1; i++) {
                lines.add(cursorLine + i, parts[i]);
            }

            lines.add(cursorLine + parts.length - 1, parts[parts.length - 1] + after);

            cursorLine += parts.length - 1;
            cursorColumn = parts[parts.length - 1].length();
        }

        ensureCursorVisible();
        rescanLocalCompletions();
        updateAutocomplete();
    }


    public String getText() {
        return String.join("\n", lines);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {

        if (!isMouseOver(mouseX, mouseY))
            return false;


        this.scrollY -= (int)(scrollY * lineHeight * 3);


        clampScroll();

        return true;
    }

    private void clampScroll() {

        int maxY = Math.max(
                0,
                lines.size() * lineHeight - height + lineHeight
        );

        scrollY = Math.max(
                0,
                Math.min(scrollY, maxY)
        );


        int maxX = 0;

        Minecraft mc = Minecraft.getInstance();

        for (String line : lines) {
            maxX = Math.max(
                    maxX,
                    mc.font.width(line)
            );
        }

        maxX = Math.max(
                0,
                maxX - (width - lineNumberWidth - 10)
        );

        scrollX = Math.max(
                0,
                Math.min(scrollX, maxX)
        );
    }

    private static boolean isBlockOpener(String trimmedLine) {
        return trimmedLine.matches("^(if|while|for|def|class)\\b.*");
    }

    private static boolean isBlockCloser(String trimmedLine) {
        return trimmedLine.equals("endif") || trimmedLine.equals("endwhile")
                || trimmedLine.equals("endfor") || trimmedLine.equals("enddef") || trimmedLine.equals("endclass");
    }

    private static String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return line.substring(0, i);
    }

    private void indentLine(int lineIndex) {
        lines.set(lineIndex, INDENT + lines.get(lineIndex));
    }

    private int dedentLine(int lineIndex) {
        String line = lines.get(lineIndex);
        String leading = leadingWhitespace(line);
        int remove = Math.min(INDENT.length(), leading.length());
        lines.set(lineIndex, line.substring(remove));
        return remove;
    }

    private int[] getLineColumnAt(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();

        int relativeY = (int) mouseY - getY() - 5 + scrollY;
        int line = relativeY / lineHeight;

        if (line < 0) line = 0;
        if (line >= lines.size()) line = lines.size() - 1;

        String textLine = lines.get(line);
        int relativeX = (int) mouseX - getX() - lineNumberWidth - 5 + scrollX;

        int column = 0;
        for (int i = 0; i < textLine.length(); i++) {
            int width = mc.font.width(textLine.substring(0, i + 1));
            if (width > relativeX) break;
            column = i + 1;
        }

        return new int[]{line, column};
    }

    private boolean isOpeningBracket(char c) {
        return c == '(' || c == '[' || c == '{' || c == '"';
    }

    private boolean isClosingBracket(char c) {
        return c == ')' || c == ']' || c == '}' || c == '"';
    }

    private char matchingBracket(char c) {
        return switch (c) {
            case '(' -> ')';
            case '[' -> ']';
            case '{' -> '}';
            case ')' -> '(';
            case ']' -> '[';
            case '}' -> '{';
            case '"' -> '"';
            default -> '\0';
        };
    }

    private boolean isNextChar(char c) {
        String line = lines.get(cursorLine);

        return cursorColumn < line.length()
                && line.charAt(cursorColumn) == c;
    }


    private void drawHighlightedLine(
            GuiGraphics graphics,
            Minecraft mc,
            String line,
            int x,
            int y
    ) {
        int drawX = x;

        int i = 0;

        while (i < line.length()) {

            char c = line.charAt(i);

            /*
             * Whitespace
             */
            if (Character.isWhitespace(c)) {

                int start = i;

                while (i < line.length() &&
                        Character.isWhitespace(line.charAt(i))) {
                    i++;
                }

                String text = line.substring(start, i);

                graphics.drawString(
                        mc.font,
                        text,
                        drawX,
                        y,
                        COLOR_NORMAL
                );

                drawX += mc.font.width(text);
                continue;
            }


            /*
             * Strings
             */
            if (c == '"') {

                int start = i;
                i++;

                while (i < line.length()) {

                    if (line.charAt(i) == '"' &&
                            (i == 0 || line.charAt(i - 1) != '\\')) {

                        i++;
                        break;
                    }

                    i++;
                }

                String text = line.substring(start, i);

                graphics.drawString(
                        mc.font,
                        text,
                        drawX,
                        y,
                        COLOR_STRING
                );

                drawX += mc.font.width(text);
                continue;
            }


            /*
             * Comments
             *
             * Everything after # is gray.
             */
            if (c == '#') {

                String text = line.substring(i);

                graphics.drawString(
                        mc.font,
                        text,
                        drawX,
                        y,
                        COLOR_COMMENT
                );

                break;
            }


            /*
             * Brackets
             */
            if (isBracket(c)) {

                String text = String.valueOf(c);

                graphics.drawString(
                        mc.font,
                        text,
                        drawX,
                        y,
                        COLOR_BRACKET
                );

                drawX += mc.font.width(text);
                i++;
                continue;
            }


            /*
             * Numbers
             */
            if (Character.isDigit(c) ||
                    (c == '.' &&
                            i + 1 < line.length() &&
                            Character.isDigit(line.charAt(i + 1)))) {

                int start = i;

                while (i < line.length()) {

                    char n = line.charAt(i);

                    if (!Character.isDigit(n) && n != '.') {
                        break;
                    }

                    i++;
                }

                String text = line.substring(start, i);

                graphics.drawString(
                        mc.font,
                        text,
                        drawX,
                        y,
                        COLOR_NUMBER
                );

                drawX += mc.font.width(text);
                continue;
            }


            /*
             * Identifiers
             *
             * Examples:
             *
             * hello
             * getPos
             * myVariable
             * className
             */
            if (Character.isLetter(c) || c == '_') {

                int start = i;

                while (i < line.length()) {

                    char n = line.charAt(i);

                    if (!Character.isLetterOrDigit(n) && n != '_') {
                        break;
                    }

                    i++;
                }

                String word = line.substring(start, i);

                int color = getIdentifierColor(line, start, i, word);

                graphics.drawString(
                        mc.font,
                        word,
                        drawX,
                        y,
                        color
                );

                drawX += mc.font.width(word);
                continue;
            }


            /*
             * Operators / punctuation
             */
            String text = String.valueOf(c);

            graphics.drawString(
                    mc.font,
                    text,
                    drawX,
                    y,
                    COLOR_NORMAL
            );

            drawX += mc.font.width(text);
            i++;
        }
    }

    private boolean isBracket(char c) {
        return c == '(' ||
                c == ')' ||
                c == '[' ||
                c == ']' ||
                c == '{' ||
                c == '}';
    }

    private int getIdentifierColor(
            String line,
            int start,
            int end,
            String word
    ) {

        // =========================================================
        // Keywords
        // =========================================================

        if (CONTROL_KEYWORDS.contains(word)) {
            return COLOR_CONTROL_KEYWORD;
        }

        if (DECLARATION_KEYWORDS.contains(word)) {
            return COLOR_DECLARATION_KEYWORD;
        }

        // =========================================================
        // Class declaration
        //
        // class MyClass
        //       ^^^^^^^
        // =========================================================

        String before = line.substring(0, start).trim();

        if (before.matches("class\\s*$")) {
            return COLOR_CLASS;
        }

        // =========================================================
        // Built-in / user classes
        // =========================================================

        if (BUILTIN_CLASSES.contains(word)) {
            return COLOR_CLASS;
        }

        if (classes.contains(word) || nativeClasses.contains(word)) {
            return COLOR_CLASS;
        }

        // =========================================================
        // Functions
        // =========================================================

        if (BUILTIN_FUNCTIONS.contains(word)) {
            return COLOR_FUNCTION;
        }

        if (functions.contains(word)) {
            return COLOR_FUNCTION;
        }

        // Anything followed by '(' is a function call
        int next = end;

        while (next < line.length()
                && Character.isWhitespace(line.charAt(next))) {
            next++;
        }

        if (next < line.length()
                && line.charAt(next) == '(') {
            return COLOR_FUNCTION;
        }

        // =========================================================
        // Variables
        // =========================================================

        return COLOR_VARIABLE;
    }

    private void ensureCursorVisible() {

        Minecraft mc = Minecraft.getInstance();

        String line = lines.get(cursorLine);
        cursorColumn = Math.min(cursorColumn, line.length());

        // =========================================================
        // HORIZONTAL SCROLL
        // =========================================================

        int cursorTextX = mc.font.width(
                line.substring(0, cursorColumn)
        );

        int textAreaWidth = width - lineNumberWidth - 10;

        // Cursor is too far left
        if (cursorTextX < scrollX) {
            scrollX = cursorTextX;
        }

        // Cursor is too far right
        if (cursorTextX > scrollX + textAreaWidth) {
            scrollX = cursorTextX - textAreaWidth;
        }


        // =========================================================
        // VERTICAL SCROLL
        // =========================================================

        int cursorTop = cursorLine * lineHeight + lineHeight;
        int cursorBottom = cursorTop + lineHeight;

        // Cursor is above the visible area
        if (cursorTop < scrollY) {
            scrollY = cursorTop;
        }

        // Cursor is below the visible area
        if (cursorBottom > scrollY + height) {
            scrollY = cursorBottom - height;
        }


        // Don't allow negative scrolling
        scrollX = Math.max(0, scrollX);
        scrollY = Math.max(0, scrollY);


        // Don't scroll past the end of the document
        clampScroll();
    }

    private String getCurrentWord() {

        String line = lines.get(cursorLine);

        int end = Math.min(cursorColumn, line.length());
        int start = end;

        while (start > 0) {

            char c = line.charAt(start - 1);

            if (!Character.isLetterOrDigit(c) && c != '_') {
                break;
            }

            start--;
        }

        autocompleteStartColumn = start;

        return line.substring(start, end);
    }

    private void updateAutocomplete() {

        if (isInsideStringOrComment()) {
            autocompleteOpen = false;
            autocompleteSuggestions.clear();
            return;
        }

        String line = lines.get(cursorLine);
        int col = Math.min(cursorColumn, line.length());

        // Context 1: right after "something." — suggest all known method names
        int dotBefore = findDotBeforeWord(line, col);
        // Context 1: dot-access
        if (dotBefore != -1) {
            autocompleteMode = AutocompleteMode.METHOD;
            showMethodCompletions(line, col, dotBefore);
            return;
        }

        // Context 2: inside call parens
        String enclosingCall = findEnclosingCallName(line, col);
        if (enclosingCall != null && callableParams.containsKey(enclosingCall)) {
            autocompleteMode = AutocompleteMode.PARAM;
            showParamCompletions(enclosingCall, line, col);
            return;
        }

        // Fallback: existing normal identifier-prefix completion
        String prefix = getCurrentWord();
        autocompletePrefix = prefix;

        autocompleteSuggestions.clear();
        autocompleteIndex = 0;

        if (prefix.isEmpty()) {
            autocompleteOpen = false;
            return;
        }

        String lowerPrefix = prefix.toLowerCase();

        addMatching(BUILTIN_FUNCTIONS.stream().toList(), lowerPrefix);
        addMatching(functions, lowerPrefix);
        addMatching(classes, lowerPrefix);
        addMatching(variables, lowerPrefix);
        addMatching(nativeClasses, lowerPrefix);
        addMatching(CONTROL_KEYWORDS.stream().toList(), lowerPrefix);
        addMatching(DECLARATION_KEYWORDS.stream().toList(), lowerPrefix);

        if (autocompleteSuggestions.isEmpty()) {
            autocompleteOpen = false;
            return;
        }

        if (autocompleteSuggestions.size() > AUTOCOMPLETE_MAX_RESULTS) {
            autocompleteSuggestions.subList(AUTOCOMPLETE_MAX_RESULTS, autocompleteSuggestions.size()).clear();
        }

        autocompleteOpen = true;
        autocompleteMode = AutocompleteMode.NORMAL;
        autocompleteOpen = true;
    }

    private void addMatching(List<String> names, String prefix) {
        for (String name : names) {
            if (name.toLowerCase().startsWith(prefix)
                    && !autocompleteSuggestions.contains(name)) {

                autocompleteSuggestions.add(name);
            }
        }
    }

    private void acceptAutocomplete() {

        if (!autocompleteOpen || autocompleteSuggestions.isEmpty()) {
            return;
        }

        String suggestion = autocompleteSuggestions.get(autocompleteIndex);

        String line = lines.get(cursorLine);

        int start = autocompleteStartColumn;
        int end = cursorColumn;

        start = Math.max(0, Math.min(start, line.length()));
        end = Math.max(start, Math.min(end, line.length()));

        String currentWord = line.substring(start, end);

        // If the user already typed the full suggestion, don't re-insert the base word —
        // just close the popup (still allow "=" / "()" appending logic below to run once).
        boolean alreadyFullyTyped = currentWord.equals(suggestion);

        String insertion = alreadyFullyTyped ? "" : suggestion;
        int cursorOffsetFromEnd = 0;

        if (autocompleteMode == AutocompleteMode.PARAM) {
            boolean alreadyHasEquals = end < line.length() && line.charAt(end) == '=';
            if (!alreadyHasEquals) {
                insertion = insertion + "=";
            }
        } else if (autocompleteMode == AutocompleteMode.METHOD
                || (autocompleteMode == AutocompleteMode.NORMAL
                && (functions.contains(suggestion) || classes.contains(suggestion)
                || nativeClasses.contains(suggestion) || BUILTIN_FUNCTIONS.contains(suggestion)))) {

            boolean alreadyHasParens = end < line.length() && line.charAt(end) == '(';
            if (!alreadyHasParens) {
                insertion = insertion + "()";
                cursorOffsetFromEnd = 1;
            }
        }

        if (insertion.isEmpty()) {
            // nothing to insert and nothing to append — just close the popup
            autocompleteOpen = false;
            autocompleteSuggestions.clear();
            return;
        }

        lines.set(
                cursorLine,
                line.substring(0, start) + insertion + line.substring(end)
        );

        cursorColumn = start + (alreadyFullyTyped ? end - start : 0) + insertion.length() - cursorOffsetFromEnd;

        autocompleteOpen = false;
        autocompleteSuggestions.clear();

        timer = 0;
        draw_cursor = true;

        ensureCursorVisible();
        maybeAutoOutdent();
    }

    private boolean isInsideStringOrComment() {

        String line = lines.get(cursorLine);

        int end = Math.min(cursorColumn, line.length());

        boolean insideString = false;

        for (int i = 0; i < end; i++) {

            char c = line.charAt(i);

            // Comment
            if (c == '#' && !insideString) {
                return true;
            }

            // String
            if (c == '"' &&
                    (i == 0 || line.charAt(i - 1) != '\\')) {

                insideString = !insideString;
            }
        }

        return insideString;
    }

    public boolean isAutocompleteOpen() {
        return autocompleteOpen;
    }

    public void closeAutocomplete() {
        autocompleteOpen = false;
        autocompleteSuggestions.clear();
    }

    /**
     * Returns the index right after a '.' if the text immediately before the
     * current word being typed is a dot (member access context), else -1.
     */
    private int findDotBeforeWord(String line, int col) {
        int start = col;
        while (start > 0 && (Character.isLetterOrDigit(line.charAt(start - 1)) || line.charAt(start - 1) == '_')) {
            start--;
        }
        if (start > 0 && line.charAt(start - 1) == '.') {
            return start;
        }
        return -1;
    }

    private void showMethodCompletions(String line, int col, int wordStart) {
        autocompleteStartColumn = wordStart;
        String prefix = line.substring(wordStart, col).toLowerCase();

        autocompleteSuggestions.clear();
        autocompleteIndex = 0;



        Set<String> seen = new LinkedHashSet<>();
        for (List<String> methods : classMethods.values()) {
            for (String m : methods) {
                if (m.toLowerCase().startsWith(prefix) && seen.add(m)) {
                    autocompleteSuggestions.add(m);
                }
            }
        }

        if (autocompleteSuggestions.isEmpty()) {
            autocompleteOpen = false;
            return;
        }

        if (autocompleteSuggestions.size() > AUTOCOMPLETE_MAX_RESULTS) {
            autocompleteSuggestions.subList(AUTOCOMPLETE_MAX_RESULTS, autocompleteSuggestions.size()).clear();
        }

        autocompleteOpen = true;
    }

    /**
     * Scans backwards from the cursor on the current line to find an unmatched
     * '(' and returns the identifier immediately before it, or null if none.
     */
    private String findEnclosingCallName(String line, int col) {
        int depth = 0;
        int i = col - 1;

        while (i >= 0) {
            char c = line.charAt(i);
            if (c == ')') depth++;
            else if (c == '(') {
                if (depth == 0) {
                    int end = i;
                    int start = end;
                    while (start > 0 && (Character.isLetterOrDigit(line.charAt(start - 1)) || line.charAt(start - 1) == '_')) {
                        start--;
                    }
                    if (start < end) {
                        return line.substring(start, end);
                    }
                    return null;
                }
                depth--;
            }
            i--;
        }
        return null;
    }

    private void showParamCompletions(String callName, String line, int col) {
        String currentWordPrefix = getCurrentWord(); // also sets autocompleteStartColumn
        String lowerPrefix = currentWordPrefix.toLowerCase();

        autocompleteSuggestions.clear();
        autocompleteIndex = 0;

        for (String param : callableParams.get(callName)) {
            if (param.toLowerCase().startsWith(lowerPrefix)) {
                autocompleteSuggestions.add(param);
            }
        }

        if (autocompleteSuggestions.isEmpty()) {
            autocompleteOpen = false;
            return;
        }

        if (autocompleteSuggestions.size() > AUTOCOMPLETE_MAX_RESULTS) {
            autocompleteSuggestions.subList(AUTOCOMPLETE_MAX_RESULTS, autocompleteSuggestions.size()).clear();
        }

        autocompleteOpen = true;
    }

    private void rescanLocalCompletions() {
        String fullText = String.join("\n", lines);
        String[] programLines = fullText.split("\n", -1);

        Set<String> localVars = new LinkedHashSet<>(serverVariables);
        Set<String> localFunctions = new LinkedHashSet<>(serverFunctions);
        Set<String> localClasses = new LinkedHashSet<>(serverClasses);
        Map<String, List<String>> localClassMethods = new LinkedHashMap<>(serverClassMethods);
        Map<String, List<String>> localCallableParams = new LinkedHashMap<>(serverCallableParams);
        localCallableParams.putAll(BUILTIN_FUNCTION_PARAMS);
        localCallableParams.putAll(BUILTIN_CLASS_PARAMS);

        String currentClass = null;

        for (String line : programLines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            if (trimmed.startsWith("class")) {
                String name = trimmed.substring(5).trim();
                if (name.endsWith("()")) name = name.substring(0, name.length() - 2).trim();
                if (!name.isEmpty()) {
                    currentClass = name;
                    localClasses.add(name);
                    localClassMethods.computeIfAbsent(name, k -> new ArrayList<>());
                }
                continue;
            }
            if (trimmed.equals("endclass")) {
                currentClass = null;
                continue;
            }

            if (trimmed.startsWith("def")) {
                int space = trimmed.indexOf(' ');
                int open = trimmed.indexOf('(');
                int close = trimmed.indexOf(')');
                if (space != -1 && open != -1 && close != -1 && open < close) {
                    String name = trimmed.substring(space + 1, open).trim();
                    String argsStr = trimmed.substring(open + 1, close).trim();
                    List<String> params = argsStr.isEmpty() ? List.of() :
                            Arrays.stream(argsStr.split(",")).map(s -> s.trim().split("=")[0].trim())
                                    .filter(p -> !p.equals("self")).toList();

                    if (!name.isEmpty()) {
                        if (currentClass != null) {
                            localClassMethods.computeIfAbsent(currentClass, k -> new ArrayList<>()).add(name);
                        } else {
                            localFunctions.add(name);
                        }
                        localCallableParams.put(name, params);
                    }
                }
                continue;
            }

            if (trimmed.contains("=") && !trimmed.contains("==") && !trimmed.contains("!=")
                    && !trimmed.contains(">=") && !trimmed.contains("<=")
                    && !trimmed.contains(".") && !trimmed.contains("[")) {

                String left = trimmed.substring(0, trimmed.indexOf('=')).trim();
                if (left.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
                    localVars.add(left);
                } else if (left.matches("^[A-Za-z_][A-Za-z0-9_]*(\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)+$")) {
                    for (String name : left.split("\\s*,\\s*")) localVars.add(name.trim());
                }
            }
        }

        this.variables = List.copyOf(localVars);
        this.functions = List.copyOf(localFunctions);
        this.classes = List.copyOf(localClasses);
        this.classMethods = localClassMethods;
        this.callableParams = localCallableParams;
    }

    private void maybeAutoOutdent() {
        String currentLine = lines.get(cursorLine);
        String trimmed = currentLine.trim();

        if (isBlockCloser(trimmed)
                && currentLine.equals(leadingWhitespace(currentLine) + trimmed)
                && cursorColumn == currentLine.length()) {

            int removed = dedentLine(cursorLine);
            cursorColumn -= removed;
        }
    }
}