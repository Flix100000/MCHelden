package net.bananemdnsa.mchelden;

import net.bananemdnsa.mchelden.client.ClientState;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

// Wird auf dedizierten Servern nicht geladen. Client-Code ist hier sicher.
@Mod(value = MCHelden.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MCHelden.MODID, value = Dist.CLIENT)
public class MCHeldenClient {
    public MCHeldenClient(ModContainer container) {
    }

    /** Zustand beim Verlassen einer Welt leeren, damit nichts in die naechste Session leckt. */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
    }
}
