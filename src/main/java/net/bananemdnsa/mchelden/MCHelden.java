package net.bananemdnsa.mchelden;

import com.mojang.logging.LogUtils;

import net.bananemdnsa.mchelden.bounty.BountyManager;
import net.bananemdnsa.mchelden.combat.CombatEvents;
import net.bananemdnsa.mchelden.combat.CombatLogout;
import net.bananemdnsa.mchelden.combat.ContainerLock;
import net.bananemdnsa.mchelden.combat.QuotaEvents;
import net.bananemdnsa.mchelden.grave.GraveEvents;
import net.bananemdnsa.mchelden.registry.MCHeldenBlockEntities;
import net.bananemdnsa.mchelden.registry.MCHeldenBlocks;
import net.bananemdnsa.mchelden.registry.MCHeldenMenus;
import net.bananemdnsa.mchelden.command.HeldenCommand;
import net.bananemdnsa.mchelden.hearts.Elimination;
import net.bananemdnsa.mchelden.hearts.HeartEvents;
import net.bananemdnsa.mchelden.hearts.HeartManager;
import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.playtime.PlaytimeTracker;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.world.ArenaCenter;
import net.bananemdnsa.mchelden.world.BorderController;
import net.bananemdnsa.mchelden.world.DividerWall;
import net.bananemdnsa.mchelden.world.SafeZone;
import net.bananemdnsa.mchelden.world.SpawnPlacer;
import net.bananemdnsa.mchelden.text.HeldenText;
import net.bananemdnsa.mchelden.text.StatusReport;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import org.slf4j.Logger;

@Mod(MCHelden.MODID)
public class MCHelden {
    public static final String MODID = "mchelden";
    public static final String NETWORK_VERSION = "1";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MCHelden(IEventBus modEventBus, ModContainer modContainer) {
        MCHeldenBlocks.register(modEventBus);
        MCHeldenBlockEntities.register(modEventBus);
        MCHeldenMenus.register(modEventBus);
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                MCHeldenConfig.SPEC);

        modEventBus.addListener(this::registerPayloads);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(HeartEvents::onRespawn);
        // Ganz zuletzt: die Safezone und die Trennwand sagen Angriffe auf der normalen
        // Stufe ab. Liefe der Combat-Handler wie jeder andere mit, stuende man in der
        // Safezone im Timer, ohne einen Kratzer abbekommen zu haben.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CombatEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(PlaytimeTracker::onLogout);
        NeoForge.EVENT_BUS.addListener(CombatEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ContainerLock::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ContainerLock::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(QuotaEvents::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(QuotaEvents::onBlockPlace);
        NeoForge.EVENT_BUS.addListener(QuotaEvents::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(GraveEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(GraveEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(DividerWall::onEntityTick);
        NeoForge.EVENT_BUS.addListener(DividerWall::onBreak);
        NeoForge.EVENT_BUS.addListener(DividerWall::onPlace);
        NeoForge.EVENT_BUS.addListener(DividerWall::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(DividerWall::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(DividerWall::onAttack);
        NeoForge.EVENT_BUS.addListener(SpawnPlacer::onRespawn);
        NeoForge.EVENT_BUS.addListener(SafeZone::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(SafeZone::onExplosion);
        NeoForge.EVENT_BUS.addListener(SafeZone::onPlace);
        NeoForge.EVENT_BUS.addListener(SafeZone::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SafeZone::onSpawn);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        NetworkHandler.register(event.registrar(NETWORK_VERSION));
    }

    private void registerCommands(RegisterCommandsEvent event) {
        HeldenCommand.register(event.getDispatcher());
    }

    /**
     * Setzt der Welt beim allerersten Start ihre Border.
     *
     * <p>Steht hier und nicht beim Weltwechsel: die Border gehoert zur Welt, nicht zu einer
     * Phase, und eine frische Welt haette sonst die Vanilla-Grenze von sechzig Millionen.
     */
    /**
     * Richtet eine frische Welt ein.
     *
     * <p>Die Reihenfolge ist nicht beliebig: beide erkennen an demselben Schalter, ob die
     * Welt neu ist, und {@link BorderController#initialise} legt ihn um. Die Mitte muss
     * deswegen zuerst stehen — sonst zoege die Border an den alten Fleck.
     */
    private void onServerStarted(ServerStartedEvent event) {
        ArenaCenter.initialise(event.getServer());
        BorderController.initialise(event.getServer());
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

        // Ausgeschieden: auf dem Server abweisen, im Einzelspieler als Zuschauer
        // hereinlassen. Ein Rauswurf aus der eigenen Welt fuehrt zu nichts.
        if (state.isEliminated()) {
            Elimination.remove(player.getServer(), player);
            if (!player.getServer().isSingleplayer()) {
                return;
            }
        }

        // Vor allem anderen: wer sein Tageskontingent aufgebraucht hat, kommt gar nicht
        // erst herein. Alles Weitere waere Arbeit fuer jemanden, der gleich wieder weg ist.
        if (PlaytimeTracker.onJoin(player.getServer(), player)) {
            return;
        }

        // Beim allerersten Join: Seite zuteilen und dorthin setzen. Danach nie wieder.
        SpawnPlacer.placeOnFirstJoin(player.getServer(), player);

        CombatLogout.deliverPendingRespawn(player);
        NetworkHandler.syncTo(player);

        // Wer sich im Kampf ausgeloggt hat, respawnt nie — er joint. Die vorgemerkte
        // Verlust-Anzeige braucht deswegen auch hier einen Abnehmer.
        HeartManager.deliverPendingLossDelayed(player.getUUID());

        // Und wer beim Bounty-Roll offline war, bekommt ihn jetzt nachgespielt.
        BountyManager.deliverPendingRollDelayed(player.getServer(), player.getUUID());

        // Zum Schluss der Statusblock: bei jedem Join, nicht nur beim ersten. Zwischen zwei
        // Sitzungen aendert sich der Zustand, und genau dann will man ihn wissen. Er steht
        // ganz am Ende, damit ein ausstehendes Gluecksrad sein Ziel noch verschweigen kann.
        StatusReport.send(player.getServer(), player);
    }
}
