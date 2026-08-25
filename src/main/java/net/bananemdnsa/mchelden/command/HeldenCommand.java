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
import net.bananemdnsa.mchelden.text.HeldenText;

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
                HeldenText.playtimeLeft(formatDuration(
                        Math.max(0, PlayerState.DAILY_PLAYTIME_SECONDS - state.getPlaytimeUsedSeconds())))), false);
        source.sendSuccess(() -> HeldenText.infoLine("mchelden.command.info.status",
                state.isEliminated() ? HeldenText.statusEliminated() : HeldenText.statusActive()), false);
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

    private static int phaseSet(CommandSourceStack source, String phaseId) {
        Phase phase = Phase.byId(phaseId);
        GameState.get(source.getServer()).setPhase(phase);
        source.sendSuccess(() -> HeldenText.phaseSet(phase.getDisplayName()), true);
        return 1;
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
