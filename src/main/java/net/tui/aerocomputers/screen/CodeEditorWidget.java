package net.tui.aerocomputers.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.tui.aerocomputers.AeroComputers;
import net.tui.aerocomputers.block.entity.BoardComputerBlockEntity;

import java.util.*;
import java.util.function.Consumer;

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

    public static final Map<String, String> HELP_LOOKUP = Map.ofEntries(
            Map.entry("if", "help.aero.if.text"),
            Map.entry("while", "help.aero.while.text"),
            Map.entry("for", "help.aero.for.text"),
            Map.entry("def", "help.aero.def.text"),
            Map.entry("class", "help.aero.class.text"),
            Map.entry("Label", "help.aero.label.text"),
            Map.entry("Line", "help.aero.line.text"),
            Map.entry("Rect", "help.aero.rect.text"),
            Map.entry("Poly", "help.aero.poly.text"),
            Map.entry("print", "help.aero.print.text"),
            Map.entry("getPos", "help.aero.getpos.text"),
            Map.entry("getRot", "help.aero.getrot.text"),
            Map.entry("getVel", "help.aero.getvel.text")
    );

    private boolean readOnly = false;

    private static final int SCROLLBAR_SIZE = 7;
    private static final int SCROLLBAR_MIN_THUMB = 15;

    private boolean draggingVerticalScrollbar = false;
    private boolean draggingHorizontalScrollbar = false;

    private int scrollbarDragOffset = 0;

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    private Consumer<String> helpCallback;

    private record HelpWordHit(
            String word,
            int line,
            int startColumn,
            int endColumn
    ) {}

    private HelpWordHit hoveredHelpWord;

    public CodeEditorWidget(
            int x,
            int y,
            int width,
            int height,
            String text,
            BoardComputerBlockEntity.EditorCompletions completions,
            Consumer<String> helpCallback
    ) {
        super(x, y, width, height, Component.empty());
        lines.addAll(List.of(text.split("\n", -1)));

        this.serverVariables = completions.variables();
        this.serverFunctions = completions.functions();
        this.serverClasses = completions.classes();
        this.serverClassMethods = completions.classMethods();
        this.serverCallableParams = completions.callableParams();

        this.builtins = completions.builtins();
        this.nativeClasses = completions.nativeClasses();
        this.helpCallback = helpCallback;

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

        hoveredHelpWord = null;

        if (hasControlDown()) {
            hoveredHelpWord = getHelpWordAt(mouseX, mouseY);
        }

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
                getX(),
                getY(),
                getX() + width - SCROLLBAR_SIZE,
                getY() + height - SCROLLBAR_SIZE
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
                getX() + width - SCROLLBAR_SIZE,            // right
                getY() + height - SCROLLBAR_SIZE            // bottom
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
                    getY() + 5 + i * lineHeight - scrollY,
                    i
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

        drawScrollbars(guiGraphics);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // =========================================================
// Scrollbars
// =========================================================

        if (button == 0) {

            if (isOverVerticalScrollbar(mouseX, mouseY)) {

                if (isOverVerticalThumb(mouseX, mouseY)) {

                    draggingVerticalScrollbar = true;

                    int viewportHeight = height - SCROLLBAR_SIZE;
                    int contentHeight = Math.max(
                            viewportHeight,
                            lines.size() * lineHeight
                    );

                    int thumbHeight = Math.max(
                            SCROLLBAR_MIN_THUMB,
                            (int) ((float) viewportHeight
                                    / contentHeight
                                    * viewportHeight)
                    );

                    int maxScroll = contentHeight - viewportHeight;
                    int maxThumbTravel = viewportHeight - thumbHeight;

                    int thumbY = getY();

                    if (maxThumbTravel > 0) {
                        thumbY += (int) (
                                ((float) scrollY / maxScroll)
                                        * maxThumbTravel
                        );
                    }

                    scrollbarDragOffset =
                            (int) mouseY - thumbY;

                } else {

                    // Click on track
                    int viewportHeight = height - SCROLLBAR_SIZE;
                    int contentHeight = Math.max(
                            viewportHeight,
                            lines.size() * lineHeight
                    );

                    int maxScroll = contentHeight - viewportHeight;

                    double percentage =
                            ((mouseY - getY())
                                    / (double) viewportHeight);

                    scrollY = (int) (percentage * maxScroll);

                    clampScroll();
                }

                return true;
            }


            if (isOverHorizontalScrollbar(mouseX, mouseY)) {

                if (isOverHorizontalThumb(mouseX, mouseY)) {

                    draggingHorizontalScrollbar = true;

                    int viewportWidth =
                            width - lineNumberWidth - SCROLLBAR_SIZE;

                    int contentWidth = getContentWidth();

                    int thumbWidth = Math.max(
                            SCROLLBAR_MIN_THUMB,
                            (int) ((float) viewportWidth
                                    / contentWidth
                                    * viewportWidth)
                    );

                    int maxScroll = contentWidth - viewportWidth;
                    int maxThumbTravel = viewportWidth - thumbWidth;

                    int thumbX = getX() + lineNumberWidth;

                    if (maxThumbTravel > 0) {
                        thumbX += (int) (
                                ((float) scrollX / maxScroll)
                                        * maxThumbTravel
                        );
                    }

                    scrollbarDragOffset =
                            (int) mouseX - thumbX;

                } else {

                    int viewportWidth =
                            width - lineNumberWidth - SCROLLBAR_SIZE;

                    int contentWidth = getContentWidth();

                    int maxScroll =
                            contentWidth - viewportWidth;

                    double percentage =
                            ((mouseX - getX() - lineNumberWidth)
                                    / (double) viewportWidth);

                    scrollX = (int) (percentage * maxScroll);

                    clampScroll();
                }

                return true;
            }
        }

        if (autocompleteOpen
                && !autocompleteSuggestions.isEmpty()
                && button == 0) {

            int clickedIndex =
                    getAutocompleteIndexAt(mouseX, mouseY);

            if (clickedIndex >= 0) {
                autocompleteIndex = clickedIndex;
                acceptAutocomplete();
                return true;
            }
        }

        // Ctrl + click help link
        if (hasControlDown() && button == 0) {

            HelpWordHit helpWord =
                    getHelpWordAt(mouseX, mouseY);

            if (helpWord != null) {

                String translationKey =
                        HELP_LOOKUP.get(helpWord.word());

                if (helpCallback != null) {
                    helpCallback.accept(translationKey);
                }

                return true;
            }
        }

        if (!isMouseOver(mouseX, mouseY)) {
            setFocused(false);
            clearSelection();
            return false;
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
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {

        if (button != 0) {
            return false;
        }

        // =========================================================
        // Vertical scrollbar dragging
        // =========================================================

        if (draggingVerticalScrollbar) {

            int viewportHeight =
                    height - SCROLLBAR_SIZE;

            int contentHeight = Math.max(
                    viewportHeight,
                    lines.size() * lineHeight
            );

            int thumbHeight = Math.max(
                    SCROLLBAR_MIN_THUMB,
                    (int) ((float) viewportHeight
                            / contentHeight
                            * viewportHeight)
            );

            int maxThumbTravel =
                    viewportHeight - thumbHeight;

            int maxScroll =
                    contentHeight - viewportHeight;

            if (maxThumbTravel > 0 && maxScroll > 0) {

                int newThumbY =
                        (int) mouseY
                                - getY()
                                - scrollbarDragOffset;

                newThumbY =
                        Math.max(
                                0,
                                Math.min(
                                        newThumbY,
                                        maxThumbTravel
                                )
                        );

                scrollY =
                        (int) (
                                (newThumbY
                                        / (double) maxThumbTravel)
                                        * maxScroll
                        );

                clampScroll();
            }

            return true;
        }


        // =========================================================
        // Horizontal scrollbar dragging
        // =========================================================

        if (draggingHorizontalScrollbar) {

            int viewportWidth =
                    width - lineNumberWidth - SCROLLBAR_SIZE;

            int contentWidth = getContentWidth();

            int thumbWidth = Math.max(
                    SCROLLBAR_MIN_THUMB,
                    (int) ((float) viewportWidth
                            / contentWidth
                            * viewportWidth)
            );

            int maxThumbTravel =
                    viewportWidth - thumbWidth;

            int maxScroll =
                    contentWidth - viewportWidth;

            if (maxThumbTravel > 0 && maxScroll > 0) {

                int newThumbX =
                        (int) mouseX
                                - getX()
                                - lineNumberWidth
                                - scrollbarDragOffset;

                newThumbX =
                        Math.max(
                                0,
                                Math.min(
                                        newThumbX,
                                        maxThumbTravel
                                )
                        );

                scrollX =
                        (int) (
                                (newThumbX
                                        / (double) maxThumbTravel)
                                        * maxScroll
                        );

                clampScroll();
            }

            return true;
        }


        // =========================================================
        // Normal text selection dragging
        // =========================================================

        if (!isDragging) {
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
    public boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button
    ) {

        if (button == 0) {

            draggingVerticalScrollbar = false;
            draggingHorizontalScrollbar = false;

            isDragging = false;

            if (hasSelection()
                    && selectionLine == cursorLine
                    && selectionColumn == cursorColumn) {

                clearSelection();
            }
        }

        return true;
    }


    @Override
    public boolean charTyped(char c, int modifiers) {
        if (readOnly) {
            return false;
        }

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
        if (readOnly) {
            return false;
        }

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

    public void setText(String text) {
        lines.clear();
        lines.addAll(List.of(text.split("\n", -1)));
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {

        if (!isMouseOver(mouseX, mouseY))
            return false;

        if (hasShiftDown()) {

            this.scrollX -= (int) (scrollY * 30);

        } else {

            this.scrollY -= (int) (scrollY * lineHeight * 3);
        }

        clampScroll();

        return true;
    }

    private void clampScroll() {

        // =========================================================
        // Vertical
        // =========================================================

        int viewportHeight =
                height - SCROLLBAR_SIZE;

        int contentHeight =
                (lines.size() + 1) * lineHeight;

        int maxY = Math.max(
                0,
                contentHeight - viewportHeight
        );

        scrollY = Math.max(
                0,
                Math.min(scrollY, maxY)
        );


        // =========================================================
        // Horizontal
        // =========================================================

        int viewportWidth =
                width - lineNumberWidth - SCROLLBAR_SIZE;

        int contentWidth =
                getContentWidth();

        int maxX = Math.max(
                0,
                contentWidth - viewportWidth
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

        int relativeY =
                (int) mouseY
                        - getY()
                        - 5
                        + scrollY;

        int line = relativeY / lineHeight;

        if (line < 0) {
            line = 0;
        }

        if (line >= lines.size()) {
            line = lines.size() - 1;
        }

        String textLine = lines.get(line);

        int relativeX =
                (int) mouseX
                        - getX()
                        - lineNumberWidth
                        - 5
                        + scrollX;

        int column = 0;

        for (int i = 0; i < textLine.length(); i++) {

            int textWidth =
                    mc.font.width(
                            textLine.substring(0, i + 1)
                    );

            if (textWidth > relativeX) {
                break;
            }

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
            int y,
            int lineIndex
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

                String word = line.substring(start, i);

                int color = getIdentifierColor(
                        line,
                        start,
                        i,
                        word
                );

                graphics.drawString(
                        mc.font,
                        word,
                        drawX,
                        y,
                        color
                );

// Ctrl + hover help underline
                if (hoveredHelpWord != null
                        && hoveredHelpWord.line() == lineIndex
                        && hoveredHelpWord.startColumn() == start
                        && hoveredHelpWord.endColumn() == i) {

                    int wordWidth = mc.font.width(word);

                    graphics.fill(
                            drawX,
                            y + mc.font.lineHeight - 1,
                            drawX + wordWidth,
                            y + mc.font.lineHeight,
                            0xFF3399FF
                    );
                }

                drawX += mc.font.width(word);
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

// Ctrl + hover help underline
                if (hoveredHelpWord != null
                        && hoveredHelpWord.line() == lineIndex
                        && hoveredHelpWord.startColumn() == start
                        && hoveredHelpWord.endColumn() == i) {

                    int wordWidth = mc.font.width(word);

                    graphics.fill(
                            drawX,
                            y + mc.font.lineHeight - 1,
                            drawX + wordWidth,
                            y + mc.font.lineHeight,
                            0xFF3399FF
                    );
                }

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

        int textAreaWidth = width - lineNumberWidth - 10 - SCROLLBAR_SIZE;

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

        boolean isDeclarationLine = line.trim().startsWith("class ") || line.trim().startsWith("def ");

        // Context 1: dot-access
        int dotBefore = findDotBeforeWord(line, col);
        if (dotBefore != -1 && !isDeclarationLine) {
            autocompleteMode = AutocompleteMode.METHOD;
            showMethodCompletions(line, col, dotBefore);
            return;
        }

        // Context 2: inside call parens — also skip for declaration lines,
        // since "(" in "def name(" is a parameter list, not a call
        String enclosingCall = isDeclarationLine ? null : findEnclosingCallName(line, col);
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

        if (!isDeclarationLine) {
            addMatching(BUILTIN_FUNCTIONS.stream().toList(), lowerPrefix);
            addMatching(functions, lowerPrefix);
            addMatching(classes, lowerPrefix);
            addMatching(nativeClasses, lowerPrefix);
        }
        addMatching(variables, lowerPrefix);
        addMatching(CONTROL_KEYWORDS.stream().toList(), lowerPrefix);
        addMatching(DECLARATION_KEYWORDS.stream().toList(), lowerPrefix);

        if (autocompleteSuggestions.isEmpty()) {
            autocompleteOpen = false;
            return;
        }

        if (autocompleteSuggestions.size() > AUTOCOMPLETE_MAX_RESULTS) {
            autocompleteSuggestions.subList(AUTOCOMPLETE_MAX_RESULTS, autocompleteSuggestions.size()).clear();
        }

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
        boolean alreadyFullyTyped = currentWord.equals(suggestion);

        String suffix = "";
        int cursorOffsetFromEnd = 0;

        if (autocompleteMode == AutocompleteMode.PARAM) {
            boolean alreadyHasEquals = end < line.length() && line.charAt(end) == '=';
            if (!alreadyHasEquals) {
                suffix = "=";
            }
        } else if (autocompleteMode == AutocompleteMode.METHOD
                || (autocompleteMode == AutocompleteMode.NORMAL
                && (functions.contains(suggestion) || classes.contains(suggestion)
                || nativeClasses.contains(suggestion) || BUILTIN_FUNCTIONS.contains(suggestion)))) {

            boolean alreadyHasParens = end < line.length() && line.charAt(end) == '(';
            if (!alreadyHasParens) {
                suffix = "()";
                cursorOffsetFromEnd = 1;
            }
        }

        String replacement = alreadyFullyTyped ? currentWord : suggestion;
        String insertion = replacement + suffix;

        if (alreadyFullyTyped && suffix.isEmpty()) {
            // nothing to change at all — just close the popup
            autocompleteOpen = false;
            autocompleteSuggestions.clear();
            return;
        }

        lines.set(
                cursorLine,
                line.substring(0, start) + insertion + line.substring(end)
        );

        cursorColumn = start + insertion.length() - cursorOffsetFromEnd;

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
                            if (name.equals("__init__")) {
                                localCallableParams.put(currentClass, params); // constructor args, keyed by class name
                            }
                        } else {
                            localFunctions.add(name);
                        }
                        localCallableParams.put(name, params);
                        localVars.addAll(params); // see fix #3 below
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


    private HelpWordHit getHelpWordAt(double mouseX, double mouseY) {
        if (!HELP_LOOKUP.containsKey("")) {
            // no-op; keeps this method independent of rendering
        }

        Minecraft mc = Minecraft.getInstance();

        // Convert mouse Y into document line
        int relativeY = (int) mouseY - getY() - 5 + scrollY;

        if (relativeY < 0) {
            return null;
        }

        int lineIndex = relativeY / lineHeight;

        if (lineIndex < 0 || lineIndex >= lines.size()) {
            return null;
        }

        String line = lines.get(lineIndex);

        // Convert mouse X into text coordinates
        int relativeX =
                (int) mouseX
                        - getX()
                        - lineNumberWidth
                        - 5
                        + scrollX;

        if (relativeX < 0) {
            return null;
        }

        // Find which character the mouse is over
        int column = 0;

        for (int i = 0; i < line.length(); i++) {
            int charWidth = mc.font.width(line.substring(0, i + 1));

            if (charWidth > relativeX) {
                column = i;
                break;
            }

            column = i + 1;
        }

        // Find identifier around the column
        if (column > line.length()) {
            column = line.length();
        }

        int start = column;

        while (start > 0) {
            char c = line.charAt(start - 1);

            if (!Character.isLetterOrDigit(c) && c != '_') {
                break;
            }

            start--;
        }

        int end = column;

        while (end < line.length()) {
            char c = line.charAt(end);

            if (!Character.isLetterOrDigit(c) && c != '_') {
                break;
            }

            end++;
        }

        if (start == end) {
            return null;
        }

        String word = line.substring(start, end);

        if (!HELP_LOOKUP.containsKey(word)) {
            return null;
        }

        return new HelpWordHit(
                word,
                lineIndex,
                start,
                end
        );
    }

    private void drawScrollbars(GuiGraphics guiGraphics) {

        // =========================================================
        // Vertical scrollbar
        // =========================================================

        int viewportHeight = height - SCROLLBAR_SIZE;

        int contentHeight = Math.max(
                viewportHeight,
                lines.size() * lineHeight
        );

        boolean needsVertical = contentHeight > viewportHeight;

        if (needsVertical) {

            int trackX = getX() + width - SCROLLBAR_SIZE;
            int trackY = getY();

            int trackHeight = viewportHeight;

            // Thumb size based on visible/content ratio
            int thumbHeight = Math.max(
                    SCROLLBAR_MIN_THUMB,
                    (int) ((float) viewportHeight / contentHeight * trackHeight)
            );

            int maxScroll = Math.max(
                    1,
                    contentHeight - viewportHeight
            );

            int maxThumbTravel = trackHeight - thumbHeight;

            int thumbY = trackY;

            if (maxThumbTravel > 0) {
                thumbY += (int) (
                        ((float) scrollY / maxScroll)
                                * maxThumbTravel
                );
            }

            // Track
            guiGraphics.fill(
                    trackX,
                    trackY,
                    trackX + SCROLLBAR_SIZE,
                    trackY + trackHeight,
                    0xFF181818
            );

            // Thumb
            guiGraphics.fill(
                    trackX + 1,
                    thumbY + 1,
                    trackX + SCROLLBAR_SIZE - 1,
                    thumbY + thumbHeight - 1,
                    draggingVerticalScrollbar
                            ? 0xFFAAAAAA
                            : 0xFF666666
            );
        }


        // =========================================================
        // Horizontal scrollbar
        // =========================================================

        int viewportWidth =
                width - lineNumberWidth - SCROLLBAR_SIZE;

        int contentWidth = getContentWidth();

        boolean needsHorizontal = contentWidth > viewportWidth;

        if (needsHorizontal) {

            int trackX = getX() + lineNumberWidth;
            int trackY = getY() + height - SCROLLBAR_SIZE;

            int trackWidth = viewportWidth;

            int thumbWidth = Math.max(
                    SCROLLBAR_MIN_THUMB,
                    (int) ((float) viewportWidth / contentWidth * trackWidth)
            );

            int maxScroll = Math.max(
                    1,
                    contentWidth - viewportWidth
            );

            int maxThumbTravel = trackWidth - thumbWidth;

            int thumbX = trackX;

            if (maxThumbTravel > 0) {
                thumbX += (int) (
                        ((float) scrollX / maxScroll)
                                * maxThumbTravel
                );
            }

            // Track
            guiGraphics.fill(
                    trackX,
                    trackY,
                    trackX + trackWidth,
                    trackY + SCROLLBAR_SIZE,
                    0xFF181818
            );

            // Thumb
            guiGraphics.fill(
                    thumbX + 1,
                    trackY + 1,
                    thumbX + thumbWidth - 1,
                    trackY + SCROLLBAR_SIZE - 1,
                    draggingHorizontalScrollbar
                            ? 0xFFAAAAAA
                            : 0xFF666666
            );
        }
    }

    private int getContentWidth() {

        Minecraft mc = Minecraft.getInstance();

        int maxWidth = 0;

        for (String line : lines) {
            maxWidth = Math.max(
                    maxWidth,
                    mc.font.width(line)
            );
        }

        return maxWidth + 10;
    }

    private boolean isOverVerticalScrollbar(double mouseX, double mouseY) {

        int x = getX() + width - SCROLLBAR_SIZE;

        return mouseX >= x
                && mouseX <= getX() + width
                && mouseY >= getY()
                && mouseY <= getY() + height - SCROLLBAR_SIZE;
    }

    private boolean isOverHorizontalScrollbar(double mouseX, double mouseY) {

        int x = getX() + lineNumberWidth;
        int y = getY() + height - SCROLLBAR_SIZE;

        return mouseX >= x
                && mouseX <= getX() + width - SCROLLBAR_SIZE
                && mouseY >= y
                && mouseY <= getY() + height;
    }

    private boolean isOverVerticalThumb(double mouseX, double mouseY) {

        int viewportHeight = height - SCROLLBAR_SIZE;

        int contentHeight = Math.max(
                viewportHeight,
                lines.size() * lineHeight
        );

        if (contentHeight <= viewportHeight) {
            return false;
        }

        int trackHeight = viewportHeight;

        int thumbHeight = Math.max(
                SCROLLBAR_MIN_THUMB,
                (int) ((float) viewportHeight / contentHeight * trackHeight)
        );

        int maxScroll = contentHeight - viewportHeight;
        int maxThumbTravel = trackHeight - thumbHeight;

        int thumbY = getY();

        if (maxThumbTravel > 0) {
            thumbY += (int) (
                    ((float) scrollY / maxScroll)
                            * maxThumbTravel
            );
        }

        return mouseX >= getX() + width - SCROLLBAR_SIZE
                && mouseX <= getX() + width
                && mouseY >= thumbY
                && mouseY <= thumbY + thumbHeight;
    }

    private boolean isOverHorizontalThumb(double mouseX, double mouseY) {

        int viewportWidth =
                width - lineNumberWidth - SCROLLBAR_SIZE;

        int contentWidth = getContentWidth();

        if (contentWidth <= viewportWidth) {
            return false;
        }

        int trackWidth = viewportWidth;

        int thumbWidth = Math.max(
                SCROLLBAR_MIN_THUMB,
                (int) ((float) viewportWidth / contentWidth * trackWidth)
        );

        int maxScroll = contentWidth - viewportWidth;
        int maxThumbTravel = trackWidth - thumbWidth;

        int thumbX = getX() + lineNumberWidth;

        if (maxThumbTravel > 0) {
            thumbX += (int) (
                    ((float) scrollX / maxScroll)
                            * maxThumbTravel
            );
        }

        return mouseX >= thumbX
                && mouseX <= thumbX + thumbWidth
                && mouseY >= getY() + height - SCROLLBAR_SIZE
                && mouseY <= getY() + height;
    }


}