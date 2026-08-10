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

public record BoardComputerVariablePayload(BlockPos pos, Action action, String name, String extra)
        implements CustomPacketPayload {

    public enum Action { ADD, REMOVE, RENAME, SET_VALUE }

    public static final Type<BoardComputerVariablePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroComputers.MOD_ID, "board_computer_variable"));

    public static final StreamCodec<FriendlyByteBuf, BoardComputerVariablePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BoardComputerVariablePayload::pos,
                    ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal), BoardComputerVariablePayload::action,
                    ByteBufCodecs.STRING_UTF8, BoardComputerVariablePayload::name,
                    ByteBufCodecs.STRING_UTF8, BoardComputerVariablePayload::extra,
                    BoardComputerVariablePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.level().getBlockEntity(pos) instanceof BoardComputerBlockEntity be) {
                switch (action) {
                    case ADD -> be.addPersistentVariable(name);
                    case REMOVE -> be.removePersistentVariable(name);
                    case RENAME -> be.renamePersistentVariable(name, extra);
                    case SET_VALUE -> {
                        try {
                            be.setPersistentVariableValue(name, Double.parseDouble(extra));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        });
    }
}