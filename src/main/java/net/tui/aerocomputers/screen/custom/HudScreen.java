package net.tui.aerocomputers.screen.custom;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import net.tui.aerocomputers.AeroComputers;
import net.tui.aerocomputers.network.HudEntryPayload;

public class HudScreen extends Screen {
    private final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(AeroComputers.MOD_ID, "textures/gui/hud/hud_gui.png");

    private final InteractionHand hand;
    private EditBox entryBox;

    public HudScreen(InteractionHand hand) {
        super(Component.literal("Set Board Computer"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        super.init();

        int boxWidth = 100;
        int boxHeight = 14;
        int x = (width - boxWidth) / 2;
        int y = (height - boxHeight) / 2;

        entryBox = new EditBox(font, x, y, boxWidth, boxHeight, Component.empty());
        entryBox.setValue(readCurrentValue());
        addRenderableWidget(entryBox);
        setInitialFocus(entryBox);
    }

    private String readCurrentValue() {
        // client-side read is fine here — this only prefills the box,
        // it never gets written back directly
        ItemStack stack = Minecraft.getInstance().player.getItemInHand(hand);
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        return tag.contains(HudEntryPayload.NBT_KEY) ? tag.getString(HudEntryPayload.NBT_KEY) : "";
    }

    @Override
    public void removed() {
        PacketDistributor.sendToServer(new HudEntryPayload(hand, entryBox.getValue()));
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int imageWidth = 120;
        int imageHeight = 30;
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(
                GUI_TEXTURE, x, y,
                0, 0, imageWidth, imageHeight,
                imageWidth, imageHeight
        );
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}