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

public record BoardComputerChannelPayload(BlockPos pos, Direction direction, Action action, String id, String extra)
        implements CustomPacketPayload {

    public enum Direction { INPUT, OUTPUT }
    public enum Action { ADD, REMOVE, RENAME, SET_MODE, SET_FREQ_A, SET_FREQ_B }

    public static final Type<BoardComputerChannelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroComputers.MOD_ID, "board_computer_channel"));

    public static final StreamCodec<FriendlyByteBuf, BoardComputerChannelPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BoardComputerChannelPayload::pos,
                    ByteBufCodecs.idMapper(i -> Direction.values()[i], Direction::ordinal), BoardComputerChannelPayload::direction,
                    ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), BoardComputerChannelPayload::action,
                    ByteBufCodecs.STRING_UTF8, BoardComputerChannelPayload::id,
                    ByteBufCodecs.STRING_UTF8, BoardComputerChannelPayload::extra,
                    BoardComputerChannelPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player.level().getBlockEntity(pos) instanceof BoardComputerBlockEntity be)) return;

            boolean input = direction == Direction.INPUT;

            switch (action) {
                case ADD -> {
                    if (input) be.addInputChannel(); else be.addOutputChannel();
                }
                case REMOVE -> {
                    if (input) be.removeInputChannel(id); else be.removeOutputChannel(id);
                }
                case RENAME -> {
                    if (input) be.setInputChannelName(id, extra); else be.setOutputChannelName(id, extra);
                }
                case SET_MODE -> {
                    var mode = BoardComputerBlockEntity.SignalMode.valueOf(extra);
                    if (input) be.setInputChannelMode(id, mode); else be.setOutputChannelMode(id, mode);
                }
                case SET_FREQ_A, SET_FREQ_B -> {
                    var carried = player.containerMenu.getCarried();
                    boolean first = action == Action.SET_FREQ_A;
                    if (input) be.setInputChannelFrequency(id, first, carried);
                    else be.setOutputChannelFrequency(id, first, carried);
                }
            }
        });
    }
}