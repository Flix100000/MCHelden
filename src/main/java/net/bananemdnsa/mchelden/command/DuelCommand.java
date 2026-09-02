package net.bananemdnsa.mchelden.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.bananemdnsa.mchelden.duel.DuelManager;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

/**
 * Der Duell-Command. Steht bewusst neben {@code /helden} statt darin: dessen Zweige haengen
 * an Rechten, und ein Duell verabreden duerfen alle.
 *
 * <p>Die drei Unterbefehle stehen vor dem Spielernamen. Brigadier prueft Literale vor
 * Argumenten, {@code /duell accept Max} landet also verlaesslich im richtigen Zweig.
 */
public final class DuelCommand {
    private DuelCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("duell")
                .then(Commands.literal("accept")
                        .then(Commands.argument("spieler", EntityArgument.player())
                                .executes(context -> accept(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "spieler")))))
                .then(Commands.literal("deny")
                        .then(Commands.argument("spieler", EntityArgument.player())
                                .executes(context -> deny(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "spieler")))))
                .then(Commands.literal("cancel")
                        .executes(context -> cancel(context.getSource())))
                .then(Commands.argument("spieler", EntityArgument.player())
                        .executes(context -> request(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "spieler")))));
    }

    private static int request(CommandSourceStack source, ServerPlayer target)
            throws CommandSyntaxException {
        DuelManager.request(source.getPlayerOrException(), target);
        return 1;
    }

    private static int accept(CommandSourceStack source, ServerPlayer requester)
            throws CommandSyntaxException {
        DuelManager.accept(source.getPlayerOrException(), requester);
        return 1;
    }

    private static int deny(CommandSourceStack source, ServerPlayer requester)
            throws CommandSyntaxException {
        DuelManager.deny(source.getPlayerOrException(), requester);
        return 1;
    }

    private static int cancel(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!DuelManager.cancel(player)) {
            source.sendFailure(HeldenText.duelDenied("mchelden.duel.cancel.none"));
            return 0;
        }
        return 1;
    }
}
