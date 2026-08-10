package net.tui.aerocomputers.item.custom;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.tui.aerocomputers.screen.custom.HudScreen;

public class HUDHelmetItem extends ArmorItem {

    public HUDHelmetItem(net.minecraft.core.Holder<net.minecraft.world.item.ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown() && level.isClientSide) {
            Minecraft.getInstance().setScreen(new net.tui.aerocomputers.screen.custom.HudScreen(hand));
        }

        return InteractionResultHolder.success(stack);
    }
}