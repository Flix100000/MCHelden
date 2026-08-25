package net.bananemdnsa.mchelden.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.bananemdnsa.mchelden.hearts.Elimination;
import net.bananemdnsa.mchelden.hearts.HeartManager;
import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.state.PlayerState;
import net.bananemdnsa.mchelden.state.PlayerStateStore;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

public final class HeldenCommand {
    private HeldenCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("helden")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("info")
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(context -> info(
                                        context.getSource(),
                                        GameProfileArgument.getGameProfiles(context, "spieler")))))
                .then(Commands.literal("heart")
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
                .then(Commands.literal("phase")
                        .then(Commands.literal("info")
                                .executes(context -> phaseInfo(context.getSource())))
                        .then(Commands.literal("set")
                                .then(Commands.argument("phase", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Phase.values()).map(Phase::getId), builder))
                                        .executes(context -> phaseSet(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "phase")))))));
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
            int now = HeartManager.add(server, profile.getId(), delta, "");
            report(source, profile, now);
        }
        return profiles.size();
    }

    private static int heartSet(CommandSourceStack source, Collection<GameProfile> profiles, int hearts) {
        MinecraftServer server = source.getServer();
        for (GameProfile profile : profiles) {
            int now = HeartManager.set(server, profile.getId(), hearts, "");
            report(source, profile, now);
        }
        return profiles.size();
    }

    private static int revive(CommandSourceStack source, Collection<GameProfile> profiles, int hearts) {
        MinecraftServer server = source.getServer();
        for (GameProfile profile : profiles) {
            Elimination.revive(server, profile.getId(), hearts);
            source.sendSuccess(() -> Component.literal(nameOf(profile) + " ist zurück im Spiel mit "
                    + hearts + " Herzen").withStyle(ChatFormatting.GREEN), true);
        }
        return profiles.size();
    }

    private static void report(CommandSourceStack source, GameProfile profile, int hearts) {
        source.sendSuccess(() -> Component.literal(nameOf(profile) + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(hearts + " / " + PlayerState.MAX_HEARTS)
                        .withStyle(hearts == 0 ? ChatFormatting.RED : ChatFormatting.WHITE)), true);
    }

    private static String nameOf(GameProfile profile) {
        return profile.getName() != null ? profile.getName() : profile.getId().toString();
    }

    private static int phaseSet(CommandSourceStack source, String phaseId) {
        Phase phase = Phase.byId(phaseId);
        GameState gameState = GameState.get(source.getServer());
        gameState.setPhase(phase);

        source.sendSuccess(() -> Component.literal("Phase gesetzt auf ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(phase.getDisplayName()).withStyle(ChatFormatting.GOLD)), true);
        return 1;
    }

    private static int info(CommandSourceStack source, Collection<GameProfile> profiles) {
        MinecraftServer server = source.getServer();
        PlayerStateStore store = PlayerStateStore.get(server);

        for (GameProfile profile : profiles) {
            printPlayer(source, store, profile);
        }
        return profiles.size();
    }

    private static void printPlayer(CommandSourceStack source, PlayerStateStore store, GameProfile profile) {
        UUID uuid = profile.getId();
        PlayerState state = store.find(uuid);

        String name = profile.getName() != null ? profile.getName() : uuid.toString();
        source.sendSuccess(() -> Component.literal(name).withStyle(ChatFormatting.GOLD), false);

        if (state == null) {
            source.sendSuccess(() -> Component.literal("  kein Zustand gespeichert — war noch nie auf dem Server")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }

        line(source, "Herzen", state.getHearts() + " / " + PlayerState.MAX_HEARTS);
        line(source, "Bounty", describeBounty(store, state));
        line(source, "Spielzeit", formatDuration(
                Math.max(0, PlayerState.DAILY_PLAYTIME_SECONDS - state.getPlaytimeUsedSeconds())) + " übrig");
        line(source, "Status", state.isEliminated() ? "ausgeschieden" : "aktiv");
    }

    private static String describeBounty(PlayerStateStore store, PlayerState state) {
        UUID target = state.getBountyTarget();
        if (target == null) {
            return state.isBountyResolved() ? "aufgelöst" : "kein Ziel";
        }
        PlayerState targetState = store.find(target);
        String targetName = targetState != null && !targetState.getName().isEmpty()
                ? targetState.getName()
                : target.toString();
        return targetName;
    }

    private static int phaseInfo(CommandSourceStack source) {
        GameState gameState = GameState.get(source.getServer());
        source.sendSuccess(() -> Component.literal("Phase: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(gameState.getPhase().getDisplayName())
                        .withStyle(ChatFormatting.GOLD)), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String label, String value) {
        source.sendSuccess(() -> Component.literal("  " + label + ": ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
    }

    private static String formatDuration(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
