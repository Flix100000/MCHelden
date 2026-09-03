package net.bananemdnsa.mchelden.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.bananemdnsa.mchelden.grave.GraveBlockEntity;
import net.bananemdnsa.mchelden.grave.GraveNotice;
import net.bananemdnsa.mchelden.grave.GraveRegistry;
import net.bananemdnsa.mchelden.text.DurationText;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Der {@code grave}-Teilbaum.
 *
 * <p>Eigene Datei aus demselben Grund wie {@link ResetCommand}: {@code HeldenCommand} ist
 * ohnehin die groesste Datei der Mod, und der Schnitt liegt an einer Naht, die es schon gibt.
 *
 * <p>Bisher war {@code /helden reset graves} der einzige Grave-Befehl — abraeumen oder
 * nichts. Ein Operator konnte kein Grab nachschlagen, keins ansehen und nicht hinreisen.
 *
 * <p><b>Alle vier haengen am Verzeichniseintrag, nicht am Block.</b> Ein Eintrag ohne Block
 * ist ein regulaerer Zustand — das Verzeichnis darf hinterherhinken —, und genau diese Reste
 * will man ansehen und wegraeumen koennen.
 *
 * <p><b>Alle vier sind still.</b> Wie bei den Resets sieht das Ergebnis nur der ausfuehrende
 * Op. {@code grave info} oeffentlich zu machen wuerde verraten, wer wo was liegen hat, und
 * das ist Spielwissen, das ein Operator nicht verteilen soll, nur weil er nachgesehen hat.
 *
 * <p>Alles laeuft in der Oberwelt. Das Verzeichnis speichert eine {@link BlockPos} ohne
 * Dimension, und ohne Nether und End gibt es nichts zu unterscheiden.
 */
public final class GraveCommand {

    /**
     * Wie viele Zeilen {@code list} hoechstens ausgibt.
     *
     * <p>Nach zwei Wochen Projekt koennen leicht hundert Eintraege stehen. Eine Liste, die
     * den Chat ueberflutet, ist keine Liste — wer weiter zurueck will, filtert nach Spieler.
     */
    private static final int LIST_LIMIT = 20;

    private GraveCommand() {
    }

