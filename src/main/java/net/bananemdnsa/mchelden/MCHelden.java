package net.bananemdnsa.mchelden;

import com.mojang.logging.LogUtils;

import net.bananemdnsa.mchelden.combat.CombatEvents;
import net.bananemdnsa.mchelden.combat.CombatLogout;
import net.bananemdnsa.mchelden.combat.ContainerLock;
import net.bananemdnsa.mchelden.combat.QuotaEvents;
import net.bananemdnsa.mchelden.command.HeldenCommand;
import net.bananemdnsa.mchelden.hearts.HeartEvents;
import net.bananemdnsa.mchelden.hearts.HeartManager;
import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import org.slf4j.Logger;

@Mod(MCHelden.MODID)
public class MCHelden {
    public static final String MODID = "mchelden";
    public static final String NETWORK_VERSION = "1";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MCHelden(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(HeartEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ContainerLock::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ContainerLock::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(QuotaEvents::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(QuotaEvents::onBlockPlace);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        NetworkHandler.register(event.registrar(NETWORK_VERSION));
    }

    private void registerCommands(RegisterCommandsEvent event) {
        HeldenCommand.register(event.getDispatcher());
    }

    /** Legt beim ersten Join den Zustand an, weist Ausgeschiedene ab, synct den Rest. */
    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) {
            return;
        }

        PlayerStateStore store = PlayerStateStore.get(player.getServer());
        PlayerState state = store.getOrCreate(player.getUUID());
        state.setName(player.getGameProfile().getName());
        store.setDirty();

        if (state.isEliminated()) {
            player.connection.disconnect(HeldenText.eliminationKick());
            return;
        }

        CombatLogout.deliverPendingRespawn(player);
        NetworkHandler.syncTo(player);

        // Wer sich im Kampf ausgeloggt hat, respawnt nie — er joint. Die vorgemerkte
        // Verlust-Anzeige braucht deswegen auch hier einen Abnehmer.
        HeartManager.deliverPendingLossDelayed(player.getUUID());
    }
}
