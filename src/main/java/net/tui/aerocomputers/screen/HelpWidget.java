package net.tui.aerocomputers.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.tui.aerocomputers.block.entity.BoardComputerBlockEntity;

import java.util.List;
import java.util.Map;

public class HelpWidget extends AbstractWidget {

    private final CodeEditorWidget codeWidget;
    private record HelpEntry(String buttonKey, String textKey) {}

    private static final List<HelpEntry> HELP_ENTRIES = List.of(
            new HelpEntry("help.aero.basics", "help.aero.basics.text"),
            new HelpEntry("help.aero.variables", "help.aero.variables.text"),
            new HelpEntry("help.aero.operators", "help.aero.operators.text"),
            new HelpEntry("help.aero.channels", "help.aero.channels.text"),
            new HelpEntry("help.aero.memory", "help.aero.memory.text"),
            new HelpEntry("help.aero.def", "help.aero.def.text"),
            new HelpEntry("help.aero.while", "help.aero.while.text"),
            new HelpEntry("help.aero.for", "help.aero.for.text"),
            new HelpEntry("help.aero.if", "help.aero.if.text"),
            new HelpEntry("help.aero.class", "help.aero.class.text"),
            new HelpEntry("help.aero.label", "help.aero.label.text"),
            new HelpEntry("help.aero.line", "help.aero.line.text"),
            new HelpEntry("help.aero.rect", "help.aero.rect.text"),
            new HelpEntry("help.aero.poly", "help.aero.poly.text")
    );

    private int buttonScrollOffset = 0;

    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 2;

    private static final int BUTTON_LIST_X = 10;
    private static final int BUTTON_LIST_Y = 35;
    private static final int BUTTON_LIST_WIDTH = 90;

    private final List<net.minecraft.client.gui.components.Button> helpButtons = new java.util.ArrayList<>();

    public HelpWidget(int x, int y, int width, int height , BoardComputerBlockEntity.EditorCompletions completions, String selectedCategory) {
        super(x, y, width, height, Component.literal("Help"));

        codeWidget = new CodeEditorWidget(
                x + width/2 - 40,
                y + 10,
                width/2 + 30,
                height - 20,
                "",
                completions,
                this::setHelpText

        );

        codeWidget.setReadOnly(true);
        codeWidget.setFocused(false);

        setHelpText(selectedCategory);

        for (HelpEntry entry : HELP_ENTRIES) {
            Button button = Button.builder(
                    Component.translatable(entry.buttonKey()),
                    btn -> setHelpText(entry.textKey())
            ).bounds(
                    x + 10,
                    y,
                    BUTTON_WIDTH,
                    BUTTON_HEIGHT
            ).build();

            helpButtons.add(button);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        // Outside the popup -> don't consume the click
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }

        // Inside the button-list viewport
        if (isInsideButtonList(mouseX, mouseY)) {

            for (int i = 0; i < helpButtons.size(); i++) {
                Button helpButton = helpButtons.get(i);

                int buttonY = getButtonY(i);
                helpButton.setY(buttonY);

                // Only allow buttons that are actually inside the viewport
                if (buttonY + BUTTON_HEIGHT > getY() + BUTTON_LIST_Y
                        && buttonY < getY() + BUTTON_LIST_Y + (height-(10+BUTTON_LIST_Y))) {

                    if (helpButton.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }

            // Still consume the click even if it hit empty space
            return true;
        }

        // Consume clicks anywhere else inside the popup.
        // This prevents the GUI underneath from reacting.
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        return isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (!isInsideButtonList(mouseX, mouseY)) {
            return isMouseOver(mouseX, mouseY);
        }

        int visibleHeight = height - 10 - BUTTON_LIST_Y;

        int contentHeight =
                helpButtons.size() * (BUTTON_HEIGHT + BUTTON_SPACING);

        int maxScroll = Math.max(
                0,
                contentHeight - visibleHeight
        );

        buttonScrollOffset -= (int)(scrollY * 10);

        buttonScrollOffset = Mth.clamp(
                buttonScrollOffset,
                0,
                maxScroll
        );

        return true;
    }

    @Override
    protected void renderWidget(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);

        // Popup background
        guiGraphics.fill(
                getX(),
                getY(),
                getX() + width,
                getY() + height,
                0xFF202020
        );

        // Border
        guiGraphics.renderOutline(
                getX(),
                getY(),
                width,
                height,
                0xFFFFFFFF
        );

        // Title
        guiGraphics.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                "Help Menu",
                getX() + 10,
                getY() + 10,
                0xFFFFFFFF
        );

        // Render the read-only code editor
        codeWidget.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        // Button list clipping area
        guiGraphics.enableScissor(
                getX() + BUTTON_LIST_X,
                getY() + BUTTON_LIST_Y,
                getX() + BUTTON_LIST_X + BUTTON_LIST_WIDTH,
                getY() + BUTTON_LIST_Y + (height-(10+BUTTON_LIST_Y))
        );

        for (int i = 0; i < helpButtons.size(); i++) {
            Button button = helpButtons.get(i);

            int buttonY = getButtonY(i);
            button.setY(buttonY);

            button.render(
                    guiGraphics,
                    mouseX,
                    mouseY,
                    partialTick
            );
        }

        guiGraphics.disableScissor();

        guiGraphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(
            NarrationElementOutput narrationElementOutput
    ) {
    }

    private void setHelpText(String translationKey) {
        String text = Component.translatable(translationKey).getString();

        codeWidget.setText(text);
        codeWidget.setFocused(false);
    }

    private int getButtonY(int index) {
        return getY()
                + BUTTON_LIST_Y
                + index * (BUTTON_HEIGHT + BUTTON_SPACING)
                - buttonScrollOffset;
    }

    private boolean isInsideButtonList(double mouseX, double mouseY) {
        return mouseX >= getX() + BUTTON_LIST_X
                && mouseX < getX() + BUTTON_LIST_X + BUTTON_LIST_WIDTH
                && mouseY >= getY() + BUTTON_LIST_Y
                && mouseY < getY() + BUTTON_LIST_Y + (height-(10+BUTTON_LIST_Y));
    }
}