    /** Haengt sich unter {@code /helden} ein. */
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("grave")
                .requires(HeldenPermission.branch("grave"))
                .then(Commands.literal("list")
                        .requires(HeldenPermission.GRAVE_LIST::granted)
                        .executes(context -> list(context.getSource(), null))
                        .then(Commands.argument("spieler", GameProfileArgument.gameProfile())
                                .executes(context -> list(context.getSource(),
                                        GameProfileArgument.getGameProfiles(context, "spieler")))))
                .then(positional("info", HeldenPermission.GRAVE_INFO, GraveCommand::info))
                .then(positional("tp", HeldenPermission.GRAVE_TP, GraveCommand::teleport))
                .then(positional("remove", HeldenPermission.GRAVE_REMOVE, GraveCommand::remove));
    }

    /**
     * Ein Zweig, der eine Position entgegennimmt.
     *
     * <p>Vanillas {@code BlockPosArgument} statt dreier Zahlen: damit funktioniert
     * {@code ~ ~ ~}, und die Vervollstaendigung schlaegt den angeschauten Block vor. Ein Op,
     * der vor einem Grab steht, tippt keine Koordinaten ab.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> positional(String name,
                                                                        HeldenPermission permission,
                                                                        Positional action) {
        return Commands.literal(name)
                .requires(permission::granted)
                .then(Commands.argument("position", BlockPosArgument.blockPos())
                        .executes(context -> action.apply(context.getSource(),
                                BlockPosArgument.getBlockPos(context, "position"))));
    }

    @FunctionalInterface
    private interface Positional {
        int apply(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException;
    }

    /**
     * Listet Graeber auf, neueste zuerst.
     *
     * <p><b>Fragt nur das Verzeichnis.</b> Kein Chunk wird geladen, auch nicht bei vierzig
     * Graebern. Deswegen steht hier kein Inhalt: Item-Anzahl und XP sind fluechtig, und eine
     * veraltete Zahl in einer Liste ist irreführender als gar keine. Wer den Inhalt will,
     * klickt die Zeile an und landet bei {@code info}.
     */
    private static int list(CommandSourceStack source, @Nullable Collection<GameProfile> profiles) {
        MinecraftServer server = source.getServer();
        GraveRegistry registry = GraveRegistry.get(server);
        long now = server.overworld().getGameTime();

        List<BlockPos> positions = profiles == null
                ? registry.all()
                : forProfiles(registry, profiles);

        if (positions.isEmpty()) {
            source.sendSuccess(HeldenText::graveListEmpty, false);
            return 0;
        }

        // Bei einer gefilterten Liste steht der Besitzer schon in der Kopfzeile. Ihn in jeder
        // Zeile zu wiederholen sagt nichts Neues.
        boolean filtered = profiles != null;
        int total = positions.size();

        if (filtered) {
            String who = describe(profiles);
            source.sendSuccess(() -> HeldenText.graveListHeader(who, total), false);
        } else {
            source.sendSuccess(() -> HeldenText.graveListHeaderAll(total), false);
        }

        for (BlockPos pos : positions.subList(0, Math.min(total, LIST_LIMIT))) {
            String owner = filtered ? null : ownerName(server, registry, pos);
            Component age = age(registry.diedAt(pos), now);
            source.sendSuccess(() -> HeldenText.graveListLine(GraveNotice.coordinates(pos),
                    owner, age), false);
        }

        if (total > LIST_LIMIT) {
            int rest = total - LIST_LIMIT;
            source.sendSuccess(() -> HeldenText.graveListMore(rest), false);
        }

        return total;
    }

    /**
     * Zeigt ein einzelnes Grab mit allem, was drin liegt.
     *
     * <p>Hier wird der Chunk geladen — ausdruecklich angefordert, fuer genau ein Grab. Das
     * ist der Unterschied zu {@code list}, das ueber vierzig Eintraege laufen kann.
     */
    private static int info(CommandSourceStack source, BlockPos pos) {
        MinecraftServer server = source.getServer();
        GraveRegistry registry = GraveRegistry.get(server);

        if (registry.ownerOf(pos).isEmpty()) {
            source.sendFailure(HeldenText.graveNone());
            return 0;
        }

        ServerLevel level = server.overworld();
        String coordinates = GraveNotice.coordinates(pos);
        String owner = ownerName(server, registry, pos);

        source.sendSuccess(() -> HeldenText.graveInfoHeader(owner, coordinates), false);

        if (!(level.getBlockEntity(pos) instanceof GraveBlockEntity grave)) {
            // Eintrag ohne Block. Das Alter kennt das Verzeichnis trotzdem, also steht es da.
            Component age = age(registry.diedAt(pos), level.getGameTime());
            source.sendSuccess(() -> HeldenText.graveInfoDetailsFaded(age, 0), false);
            source.sendSuccess(HeldenText::graveInfoBlockGone, false);
            return 1;
        }

        source.sendSuccess(() -> details(level, grave), false);

        for (int slot = 0; slot < grave.getContainerSize(); slot++) {
            ItemStack stack = grave.getItem(slot);
            if (!stack.isEmpty()) {
                source.sendSuccess(() -> HeldenText.graveInfoItem(stack.getCount(),
                        stack.getHoverName()), false);
            }
        }

        return 1;
    }

    /** Alter, XP und Reststrahl des Grabes, das noch steht. */
    private static Component details(ServerLevel level, GraveBlockEntity grave) {
        Component age = HeldenText.graveAge(level.getGameTime() - grave.getDiedAt());
        long remaining = grave.beamRemainingTicks(level.getGameTime());

        return remaining > 0
                ? HeldenText.graveInfoDetails(age, grave.getStoredXp(),
                        DurationText.clock(remaining * 50L))
                : HeldenText.graveInfoDetailsFaded(age, grave.getStoredXp());
    }

    /**
     * Teleportiert zum Grab.
     *
     * <p>Auf den Block, nicht in ihn: der Grabstein ist ein voller Block, und wer darin
     * landet, steckt fest.
     */
    private static int teleport(CommandSourceStack source, BlockPos pos)
            throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        GraveRegistry registry = GraveRegistry.get(server);

        if (registry.ownerOf(pos).isEmpty()) {
            source.sendFailure(HeldenText.graveNone());
            return 0;
        }

        ServerPlayer player = source.getPlayerOrException();
        String owner = ownerName(server, registry, pos);
        String coordinates = GraveNotice.coordinates(pos);

        player.teleportTo(server.overworld(), pos.getX() + 0.5, pos.getY() + 1.0,
                pos.getZ() + 0.5, player.getYRot(), player.getXRot());

        source.sendSuccess(() -> HeldenText.graveTeleported(owner, coordinates), false);
        return 1;
    }

    /**
     * Raeumt ein einzelnes Grab ab.
     *
     * <p>Ueber {@link ResetCommand#clearGraves}, damit dieselbe Regel gilt wie beim Reset:
     * {@link GraveBlockEntity#discard} leert Kiste und XP, <b>bevor</b> der Block
     * verschwindet. Ein Abraeumen ist keine Auszahlung.
     *
     * <p>Ohne Bestaetigungsabfrage. Ein einzelnes Grab ist keine {@code reset all}, und eine
     * Rueckfrage bei jedem Klick aus der Liste waere nur im Weg.
     */
    private static int remove(CommandSourceStack source, BlockPos pos) {
        MinecraftServer server = source.getServer();
        GraveRegistry registry = GraveRegistry.get(server);

        if (registry.ownerOf(pos).isEmpty()) {
            source.sendFailure(HeldenText.graveNone());
            return 0;
        }

        String owner = ownerName(server, registry, pos);
        String coordinates = GraveNotice.coordinates(pos);

        ResetCommand.clearGraves(server, List.of(pos));

        source.sendSuccess(() -> HeldenText.graveRemoved(owner, coordinates), false);
        return 1;
    }

    /** Die Graeber der genannten Spieler, neueste zuerst. */
    private static List<BlockPos> forProfiles(GraveRegistry registry,
                                              Collection<GameProfile> profiles) {
        List<BlockPos> found = new ArrayList<>();

        for (GameProfile profile : profiles) {
            found.addAll(registry.of(profile.getId()));
        }

        return found;
    }

    /**
     * Der Name des Besitzers, so gut wie er zu bekommen ist.
     *
     * <p>Das Verzeichnis speichert nur die UUID. Der Name kommt aus dem Spielerverzeichnis
     * des Servers und fehlt bei jemandem, der noch nie auf diesem Server war — dann steht
     * die UUID da. Lieber die als nichts: sie ist immer noch nachschlagbar.
     */
    private static String ownerName(MinecraftServer server, GraveRegistry registry, BlockPos pos) {
        Optional<UUID> owner = registry.ownerOf(pos);
        if (owner.isEmpty()) {
            return "?";
        }

        UUID uuid = owner.get();
        Optional<GameProfile> profile = server.getProfileCache() == null
                ? Optional.empty()
                : server.getProfileCache().get(uuid);

        return profile.map(GameProfile::getName).orElseGet(uuid::toString);
    }

    /** Das Alter eines Eintrags, oder das Eingestaendnis, dass es nicht bekannt ist. */
    private static Component age(long diedAt, long now) {
        return diedAt == GraveRegistry.UNKNOWN_TIME
                ? HeldenText.graveAgeUnknown()
                : HeldenText.graveAge(now - diedAt);
    }

    /** Wen die Liste umfasst, fuer die Kopfzeile. Dieselbe Form wie bei den Resets. */
    private static String describe(Collection<GameProfile> profiles) {
        StringBuilder names = new StringBuilder();

        for (GameProfile profile : profiles) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(profile.getName() != null ? profile.getName() : profile.getId());
        }

        return names.toString();
    }
}
