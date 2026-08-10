package net.tui.aerocomputers.renderer;

import com.simibubi.create.content.kinetics.base.ShaftVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.tui.aerocomputers.block.entity.BoardComputerBlockEntity;

public class BoardComputerVisual extends ShaftVisual<BoardComputerBlockEntity> {
    public BoardComputerVisual(VisualizationContext context, BoardComputerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
    }
}
