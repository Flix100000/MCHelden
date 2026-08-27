package net.bananemdnsa.mchelden.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.bananemdnsa.mchelden.bounty.BountyManager;
import net.bananemdnsa.mchelden.grave.GraveBlockEntity;
import net.bananemdnsa.mchelden.grave.GraveRegistry;
import net.bananemdnsa.mchelden.hearts.Elimination;
import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Der {@code reset}-Teilbaum.
 *
 * <p>Eigene Datei, weil {@code HeldenCommand} mit diesen Befehlen ueber 750 Zeilen kaeme.
 * Der Schnitt liegt an einer Naht, die es ohnehin gibt: Resets sind Korrekturen und
 * gehoeren nicht zu den Befehlen, mit denen man spielt.
 *
 * <p><b>Resets sind still.</b> Das Ergebnis sieht nur der ausfuehrende Op — deswegen
 * ueberall {@code sendSuccess(..., false)}. Ein Reset ist eine Korrektur, keine Ansage, und
 * {@code reset hearts <spieler>} oeffentlich zu machen stellt jemanden bloss. Wen es
 * betrifft, merkt es ohnehin am eigenen Zustand.
 *
 * <p><b>Ohne Spielerangabe gilt jeder Zweig global.</b> So steht es als Prinzip in
 * Spec-Abschnitt 13: alles, was einen Einzelnen betreffen kann, nimmt einen Spieler
 * entgegen.
 */
public final class ResetCommand {

    private ResetCommand() {
    }

    /** Haengt sich unter {@code /helden} ein. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("reset")
                .then(branch("hearts", ResetCommand::hearts))
                .then(branch("bounty", ResetCommand::bounty))
                .then(branch("time", ResetCommand::time))
                .then(branch("graves", ResetCommand::graves));
    }

    /** Ein Zweig mit optionalem Spielerziel. Ohne Argument global. */
    private static LiteralArgumentBuilder<CommandSourceStack> branch(String name, Reset reset) {
        return Commands.literal(name)
                .executes(context -> reset.apply(context.getSource(), null))
                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                        .executes(context -> reset.apply(
                                context.getSource(),
                                GameProfileArgument.getGameProfiles(context, "spieler"))));
    }

    @FunctionalInterface
    private interface Reset {
        int apply(CommandSourceStack source, @Nullable Collection<GameProfile> profiles);
    }

    /**
     * Setzt auf drei Herzen und hebt die Elimination auf.
     *
     * <p>Drei Herzen bei gleichzeitig „ausgeschieden" waere ein Zustand, den das Spiel
     * nicht kennt. Vier sind nur ueber den Bounty-Kill zu haben — ein Reset vergibt sie
     * deswegen nicht.
     */
    private static int hearts(CommandSourceStack source,
                              @Nullable Collection<GameProfile> profiles) {
        MinecraftServer server = source.getServer();
        List<UUID> targets = resolve(server, profiles);

        for (UUID uuid : targets) {
            Elimination.revive(server, uuid, PlayerState.DEFAULT_HEARTS);
        }

        String who = describe(profiles);
        source.sendSuccess(() -> HeldenText.resetHearts(who), false);
        return targets.size();
    }

    /** Loest wie {@code bounty clear} immer beide Seiten der Paarung auf. */
    private static int bounty(CommandSourceStack source,
                              @Nullable Collection<GameProfile> profiles) {
        MinecraftServer server = source.getServer();
        List<UUID> targets = resolve(server, profiles);

        for (UUID uuid : targets) {
            BountyManager.clear(server, uuid);
        }

        String who = describe(profiles);
        source.sendSuccess(() -> HeldenText.resetBounty(who), false);
        return targets.size();
    }

    private static int time(CommandSourceStack source,
                            @Nullable Collection<GameProfile> profiles) {
        MinecraftServer server = source.getServer();
        PlayerStateStore store = PlayerStateStore.get(server);
        List<UUID> targets = resolve(server, profiles);

        for (UUID uuid : targets) {
            PlayerState state = store.find(uuid);
            if (state == null) {
                continue;
            }

            state.setPlaytimeUsedSeconds(0);
            store.setDirty();
            sync(server, uuid);
        }

        String who = describe(profiles);
        source.sendSuccess(() -> HeldenText.resetTime(who), false);
        return targets.size();
    }

    /**
     * Raeumt Graeber ab.
     *
     * <p>Der Inhalt faellt dabei <b>nicht</b> heraus: {@link GraveBlockEntity#discard}
     * leert Kiste und XP, bevor der Block verschwindet. Ein Reset ist keine Auszahlung.
     *
     * <p>Eintraege, deren Block schon weg ist, werden trotzdem ausgetragen — so heilt sich
     * das Verzeichnis selbst.
     *
     * <p>{@code getBlockEntity} laedt den Chunk bei Bedarf nach. Das ist hier gewollt: ein
     * Grab in einem ungeladenen Chunk ist genau der Fall, fuer den es das Verzeichnis gibt.
     */
    private static int graves(CommandSourceStack source,
                              @Nullable Collection<GameProfile> profiles) {
        MinecraftServer server = source.getServer();
        int removed = 0;

        for (UUID uuid : resolve(server, profiles)) {
            removed += clearGraves(server, GraveRegistry.get(server).of(uuid));
        }

        String who = describe(profiles);
        int total = removed;
        source.sendSuccess(() -> HeldenText.resetGraves(who, total), false);
        return total;
    }

    /** Entfernt die genannten Graeber und traegt sie aus. Gibt zurueck, wie viele es waren. */
    static int clearGraves(MinecraftServer server, List<BlockPos> positions) {
        ServerLevel level = server.overworld();
        GraveRegistry registry = GraveRegistry.get(server);

        for (BlockPos pos : positions) {
            if (level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
                grave.discard();
                level.removeBlock(pos, false);
            }
            registry.remove(pos);
        }

        return positions.size();
    }

    /** Ohne Spielerangabe alle bekannten Spieler, sonst die genannten. */
    private static List<UUID> resolve(MinecraftServer server,
                                      @Nullable Collection<GameProfile> profiles) {
        List<UUID> ids = new ArrayList<>();

        if (profiles != null) {
            for (GameProfile profile : profiles) {
                ids.add(profile.getId());
            }
            return ids;
        }

        for (PlayerState state : PlayerStateStore.get(server).all()) {
            ids.add(state.getUuid());
        }
        return ids;
    }

    /** Wen der Reset getroffen hat, fuer die Meldung an den Op. */
    private static String describe(@Nullable Collection<GameProfile> profiles) {
        if (profiles == null) {
            return HeldenText.resetEveryone().getString();
        }

        StringBuilder names = new StringBuilder();
        for (GameProfile profile : profiles) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(profile.getName() != null ? profile.getName() : profile.getId());
        }
        return names.toString();
    }

    /**
     * Schickt einem Spieler seinen Zustand neu, falls er online ist.
     *
     * <p>Nur beim Zeit-Reset noetig: {@code Elimination.revive} und
     * {@code BountyManager.clear} synchronisieren selbst.
     */
    private static void sync(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            NetworkHandler.syncTo(player);
        }
    }
}
