package net.tui.aerocomputers.renderer;

import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.tui.aerocomputers.block.entity.BoardComputerBlockEntity;

public class BoardComputerRenderer extends ShaftRenderer<BoardComputerBlockEntity> {
    public BoardComputerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }
}
