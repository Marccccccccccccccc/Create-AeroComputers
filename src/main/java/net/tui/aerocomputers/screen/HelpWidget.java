package net.tui.aerocomputers.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.tui.aerocomputers.block.entity.BoardComputerBlockEntity;

public class HelpWidget extends AbstractWidget {

    private final CodeEditorWidget codeWidget;

    public HelpWidget(int x, int y, int width, int height , BoardComputerBlockEntity.EditorCompletions completions) {
        super(x, y, width, height, Component.literal("Help"));

        codeWidget = new CodeEditorWidget(
                x + 10,
                y + 50,
                width - 20,
                height - 60,
                "print(\"Hello\")",
                completions
        );

        codeWidget.setReadOnly(true);
        codeWidget.setFocused(false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Consume clicks anywhere inside the popup.
        // This prevents the GUI underneath from reacting.
        return isMouseOver(mouseX, mouseY);
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
        return isMouseOver(mouseX, mouseY);
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
                "Help",
                getX() + 10,
                getY() + 10,
                0xFFFFFFFF
        );

        // Help text
        guiGraphics.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
                "This is the help menu!",
                getX() + 10,
                getY() + 30,
                0xFFFFFFFF
        );

        // Render the read-only code editor
        codeWidget.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        guiGraphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(
            NarrationElementOutput narrationElementOutput
    ) {
    }
}