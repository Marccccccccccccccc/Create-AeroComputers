package net.tui.aerocomputers.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.tui.aerocomputers.AeroComputers;
import net.tui.aerocomputers.block.entity.BoardComputerBlockEntity;

public record BoardComputerControlPayload(
        BlockPos pos,
        Action action,
        String boardName
) implements CustomPacketPayload {

    public enum Action {
        PLAY,
        PAUSE,
        RESTART,
        NAME
    }

    public static final Type<BoardComputerControlPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            AeroComputers.MOD_ID,
                            "board_computer_control"
                    )
            );

    public static final StreamCodec<FriendlyByteBuf, BoardComputerControlPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    BoardComputerControlPayload::pos,

                    ByteBufCodecs.idMapper(
                            i -> Action.values()[i],
                            Action::ordinal
                    ),
                    BoardComputerControlPayload::action,

                    ByteBufCodecs.STRING_UTF8,
                    BoardComputerControlPayload::boardName,

                    BoardComputerControlPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {

            var player = context.player();

            if (player.level().getBlockEntity(pos)
                    instanceof BoardComputerBlockEntity be) {

                switch (action) {
                    case PLAY -> be.setRunning(true);

                    case PAUSE -> be.setRunning(false);

                    case RESTART -> be.restart();

                    case NAME -> be.setComputerName(boardName);
                }
            }
        });
    }
}