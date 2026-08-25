package net.bananemdnsa.mchelden.command;

import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

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
