package net.bananemdnsa.mchelden.registry;

import java.util.function.Supplier;

import net.bananemdnsa.mchelden.MCHelden;
import net.bananemdnsa.mchelden.grave.GraveMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MCHeldenMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MCHelden.MODID);

    /**
     * Wird mit Zusatzdaten geöffnet — Name, XP und Todeszeitpunkt reisen beim Öffnen mit,
     * weil sie in der Kopfzeile stehen und der Client sie sonst nicht kennt.
     */
    public static final Supplier<MenuType<GraveMenu>> GRAVE =
            MENUS.register("grave", () -> IMenuTypeExtension.create(GraveMenu::new));

    private MCHeldenMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
