package net.tui.aerocomputers.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.tui.aerocomputers.AeroComputers;
import net.tui.aerocomputers.block.entity.BoardComputerBlockEntity;

public record BoardComputerEditPayload(BlockPos pos, String text)
        implements CustomPacketPayload {


    public static final Type<BoardComputerEditPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            AeroComputers.MOD_ID,
                            "board_computer_edit"
                    )
            );


    public static final StreamCodec<FriendlyByteBuf, BoardComputerEditPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    BoardComputerEditPayload::pos,
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                    BoardComputerEditPayload::text,
                    BoardComputerEditPayload::new
            );


    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void handle(IPayloadContext context) {

        context.enqueueWork(() -> {

            var player = context.player();

            if (player.level()
                    .getBlockEntity(pos)
                    instanceof BoardComputerBlockEntity be) {

                be.setProgram(text);

            }

        });

    }
}