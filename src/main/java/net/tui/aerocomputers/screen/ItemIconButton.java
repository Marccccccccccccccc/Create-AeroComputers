package net.tui.aerocomputers.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class ItemIconButton extends Button {

    private final ItemStack stack;

    public ItemIconButton(
            int x,
            int y,
            ItemStack stack,
            OnPress press
    ) {
        super(
                x,
                y,
                18,
                18,
                Component.empty(),
                press,
                DEFAULT_NARRATION
        );

        this.stack = stack;
    }


    @Override
    public void renderWidget(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);

        if (!stack.isEmpty()) {
            graphics.renderItem(
                    stack,
                    getX() + 1,
                    getY() + 1
            );
        }
    }
}