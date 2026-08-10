package net.tui.aerocomputers.screen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.tui.aerocomputers.AeroComputers;
import net.tui.aerocomputers.screen.custom.BoardComputerMenu;
import net.tui.aerocomputers.screen.custom.HudDisplayMenu;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, AeroComputers.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<BoardComputerMenu>> BOARDCOMPUTER_MENU =
            registerMenuType("boardcomputer_menu", BoardComputerMenu::new);

    private static <T extends AbstractContainerMenu>DeferredHolder<MenuType<?>, MenuType<T>> registerMenuType(String name, IContainerFactory<T> factory) {
        return MENUS.register(name, () -> IMenuTypeExtension.create(factory));
    }

    public static final DeferredHolder<MenuType<?>, MenuType<HudDisplayMenu>> HUDDISPLAY_MENU =
            registerMenuType("hud_diplay_menu", HudDisplayMenu::new);

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
