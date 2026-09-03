package net.bananemdnsa.mchelden.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.bananemdnsa.mchelden.bounty.BountyManager;
import net.bananemdnsa.mchelden.combat.CombatTracker;
import net.bananemdnsa.mchelden.combat.ItemQuota;
import net.bananemdnsa.mchelden.duel.DuelManager;
import net.bananemdnsa.mchelden.event.EventManager;
import net.bananemdnsa.mchelden.event.EventType;
import net.bananemdnsa.mchelden.hearts.Elimination;
import net.bananemdnsa.mchelden.phase.PhaseManager;
import net.bananemdnsa.mchelden.playtime.PlaytimeTracker;
import net.bananemdnsa.mchelden.hearts.HeartManager;
import net.bananemdnsa.mchelden.network.NetworkHandler;
import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.state.Side;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;
import net.bananemdnsa.mchelden.text.DurationText;
import net.bananemdnsa.mchelden.text.HeldenText;
import net.bananemdnsa.mchelden.world.ArenaCenter;
import net.bananemdnsa.mchelden.world.BorderController;
import net.bananemdnsa.mchelden.world.BorderStorm;
import net.bananemdnsa.mchelden.world.DividerWall;
import net.bananemdnsa.mchelden.world.SafeZone;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.border.BorderStatus;
import net.minecraft.world.level.border.WorldBorder;

public final class HeldenCommand {
    /** Obergrenze fuer die Zeit-Commands. Zehn Stunden sind reichlich und fangen Vertipper ab. */
    private static final int MAX_TIME_MINUTES = 600;

