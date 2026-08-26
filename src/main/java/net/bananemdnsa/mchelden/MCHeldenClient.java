package net.bananemdnsa.mchelden;

import net.bananemdnsa.mchelden.client.ClientState;
import net.bananemdnsa.mchelden.client.hud.BountyHud;
import net.bananemdnsa.mchelden.client.hud.BountyRollOverlay;
import net.bananemdnsa.mchelden.client.hud.CombatHud;
import net.bananemdnsa.mchelden.client.hud.HeartHud;
import net.bananemdnsa.mchelden.client.hud.HeartLossOverlay;
import net.bananemdnsa.mchelden.client.hud.QuotaHud;
import net.bananemdnsa.mchelden.client.render.GraveRenderer;
import net.bananemdnsa.mchelden.client.screen.GraveScreen;
import net.bananemdnsa.mchelden.registry.MCHeldenBlockEntities;
import net.bananemdnsa.mchelden.registry.MCHeldenMenus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// Wird auf dedizierten Servern nicht geladen. Client-Code ist hier sicher.
// Der passende Event-Bus wird am Event-Typ erkannt, deswegen ohne bus-Angabe.
@Mod(value = MCHelden.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MCHelden.MODID, value = Dist.CLIENT)
public class MCHeldenClient {
    public MCHeldenClient(ModContainer container) {
    }

    @SubscribeEvent
    static void registerScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(MCHeldenMenus.GRAVE.get(), GraveScreen::new);
    }

    @SubscribeEvent
    static void registerRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MCHeldenBlockEntities.GRAVE.get(), GraveRenderer::new);
    }

    @SubscribeEvent
    static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, HeartHud.LAYER_ID, HeartHud::render);
        event.registerAbove(HeartHud.LAYER_ID, CombatHud.LAYER_ID, CombatHud::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, QuotaHud.LAYER_ID, QuotaHud::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, BountyHud.LAYER_ID, BountyHud::render);
        event.registerAboveAll(HeartLossOverlay.LAYER_ID, HeartLossOverlay::render);
        event.registerAboveAll(BountyRollOverlay.LAYER_ID, BountyRollOverlay::render);
    }

    @SubscribeEvent
    static void onScreenRender(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        QuotaHud.onScreenRender(event);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientState.tick();
    }

    /** Zustand beim Verlassen einer Welt leeren, damit nichts in die naechste Session leckt. */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
    }
}
