package net.tui.aerocomputers.screen.custom;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.tui.aerocomputers.block.ModBlocks;
import net.tui.aerocomputers.block.entity.HudDisplayBlockEntity;
import net.tui.aerocomputers.screen.ModMenuTypes;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

public class HudDisplayMenu extends AbstractContainerMenu {
    public final HudDisplayBlockEntity blockEntity;
    private final Level level;

    public HudDisplayMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public HudDisplayMenu(int containerId, @UnknownNullability Inventory inv, BlockEntity blockEntity) {
        super(ModMenuTypes.HUDDISPLAY_MENU.get(), containerId);
        this.blockEntity = (HudDisplayBlockEntity) blockEntity;
        this.level = inv.player.level();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, ModBlocks.HUDDISPLAY.get());
    }
}