    private HeldenCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("helden")
                .requires(HeldenPermission.root())
                .then(Commands.literal("info")
                        .requires(HeldenPermission.INFO::granted)
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(context -> info(
                                        context.getSource(),
                                        GameProfileArgument.getGameProfiles(context, "spieler")))))
                .then(Commands.literal("heart")
                        .requires(HeldenPermission.HEART::granted)
                        .then(heartDeltaBranch("give", 1))
                        .then(heartDeltaBranch("remove", -1))
                        .then(Commands.literal("set")
                                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("anzahl",
                                                        IntegerArgumentType.integer(0, PlayerState.MAX_HEARTS))
                                                .executes(context -> heartSet(
                                                        context.getSource(),
                                                        GameProfileArgument.getGameProfiles(context, "spieler"),
                                                        IntegerArgumentType.getInteger(context, "anzahl")))))))
                .then(Commands.literal("revive")
                        .requires(HeldenPermission.REVIVE::granted)
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(context -> revive(
                                        context.getSource(),
                                        GameProfileArgument.getGameProfiles(context, "spieler"),
                                        PlayerState.DEFAULT_HEARTS))
                                .then(Commands.argument("herzen",
                                                IntegerArgumentType.integer(1, PlayerState.MAX_HEARTS))
                                        .executes(context -> revive(
                                                context.getSource(),
                                                GameProfileArgument.getGameProfiles(context, "spieler"),
                                                IntegerArgumentType.getInteger(context, "herzen"))))))
                .then(Commands.literal("combat")
                        .requires(HeldenPermission.COMBAT::granted)
                        .then(Commands.literal("clear")
                                .then(Commands.argument("spieler", EntityArgument.players())
                                        .executes(context -> combatClear(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "spieler"))))))
                .then(Commands.literal("duell")
                        .requires(HeldenPermission.DUELL::granted)
                        .then(Commands.literal("clear")
                                .then(Commands.argument("spieler", EntityArgument.players())
                                        .executes(context -> duelClear(
                                                context.getSource(),
                                                EntityArgument.getPlayers(context, "spieler"))))))
                .then(Commands.literal("bounty")
                        .requires(HeldenPermission.branch("bounty"))
                        .then(Commands.literal("roll")
                                .requires(HeldenPermission.BOUNTY_ROLL::granted)
                                .executes(context -> bountyRoll(context.getSource())))
                        .then(Commands.literal("show")
                                .requires(HeldenPermission.BOUNTY_SHOW::granted)
                                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                        .executes(context -> bountyShow(
                                                context.getSource(),
                                                GameProfileArgument.getGameProfiles(context, "spieler")))))
                        .then(Commands.literal("set")
                                .requires(HeldenPermission.BOUNTY_SET::granted)
                                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("ziel", GameProfileArgument.gameProfile())
                                                .executes(context -> bountySet(
                                                        context.getSource(),
                                                        GameProfileArgument.getGameProfiles(context, "spieler"),
                                                        GameProfileArgument.getGameProfiles(context, "ziel"))))))
                        .then(Commands.literal("clear")
                                .requires(HeldenPermission.BOUNTY_CLEAR::granted)
                                .executes(context -> bountyClearAll(context.getSource()))
                                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                        .executes(context -> bountyClear(
                                                context.getSource(),
                                                GameProfileArgument.getGameProfiles(context, "spieler"))))))
                .then(Commands.literal("debug")
                        .requires(HeldenPermission.DEBUG::granted)
                        .then(Commands.literal("combat")
                                .executes(context -> debugCombat(context.getSource())))
                        .then(Commands.literal("bounty")
                                .executes(context -> debugBounty(context.getSource(), null))
                                .then(Commands.argument("ziel", GameProfileArgument.gameProfile())
                                        .executes(context -> debugBounty(
                                                context.getSource(),
                                                GameProfileArgument.getGameProfiles(context, "ziel")))))
                        .then(Commands.literal("duell")
                                .executes(context -> debugDuel(context.getSource(), null))
                                .then(Commands.argument("ziel", EntityArgument.player())
                                        .executes(context -> debugDuel(
                                                context.getSource(),
                                                EntityArgument.getPlayer(context, "ziel")))))
                        .then(Commands.literal("quota")
                                .executes(context -> debugQuota(context.getSource())))
                        .then(Commands.literal("playtime")
                                .executes(context -> debugPlaytime(context.getSource())))
                        .then(Commands.literal("render")
                                .executes(context -> debugRender(context.getSource())))
                        .then(Commands.literal("border")
                                .executes(context -> debugBorder(context.getSource())))
                        .then(Commands.literal("respawn")
                                .executes(context -> debugRespawn(context.getSource())))
                        .then(Commands.literal("death")
                                .executes(context -> debugDeath(context.getSource())))
                        .then(Commands.literal("animation")
                                .executes(context -> debugAnimation(context.getSource()))))
                .then(Commands.literal("phase")
                        .requires(HeldenPermission.branch("phase"))
                        .then(Commands.literal("info")
                                .requires(HeldenPermission.PHASE_INFO::granted)
                                .executes(context -> phaseInfo(context.getSource())))
                        .then(Commands.literal("next")
                                .requires(HeldenPermission.PHASE_NEXT::granted)
                                .executes(context -> phaseNext(context.getSource())))
                        .then(Commands.literal("set")
                                .requires(HeldenPermission.PHASE_SET::granted)
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Phase.values()).map(Phase::getId), builder))
                                        .executes(context -> phaseSet(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "phase"))))))
                .then(Commands.literal("event")
                        .requires(HeldenPermission.branch("event"))
                        // Literale vor Argumenten: `stop` und `info` landen verlaesslich
                        // hier und nicht im Eventnamen. EventType.RESERVED haelt fest,
                        // dass kein Event so heissen darf.
                        .then(Commands.literal("stop")
                                .requires(HeldenPermission.EVENT_RUN::granted)
                                .executes(context -> eventStop(context.getSource())))
                        .then(Commands.literal("info")
                                .requires(HeldenPermission.EVENT_INFO::granted)
                                .executes(context -> eventInfo(context.getSource())))
                        .then(Commands.argument("typ", StringArgumentType.word())
                                .requires(HeldenPermission.EVENT_RUN::granted)
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(EventType.values()).map(EventType::getId),
                                        builder))
                                .then(Commands.argument("dauer", StringArgumentType.word())
                                        .executes(context -> eventStart(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "typ"),
                                                StringArgumentType.getString(context, "dauer"))))))
                .then(Commands.literal("wall")
                        .requires(HeldenPermission.WALL::granted)
                        .then(Commands.literal("drop")
                                .executes(context -> wall(context.getSource(), false)))
                        .then(Commands.literal("raise")
                                .executes(context -> wall(context.getSource(), true))))
                .then(Commands.literal("finalwar")
                        .requires(HeldenPermission.FINALWAR::granted)
                        .then(Commands.literal("start")
                                .executes(context -> finalWarStart(context.getSource(), null))
                                .then(Commands.argument("dauer", StringArgumentType.word())
                                        .executes(context -> finalWarStart(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "dauer")))))
                        .then(Commands.literal("stop")
                                .executes(context -> finalWarStop(context.getSource()))))
                .then(Commands.literal("border")
                        .requires(HeldenPermission.BORDER::granted)
                        .then(Commands.literal("shrink")
                                .then(Commands.argument("groesse", IntegerArgumentType.integer(
                                                16, (int) BorderController.START_SIZE))
                                        .then(Commands.argument("dauer", StringArgumentType.word())
                                                .executes(context -> borderShrink(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "groesse"),
                                                        StringArgumentType.getString(context, "dauer"))))))
                        .then(Commands.literal("reset")
                                .executes(context -> borderReset(context.getSource()))))
                // Die argumentlose Form zeigt die Mitte nur an und haengt deswegen an der
                // Pruefung des Zweiges: wer sie verschieben darf, darf sie auch lesen.
                // Umgekehrt gilt das nicht, die drei schreibenden Formen fragen `center.set`.
                .then(Commands.literal("center")
                        .requires(HeldenPermission.branch("center"))
                        .executes(context -> centerShow(context.getSource()))
                        .then(Commands.literal("here")
                                .requires(HeldenPermission.CENTER_SET::granted)
                                .executes(context -> centerHere(context.getSource())))
                        .then(Commands.literal("reset")
                                .requires(HeldenPermission.CENTER_SET::granted)
                                .executes(context -> centerReset(context.getSource())))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .requires(HeldenPermission.CENTER_SET::granted)
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(context -> centerMove(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "x"),
                                                IntegerArgumentType.getInteger(context, "z"))))))
                .then(GraveCommand.build())
                .then(ResetCommand.build())
                .then(Commands.literal("time")
                        .requires(HeldenPermission.branch("time"))
                        .then(Commands.literal("check")
                                .requires(HeldenPermission.TIME_CHECK::granted)
                                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                        .executes(context -> timeCheck(
                                                context.getSource(),
                                                GameProfileArgument.getGameProfiles(context, "spieler")))))
                        .then(Commands.literal("add")
                                .requires(HeldenPermission.TIME_ADD::granted)
                                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("minuten",
                                                        IntegerArgumentType.integer(-MAX_TIME_MINUTES,
                                                                MAX_TIME_MINUTES))
                                                .executes(context -> timeAdd(
                                                        context.getSource(),
                                                        GameProfileArgument.getGameProfiles(context, "spieler"),
                                                        IntegerArgumentType.getInteger(context, "minuten"))))))
                        .then(Commands.literal("set")
                                .requires(HeldenPermission.TIME_SET::granted)
                                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("minuten",
                                                        IntegerArgumentType.integer(0, MAX_TIME_MINUTES))
                                                .executes(context -> timeSet(
                                                        context.getSource(),
                                                        GameProfileArgument.getGameProfiles(context, "spieler"),
                                                        IntegerArgumentType.getInteger(context, "minuten"))))))));
    }

    private static int info(CommandSourceStack source, Collection<GameProfile> profiles) {
        PlayerStateStore store = PlayerStateStore.get(source.getServer());
        for (GameProfile profile : profiles) {
            printPlayer(source, store, profile);
        }
        return profiles.size();
    }

    private static void printPlayer(CommandSourceStack source, PlayerStateStore store, GameProfile profile) {
        PlayerState state = store.find(profile.getId());

        source.sendSuccess(() -> Component.literal(nameOf(profile)).withStyle(ChatFormatting.GOLD), false);

        if (state == null) {
            source.sendSuccess(HeldenText::infoUnknown, false);
            return;
        }

        source.sendSuccess(() -> HeldenText.infoLine("mchelden.command.info.hearts",
                heartsValue(state.getHearts())), false);
        source.sendSuccess(() -> HeldenText.infoLine("mchelden.command.info.bounty",
                bountyValue(store, state)), false);
        source.sendSuccess(() -> HeldenText.infoLine("mchelden.command.info.playtime",
                playtimeValue(source.getServer(), state)), false);
        source.sendSuccess(() -> HeldenText.infoLine("mchelden.command.info.combat",
                combatValue(state.getUuid())), false);
        source.sendSuccess(() -> HeldenText.infoLine("mchelden.command.info.duel",
                duelValue(source.getServer(), state.getUuid())), false);
        source.sendSuccess(() -> HeldenText.infoLine("mchelden.command.info.status",
                state.isEliminated() ? HeldenText.statusEliminated() : HeldenText.statusActive()), false);
    }

    /**
     * Die Spielzeit-Zeile.
     *
     * <p>Sagt ausdruecklich, wenn gar kein Limit gilt — im Einzelspieler oder ausserhalb
     * der Aufbauphase. Eine Restzeit, die sich nie aendert, sieht sonst aus wie ein
     * stehengebliebener Zaehler; genau so ist der Fall beim Testen zuerst aufgefallen.
     */
    private static Component playtimeValue(MinecraftServer server, PlayerState state) {
        Component left = HeldenText.playtimeLeft(
                formatDuration(PlaytimeTracker.remainingSeconds(state.getPlaytimeUsedSeconds())));

        ServerPlayer online = server.getPlayerList().getPlayer(state.getUuid());
        return online != null && !PlaytimeTracker.isLimited(server, online)
                ? left.copy().append(" ").append(HeldenText.playtimeExempt())
                : left;
    }

    /**
     * Der laufende Combat-Timer, oder ein Strich.
     *
     * <p>Der letzte der fuenf Werte aus Spec-Abschnitt 13, und beim Nachfragen im Discord
     * der wichtigste: an ihm haengen GUI-Sperre, Safezone-Zutritt und die Frage, ob ein
     * Logout als Tod zaehlt.
     */
    private static Component combatValue(UUID uuid) {
        int ticks = CombatTracker.remainingTicks(uuid);
        return ticks <= 0
                ? HeldenText.infoNone()
                : Component.literal(formatDuration(ticks / 20)).withStyle(ChatFormatting.RED);
    }

    /** Duellgegner und Restzeit, oder "keins". */
    private static Component duelValue(MinecraftServer server, UUID uuid) {
        UUID partner = DuelManager.partnerOf(uuid);
        if (partner == null) {
            return HeldenText.duelNone();
        }

        PlayerState partnerState = PlayerStateStore.get(server).find(partner);
        return HeldenText.duelValue(partnerState != null ? partnerState.getName() : "",
                Component.literal(formatDuration(DuelManager.remainingTicks(uuid) / 20)));
    }

    private static Component heartsValue(int hearts) {
        return Component.literal(hearts + " / " + PlayerState.MAX_HEARTS)
                .withStyle(hearts == 0 ? ChatFormatting.RED : ChatFormatting.WHITE);
    }

    private static Component bountyValue(PlayerStateStore store, PlayerState state) {
        UUID target = state.getBountyTarget();
        if (target == null) {
            return state.isBountyResolved() ? HeldenText.bountyResolved() : HeldenText.bountyNone();
        }

        PlayerState targetState = store.find(target);
        String name = targetState != null && !targetState.getName().isEmpty()
                ? targetState.getName()
                : target.toString();
        return Component.literal(name).withStyle(ChatFormatting.WHITE);
    }

    /** Baut den give- bzw. remove-Zweig. Beide unterscheiden sich nur im Vorzeichen. */
    private static LiteralArgumentBuilder<CommandSourceStack> heartDeltaBranch(String name, int sign) {
        return Commands.literal(name)
                .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                        .executes(context -> heartDelta(
                                context.getSource(),
                                GameProfileArgument.getGameProfiles(context, "spieler"),
                                sign))
                        .then(Commands.argument("anzahl",
                                        IntegerArgumentType.integer(1, PlayerState.MAX_HEARTS))
                                .executes(context -> heartDelta(
                                        context.getSource(),
                                        GameProfileArgument.getGameProfiles(context, "spieler"),
                                        sign * IntegerArgumentType.getInteger(context, "anzahl")))));
    }

    private static int heartDelta(CommandSourceStack source, Collection<GameProfile> profiles, int delta) {
        MinecraftServer server = source.getServer();
        for (GameProfile profile : profiles) {
            report(source, profile, HeartManager.add(server, profile.getId(), delta, ""));
        }
        return profiles.size();
    }

    private static int heartSet(CommandSourceStack source, Collection<GameProfile> profiles, int hearts) {
        MinecraftServer server = source.getServer();
        for (GameProfile profile : profiles) {
            report(source, profile, HeartManager.set(server, profile.getId(), hearts, ""));
        }
        return profiles.size();
    }

    private static int revive(CommandSourceStack source, Collection<GameProfile> profiles, int hearts) {
        MinecraftServer server = source.getServer();
        for (GameProfile profile : profiles) {
            Elimination.revive(server, profile.getId(), hearts);
            source.sendSuccess(() -> HeldenText.revived(nameOf(profile), hearts), true);
        }
        return profiles.size();
    }

    private static void report(CommandSourceStack source, GameProfile profile, int hearts) {
        source.sendSuccess(() -> Component.literal(nameOf(profile) + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(heartsValue(hearts)), true);
    }

    /**
     * Stellt einen Tod durch einen Spieler nach — mit Todesbildschirm, Respawn und der
     * dort nachgeholten Animation. Prüft also den kompletten Ablauf, nicht nur die Optik.
     *
     * <p>Erst töten, dann das Herz abziehen: nur in dieser Reihenfolge ist der Spieler beim
     * Abzug bereits tot, und die Anzeige wird wie im Ernstfall bis zum Respawn vorgemerkt.
     */
    private static int debugDeath(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        HeartManager.loseHeart(source.getServer(), player.getUUID(), "Debug");

        source.sendSuccess(() -> Component.literal("Spielertod nachgestellt — Animation folgt beim Respawn")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /**
     * Spielt nur den Effekt ab, ohne etwas am Zustand zu ändern. Zum Abstimmen der Optik,
     * ohne jedes Mal zu sterben.
     *
     * <p>Der Herzstand bleibt dabei unverändert, die HUD-Scherbe erscheint deswegen an dem
     * Slot hinter dem letzten vollen Herz. Für das grosse Overlay spielt das keine Rolle.
     */
    private static int debugAnimation(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        NetworkHandler.sendHeartLost(player, HeartManager.get(source.getServer(), player.getUUID()));

        source.sendSuccess(() -> Component.literal("Effekt abgespielt — Herzstand unverändert")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int bountyRoll(CommandSourceStack source) {
        int pairs = BountyManager.roll(source.getServer());
        source.sendSuccess(() -> HeldenText.bountyRolled(pairs), true);
        return pairs;
    }

    private static int bountyShow(CommandSourceStack source, Collection<GameProfile> profiles) {
        PlayerStateStore store = PlayerStateStore.get(source.getServer());
        for (GameProfile profile : profiles) {
            PlayerState state = store.find(profile.getId());
            Component target = state == null ? HeldenText.bountyNone() : bountyValue(store, state);
            source.sendSuccess(() -> HeldenText.bountyShow(nameOf(profile), target), false);
        }
        return profiles.size();
    }

    /**
     * Setzt eine Paarung von Hand. Nimmt jeweils den ersten Treffer: der Command ist
     * Reparaturwerkzeug fuer genau zwei Leute, kein Massenwerkzeug.
     */
    private static int bountySet(CommandSourceStack source, Collection<GameProfile> players,
                                 Collection<GameProfile> targets) {
        GameProfile player = players.iterator().next();
        GameProfile target = targets.iterator().next();

        if (player.getId().equals(target.getId())) {
            source.sendFailure(HeldenText.bountySelf());
            return 0;
        }

        rememberName(source.getServer(), player);
        rememberName(source.getServer(), target);

        BountyManager.set(source.getServer(), player.getId(), target.getId());
        source.sendSuccess(() -> HeldenText.bountySet(nameOf(player), nameOf(target)), true);
        return 1;
    }

    private static int bountyClear(CommandSourceStack source, Collection<GameProfile> profiles) {
        for (GameProfile profile : profiles) {
            BountyManager.clear(source.getServer(), profile.getId());
            source.sendSuccess(() -> HeldenText.bountyCleared(nameOf(profile)), true);
        }
        return profiles.size();
    }

    private static int bountyClearAll(CommandSourceStack source) {
        BountyManager.clearAll(source.getServer());
        source.sendSuccess(HeldenText::bountyClearedAll, true);
        return 1;
    }

    /**
     * Spielt das Gluecksrad noch einmal ab, ohne am Zustand etwas zu aendern.
     *
     * <p>Ohne den Command laesst sich die Inszenierung genau einmal pro Spielstand ansehen —
     * beim echten Roll, und dann nie wieder.
     *
     * <p>Gerollt wird auf das eigene tatsaechliche Ziel, nicht auf ein erfundenes. Sonst
     * floege am Ende ein Kopf in einen Kasten, in dem gar keiner wohnt, und der Abgang
     * saehe nach einem Fehler aus statt nach dem, was er zeigen soll.
     */
    private static int debugBounty(CommandSourceStack source,
                                   @Nullable Collection<GameProfile> targets) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();

        GameProfile target = targets != null ? targets.iterator().next() : pickOpponent(server, player);

        // Allein auf dem Server bleibt nur der eigene Kopf. Ein Bounty auf sich selbst waere
        // ein kaputter Zustand, also laeuft dann nur die Animation.
        if (target.getId().equals(player.getUUID())) {
            BountyManager.sendRoll(server, player, player.getUUID());
            source.sendSuccess(HeldenText::bountyDebugSolo, false);
            return 1;
        }

        rememberName(server, target);

        // Erst das Rad, dann der Zustand: kaeme der Zustand zuerst, wuerde der Kasten oben
        // links elf Sekunden zu frueh aufleuchten.
        BountyManager.sendRoll(server, player, target.getId());
        BountyManager.set(server, player.getUUID(), target.getId());

        source.sendSuccess(() -> HeldenText.bountyDebug(nameOf(target)), false);
        return 1;
    }

    /**
     * Startet ein Duell ohne Anfrage — gegen das genannte Ziel, sonst gegen einen
     * zufaelligen Anwesenden.
     *
     * <p>Allein im Einzelspieler laeuft stattdessen nur die Anzeige: ein Duell mit sich
     * selbst waere ein kaputter Zustand. Der Balken kommt dann als reines Paket, und zum
     * Leuchten bekommt der Spieler die naechste Kreatur — siehe
     * {@link DuelManager#showcase(ServerPlayer)}.
     */
    private static int debugDuel(CommandSourceStack source, @Nullable ServerPlayer target)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();

        ServerPlayer opponent = target != null ? target : pickOnlineOpponent(server, player);
        if (opponent != null && !opponent.getUUID().equals(player.getUUID())) {
            DuelManager.forceOpen(server, player, opponent);
            source.sendSuccess(() -> HeldenText.duelDebug(opponent.getGameProfile().getName()), false);
            return 1;
        }

        // Allein: nur die Anzeige, ohne Duell dahinter.
        LivingEntity glowing = DuelManager.showcase(player);
        source.sendSuccess(() -> glowing == null
                ? HeldenText.duelDebugSoloEmpty()
                : HeldenText.duelDebugSolo(glowing.getDisplayName()), false);
        return 1;
    }

    /** Ein zufaelliger anderer Anwesender, oder {@code null} wenn man allein ist. */
    @Nullable
    private static ServerPlayer pickOnlineOpponent(MinecraftServer server, ServerPlayer player) {
        List<ServerPlayer> others = new ArrayList<>(server.getPlayerList().getPlayers());
        others.remove(player);

        return others.isEmpty()
                ? null
                : others.get(server.overworld().getRandom().nextInt(others.size()));
    }

    /** Beendet ein Duell von Hand. Reparaturwerkzeug wie {@code /helden combat clear}. */
    private static int duelClear(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            String name = player.getGameProfile().getName();
            if (DuelManager.clear(source.getServer(), player.getUUID())) {
                source.sendSuccess(() -> HeldenText.duelCleared(name), true);
            } else {
                source.sendSuccess(() -> HeldenText.duelCommandNone(name), false);
            }
        }
        return players.size();
    }

    /** Ein zufaelliger anderer Anwesender, sonst man selbst. */
    private static GameProfile pickOpponent(MinecraftServer server, ServerPlayer player) {
        List<ServerPlayer> others = new ArrayList<>(server.getPlayerList().getPlayers());
        others.remove(player);

        return others.isEmpty()
                ? player.getGameProfile()
                : others.get(server.overworld().getRandom().nextInt(others.size())).getGameProfile();
    }

    /**
     * Haelt den Namen eines Spielers im Zustand fest, auch wenn er noch nie da war.
     *
     * <p>Namen werden sonst nur beim Join geschrieben. Ein Bounty auf jemanden, der den
     * Server noch nicht gesehen hat, haette im HUD des Partners einen Kopf ohne Namen —
     * und im Chat eine Zeile, in der das Ziel fehlt.
     */
    private static void rememberName(MinecraftServer source, GameProfile profile) {
        if (profile.getName() == null || profile.getName().isEmpty()) {
            return;
        }

        PlayerStateStore store = PlayerStateStore.get(source);
        PlayerState state = store.getOrCreate(profile.getId());
        if (!profile.getName().equals(state.getName())) {
            state.setName(profile.getName());
            store.setDirty();
        }
    }

    private static int combatClear(CommandSourceStack source, Collection<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            CombatTracker.clear(player);
            source.sendSuccess(() -> Component.literal(player.getGameProfile().getName()
                    + " ist nicht mehr im Kampf").withStyle(ChatFormatting.GRAY), true);
        }
        return players.size();
    }

    /**
     * Setzt einen Treffer auf sich selbst. Ohne das laesst sich das Combat-HUD allein gar
     * nicht ansehen — dazu braeuchte es einen zweiten Spieler, der zuschlaegt.
     */
    private static int debugCombat(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CombatTracker.extend(player, "Debug", null);

        source.sendSuccess(() -> Component.literal("Treffer simuliert — Timer bei "
                + CombatTracker.remainingTicks(player.getUUID()) / 20 + " Sekunden")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    /**
     * Schaltet die eigene Op-Ausnahme vom Zeitlimit ab.
     *
     * <p>Ohne das laesst sich Etappe 6 im Einzelspieler nicht ansehen: Minecraft gibt dem
     * Weltbesitzer fest Rechtestufe 4, solange Cheats an sind, und ohne Cheats gaebe es
     * diesen Command nicht. Gilt bis zum Ausloggen.
     */
    private static int debugPlaytime(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean limited = PlaytimeTracker.toggleForced(player);

        String remaining = formatDuration(
                PlaytimeTracker.remainingFor(source.getServer(), player.getUUID()));
        source.sendSuccess(() -> HeldenText.debugPlaytime(limited, remaining), false);
        return 1;
    }

    /**
     * Fragt Server und Client, was sie ueber die Trennwand wissen.
     *
     * <p>Der Renderer ist der einzige Teil dieser Etappe, den nur der Client kennt. Bleibt
     * der Bildschirm leer, unterscheidet diese Ausgabe die moeglichen Ursachen, statt sie
     * raten zu lassen.
     */
    private static int debugRender(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();

        source.sendSuccess(() -> Component.literal("Server: Wand "
                + (DividerWall.isUp(server) ? "steht" : "gefallen")
                + " | dein X " + String.format("%.1f", player.getX())
                + " | Seite " + sideLabel(server, player)).withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal("Server: Safezone "
                + (SafeZone.isActive(server) ? "aktiv" : "aus")
                + " | du bist " + (SafeZone.covers(player) ? "drin" : "draussen"))
                .withStyle(ChatFormatting.GRAY), false);

        NetworkHandler.askRenderReport(player);
        return 1;
    }

    private static String sideLabel(MinecraftServer server, ServerPlayer player) {
        Side side = DividerWall.sideOf(server, player);
        return side == null ? "keine" : side.getId();
    }

    /** Leert die Kontingente, damit sich der aufgebrauchte Zustand ansehen laesst. */
    private static int debugQuota(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!CombatTracker.isInCombat(player.getUUID())) {
            CombatTracker.extend(player, "Debug", null);
        }
        ItemQuota.drain(player);

        source.sendSuccess(() -> Component.literal("Kontingente aufgebraucht")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int phaseSet(CommandSourceStack source, String phaseId) {
        Phase phase = Phase.byId(phaseId);
        if (phase == null) {
            source.sendFailure(HeldenText.phaseUnknown(phaseIds()));
            return 0;
        }
        return startPhase(source, phase);
    }

    /** Die gueltigen Kennungen, fuer die Fehlermeldung. */
    private static String phaseIds() {
        return Arrays.stream(Phase.values()).map(Phase::getId).collect(Collectors.joining(", "));
    }

    /**
     * Startet ein Event.
     *
     * <p>Drei Fehler, drei Meldungen: unbekannte Kennung, unlesbare Dauer, falsche Phase.
     * Eine pauschale "geht nicht"-Zeile liesse den Op raten.
     */
    private static int eventStart(CommandSourceStack source, String typeId, String duration) {
        EventType type = EventType.byId(typeId);
        if (type == null) {
            source.sendFailure(HeldenText.eventUnknown(eventIds()));
            return 0;
        }

        long millis = DurationText.parseMillis(duration);
        if (millis == DurationText.INVALID) {
            source.sendFailure(HeldenText.durationInvalid());
            return 0;
        }

        if (!EventManager.start(source.getServer(), type, millis)) {
            source.sendFailure(HeldenText.eventDenyPhase(
                    type.getDisplayName(), type.allowedPhase().getDisplayName()));
            return 0;
        }

        // Die Ansage macht der Manager als Broadcast — fuer einen Spieler waere eine
        // zweite Zeile eine Dopplung. Die Konsole und ein Befehlsblock stehen aber nicht
        // in der Spielerliste und saehen sonst gar nichts.
        if (source.getPlayer() == null) {
            String shown = DurationText.clock(millis);
            source.sendSuccess(() -> HeldenText.eventStarted(type.getDisplayName(), shown), false);
        }

        return 1;
    }

    private static int eventStop(CommandSourceStack source) {
        EventType running = EventManager.active(source.getServer());
        if (running == null) {
            source.sendFailure(HeldenText.eventNone());
            return 0;
        }

        EventManager.stop(source.getServer(), HeldenText.eventStopped(running.getDisplayName()));

        if (source.getPlayer() == null) {
            source.sendSuccess(() -> HeldenText.eventStopped(running.getDisplayName()), false);
        }

        return 1;
    }

    private static int eventInfo(CommandSourceStack source) {
        EventType running = EventManager.active(source.getServer());
        if (running == null) {
            source.sendSuccess(HeldenText::eventNone, false);
            return 0;
        }

        String left = DurationText.clock(EventManager.remainingMillis(source.getServer()));
        source.sendSuccess(() -> HeldenText.eventInfoRunning(running.getDisplayName(), left), false);
        return 1;
    }

    /** Die gueltigen Kennungen, fuer die Fehlermeldung. */
    private static String eventIds() {
        return Arrays.stream(EventType.values())
                .map(EventType::getId)
                .collect(Collectors.joining(", "));
    }

    private static int phaseNext(CommandSourceStack source) {
        Phase next = PhaseManager.next(source.getServer());
        if (next == null) {
            source.sendFailure(HeldenText.phaseNoNext());
            return 0;
        }
        return startPhase(source, next);
    }

    /**
     * Leitet einen Phasenwechsel ein.
     *
     * <p>Nach vorn laeuft erst ein Countdown, zurueck greift es sofort — die Rueckmeldung
     * an den Op sagt deswegen, was tatsaechlich passiert ist, statt beides gleich zu nennen.
     */
    private static int startPhase(CommandSourceStack source, Phase phase) {
        if (!PhaseManager.begin(source.getServer(), phase)) {
            source.sendSuccess(() -> HeldenText.phaseCurrent(phase.getDisplayName()), false);
            return 0;
        }

        boolean counting = PhaseManager.isCountingDown();
        source.sendSuccess(() -> counting
                ? HeldenText.phaseStarting(phase.getDisplayName())
                : HeldenText.phaseSet(phase.getDisplayName()), true);
        return 1;
    }

    /**
     * Stellt die Trennwand um, ohne die Phase anzufassen.
     *
     * <p>Die Gegenrichtung zu {@code wall drop} ist {@code wall raise} und nicht ein
     * Phasenwechsel: es darf keinen Zustand geben, aus dem es nur vorwaerts geht.
     */
    private static int wall(CommandSourceStack source, boolean up) {
        MinecraftServer server = source.getServer();
        if (DividerWall.isUp(server) == up) {
            source.sendSuccess(HeldenText::wallAlready, false);
            return 0;
        }

        DividerWall.setUp(server, up);
        server.getPlayerList().broadcastSystemMessage(
                up ? HeldenText.wallRaised() : HeldenText.wallDropped(), false);
        return 1;
    }

    /**
     * Sagt, wo dieser Spieler beim naechsten Tod aufwacht.
     *
     * <p>Ohne Bett wird bei jedem Tod neu gewuerfelt, mit Bett gilt das Bett. Von aussen
     * sieht beides gleich aus — man wacht irgendwo auf. Diese Zeile trennt es, und sie
     * nennt dazu den zuletzt gezogenen Punkt.
     */
    private static int debugRespawn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        PlayerState state = PlayerStateStore.get(server).find(player.getUUID());

        source.sendSuccess(() -> Component.literal(String.format(
                "Zustand: Seite %s | Startpunkt %s",
                state == null || state.getSide() == null ? "—" : state.getSide().getId(),
                state == null ? "—" : posOrDash(state.getStartSpawn()))), false);

        BlockPos vanilla = player.getRespawnPosition();
        source.sendSuccess(() -> Component.literal(String.format(
                "Vanilla: %s | Weltspawn %s",
                vanilla == null
                        ? "kein Bett — beim naechsten Tod wird neu gewuerfelt"
                        : posOrDash(vanilla) + (player.isRespawnForced() ? " (erzwungen)" : ""),
                posOrDash(server.overworld().getSharedSpawnPos()))), false);

        // Eine Welt aus einer frueheren Fassung traegt den Startpunkt noch als erzwungenen
        // Respawnpunkt im Spieler-NBT. Niemand setzt ihn mehr, aber er steht da — und fuer
        // die Regel sieht er aus wie ein Bett, es wird also nie neu gewuerfelt. Das ist von
        // aussen nicht zu sehen, deswegen sagt es diese Zeile.
        if (vanilla != null && player.isRespawnForced() && state != null
                && vanilla.equals(state.getStartSpawn())) {
            source.sendSuccess(() -> Component.literal(
                    "Achtung: das ist kein Bett, sondern ein Alt-Eintrag aus einer frueheren "
                            + "Fassung. Er verhindert das Neuwuerfeln. In einer neuen Welt "
                            + "gibt es ihn nicht.").withStyle(ChatFormatting.YELLOW), false);
        }

        source.sendSuccess(() -> Component.literal(String.format(
                "Jetzt hier: %d/%d/%d",
                player.getBlockX(), player.getBlockY(), player.getBlockZ())), false);
        return 1;
    }

    private static String posOrDash(@Nullable BlockPos pos) {
        return pos == null ? "—" : pos.getX() + "/" + pos.getY() + "/" + pos.getZ();
    }

    /**
     * Sagt, warum an der Border etwas passiert — oder eben nicht.
     *
     * <p>Drei Bedingungen koennen den Effekt abwuergen, und von aussen sehen alle drei
     * gleich aus: die Border bewegt sich gar nicht, man steht zu weit von der Kante weg,
     * oder sie laeuft in die falsche Richtung. Diese Zeile unterscheidet sie.
     *
     * <p>Sie nennt dieselbe Zahl, nach der sich der Effekt richtet, statt einer zweiten
     * Rechnung daneben — sonst diagnostiziert man irgendwann die Diagnose.
     */
    private static int debugBorder(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WorldBorder border = source.getServer().overworld().getWorldBorder();

        double toEdge = BorderStorm.distanceToEdge(border, player.getX(), player.getZ());
        float heat = BorderStorm.heat(toEdge);

        source.sendSuccess(() -> Component.literal(String.format(
                "Border: %.0f breit | %s | Ziel %.0f | Restzeit %s",
                border.getSize(), border.getStatus(), border.getLerpTarget(),
                DurationText.clock(border.getLerpRemainingTime()))), false);

        // Die Ecken ausschreiben, statt sie aus Mittelpunkt und Groesse im Kopf auszurechnen.
        source.sendSuccess(() -> Component.literal(String.format(
                "Ecken: %.0f/%.0f bis %.0f/%.0f",
                border.getMinX(), border.getMinZ(), border.getMaxX(), border.getMaxZ())), false);

        source.sendSuccess(() -> Component.literal(String.format(
                "Kante: %.1f Bloecke weg | Staerke %.2f | %s",
                toEdge, heat,
                border.getStatus() != BorderStatus.SHRINKING
                        ? "sie schrumpft nicht, deswegen ist es still"
                        : heat <= 0.0f
                                ? "zu weit weg, es passiert nichts"
                                : BorderStorm.strikes(toEdge)
                                        ? "Funken und Einschlaege"
                                        : "nur Funken")), false);
        return 1;
    }

    /**
     * Startet den Final War.
     *
     * <p>Ohne Dauer gilt die Vorgabe von zweieinhalb Stunden. Der Weg geht durch den
     * {@link PhaseManager}, damit Countdown, Sturm, Kuppel und Border ein Vorgang bleiben
     * und nicht vier.
     */
    private static int finalWarStart(CommandSourceStack source, @Nullable String duration) {
        long millis = BorderController.DEFAULT_DURATION_MILLIS;

        if (duration != null) {
            millis = DurationText.parseMillis(duration);
            if (millis == DurationText.INVALID) {
                source.sendFailure(HeldenText.durationInvalid());
                return 0;
            }
        }

        if (!PhaseManager.begin(source.getServer(), Phase.FINAL_WAR, millis)) {
            source.sendFailure(HeldenText.finalWarAlready());
            return 0;
        }

        String shown = DurationText.clock(millis);
        source.sendSuccess(() -> HeldenText.finalWarStarting(shown), true);
        return 1;
    }

    /**
     * Nimmt den Final War zurueck — auch mitten im Countdown.
     *
     * <p>Der {@code cancel} davor ist kein Beiwerk: ohne ihn liefe ein angefangener
     * Countdown weiter und schaltete Sekunden spaeter doch noch in den Final War.
     */
    private static int finalWarStop(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        boolean running = GameState.get(server).getPhase() == Phase.FINAL_WAR
                || PhaseManager.isCountingDown();

        if (!running) {
            source.sendFailure(HeldenText.finalWarNotRunning());
            return 0;
        }

        PhaseManager.cancel(server);
        PhaseManager.apply(server, Phase.KRIEG, false);
        source.sendSuccess(HeldenText::finalWarStopped, true);
        return 1;
    }

    /** Das nackte Werkzeug: schrumpft, ohne Phase und ohne Bossbar. */
    private static int borderShrink(CommandSourceStack source, int size, String duration) {
        long millis = DurationText.parseMillis(duration);
        if (millis == DurationText.INVALID) {
            source.sendFailure(HeldenText.durationInvalid());
            return 0;
        }

        BorderController.shrink(source.getServer(), size, millis);
        String shown = DurationText.clock(millis);
        source.sendSuccess(() -> HeldenText.borderShrinking(size, shown), true);
        return 1;
    }

    /** Sagt, wo die Arena liegt — und ob die Weltborder noch dazu passt. */
    private static int centerShow(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        double x = ArenaCenter.x(server);
        double z = ArenaCenter.z(server);
        double borderX = ArenaCenter.borderCenterX(server);
        double borderZ = ArenaCenter.borderCenterZ(server);

        source.sendSuccess(() -> HeldenText.centerShow(coords(x, z), coords(borderX, borderZ)), false);

        // Ein Op darf die Weltborder von Hand woanders hinsetzen. Dann steht die Kuppel
        // nicht mehr in ihrer Mitte, und das sieht man erst, wenn man davorsteht.
        if (Math.abs(x - borderX) > 0.5 || Math.abs(z - borderZ) > 0.5) {
            source.sendSuccess(HeldenText::centerMismatch, false);
        }
        return 1;
    }

    /** Verschiebt die Arena auf die eigene Position. Im Spiel der bequemere Weg. */
    private static int centerHere(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return centerMove(source, (int) Math.round(player.getX()), (int) Math.round(player.getZ()));
    }

    /**
     * Schiebt Safezone, Trennwand und Weltborder gemeinsam.
     *
     * <p>Gemeinsam ist der Punkt: bliebe die Wand bei X = 0, waehrend die Border woanders
     * liegt, haette eine Seite mehr Land als die andere.
     */
    private static int centerMove(CommandSourceStack source, int x, int z) {
        ArenaCenter.move(source.getServer(), x, z);
        source.sendSuccess(() -> HeldenText.centerMoved(coords(x, z)), true);
        return 1;
    }

    /** Zurueck auf den in der Serverconfig eingestellten Fleck, nicht stur auf 0,0. */
    private static int centerReset(CommandSourceStack source) {
        return centerMove(source, (int) ArenaCenter.defaultX(), (int) ArenaCenter.defaultZ());
    }

    private static String coords(double x, double z) {
        return String.format("%.0f, %.0f", x, z);
    }

    private static int borderReset(CommandSourceStack source) {
        BorderController.reset(source.getServer());
        source.sendSuccess(() -> HeldenText.borderReset((int) BorderController.START_SIZE), true);
        return 1;
    }

    private static int timeCheck(CommandSourceStack source, Collection<GameProfile> profiles) {
        MinecraftServer server = source.getServer();
        for (GameProfile profile : profiles) {
            source.sendSuccess(() -> HeldenText.playtimeReport(nameOf(profile),
                    formatDuration(PlaytimeTracker.remainingFor(server, profile.getId()))), false);
        }
        return profiles.size();
    }

    private static int timeAdd(CommandSourceStack source, Collection<GameProfile> profiles, int minutes) {
        MinecraftServer server = source.getServer();
        for (GameProfile profile : profiles) {
            PlaytimeTracker.addSeconds(server, profile.getId(), minutes * 60);
            source.sendSuccess(() -> HeldenText.timeAdded(nameOf(profile),
                    formatDuration(PlaytimeTracker.remainingFor(server, profile.getId()))), true);
        }
        return profiles.size();
    }

    private static int timeSet(CommandSourceStack source, Collection<GameProfile> profiles, int minutes) {
        MinecraftServer server = source.getServer();
        for (GameProfile profile : profiles) {
            PlaytimeTracker.setRemaining(server, profile.getId(), minutes * 60);
            source.sendSuccess(() -> HeldenText.timeSet(nameOf(profile),
                    formatDuration(PlaytimeTracker.remainingFor(server, profile.getId()))), true);
        }
        return profiles.size();
    }

    private static int phaseInfo(CommandSourceStack source) {
        Phase phase = GameState.get(source.getServer()).getPhase();
        source.sendSuccess(() -> HeldenText.phaseCurrent(phase.getDisplayName()), false);
        return 1;
    }

    private static String nameOf(GameProfile profile) {
        return profile.getName() != null ? profile.getName() : profile.getId().toString();
    }

    private static String formatDuration(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
