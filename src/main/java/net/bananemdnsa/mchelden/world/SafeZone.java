package net.bananemdnsa.mchelden.world;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.bananemdnsa.mchelden.combat.CombatTracker;
import net.bananemdnsa.mchelden.state.GameState;
import net.bananemdnsa.mchelden.state.Phase;
import net.bananemdnsa.mchelden.text.HeldenText;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Die Safezone: ein Zylinder um 0,0, in dem niemand angreifen kann.
 *
 * <p><b>Ein Zylinder und keine Kugel.</b> Eine Kugel verlaesst man, indem man hochbaut —
 * wer sich bei 0,0 eine Plattform setzt, waere ploetzlich angreifbar, ohne dass sich etwas
 * geaendert haette. Die Hoehe zaehlt deswegen gar nicht mit, und die Zone braucht keinen
 * Mittelpunkt: nur einen Radius um die Weltachse.
 *
 * <p><b>Kein Spawnschutz.</b> Der Startspawn liegt zufaellig in der Welt verteilt. Der
 * Zweck ist ein anderer: die Kugel liegt auf der Trennwand, und mit Proximity-Voicechat
 * kann man sich dort fuenf Tage lang treffen und reden, obwohl die andere Haelfte
 * unerreichbar ist. Absprachen, Buendnisse, Drohungen.
 *
 * <p>Nach dem Wandfall bleibt sie der einzige Ort, an dem man sich gegenuebertreten kann,
 * ohne dass sofort gekaempft wird. Mit dem Final War verschwindet sie — sonst waere sie
 * am Ende eine Wohnung, in der man unsterblich ist.
 *
 * <p>Wer im Kampf steht, kommt nicht hinein. Ohne diese Regel waere sie ein Fluchtknopf.
 */
public final class SafeZone {
    /** Hundert Bloecke Durchmesser, von der Trennwand halbiert — fuenfzig je Seite. */
    public static final double RADIUS = 50.0;

    /**
     * Zustand aus Sicht des Clients.
     *
     * <p>Getrennt vom Server gehalten, aus demselben Grund wie bei der Trennwand: die
     * Kollision und der Renderer laufen auch auf dem Client, und der hat weder
     * {@link GameState} noch den {@link CombatTracker}.
     */
    private static boolean clientActive = true;
    private static boolean clientInCombat;

    /** Wer beim letzten Tick drinstand. Fuer die Hinweise beim Uebertreten. */
    private static final Set<UUID> INSIDE = ConcurrentHashMap.newKeySet();

    /** Wie oft der Hinweis an der Grenze wiederholt wird, und ab welcher Naehe. */
    private static final int DENIAL_GAP = 10;
    private static final double DENIAL_RANGE = 4.0;

    /** Ab hier lohnt die genaue Pruefung. Alles Weitere kostet nur einen Vergleich. */
    private static final double REACH = 8.0;
    /** Wie weit das Ergebnis notfalls vor die Grenze gesetzt wird. */
    private static final double EDGE_GAP = 0.001;

    private SafeZone() {
    }

    /**
     * Liegt dieser Punkt im Zylinder?
     *
     * <p>Nur der waagerechte Abstand zaehlt. Die Zone reicht von der Weltuntergrenze bis
     * ueber die Bauhoehe — man kann darin bauen, tuermen und graben, ohne herauszufallen.
     */
    public static boolean contains(double x, double z) {
        return x * x + z * z <= RADIUS * RADIUS;
    }

    /** Beim Empfang des Spielzustands setzen. Nur auf dem Client. */
    public static void setClientActive(boolean active) {
        clientActive = active;
    }

    /** Aus dem Client-Tick setzen: der Client kennt seinen eigenen Combat-Timer. */
    public static void setClientInCombat(boolean inCombat) {
        clientInCombat = inCombat;
    }

    /** Gilt die Safezone gerade? Mit dem Final War verschwindet sie. */
    public static boolean isActive(Level level) {
        if (level.isClientSide()) {
            return clientActive;
        }

        MinecraftServer server = level.getServer();
        return server != null && GameState.get(server).getPhase() != Phase.FINAL_WAR;
    }

    public static boolean isActive(MinecraftServer server) {
        return GameState.get(server).getPhase() != Phase.FINAL_WAR;
    }

    /** Steht diese Entitaet in der Safezone? */
    public static boolean covers(Entity entity) {
        return isActive(entity.level()) && contains(entity.getX(), entity.getZ());
    }

    /**
     * Haelt Kaempfende und Monster draussen.
     *
     * <p>Wird aus {@code Entity.collide} heraus aufgerufen und laeuft damit auf beiden
     * Seiten — der Client bremst selbst ab, statt jeden Tick zurueckgezerrt zu werden.
     *
     * <p>Nur der Weg <em>hinein</em> ist gesperrt. Wer drinsteht und in einen Kampf
     * geraet, bleibt frei — sonst waere er eingesperrt statt geschuetzt. Dasselbe gilt fuer
     * ein Monster, das vor dieser Regel hereingekommen ist.
     *
     * @return die erlaubte Bewegung, oder {@code null}, wenn nichts zu beschneiden ist
     */
    @Nullable
    public static Vec3 limit(Entity entity, Vec3 movement) {
        if (!isActive(entity.level()) || !isBarred(entity)) {
            return null;
        }

        double[] allowed = slide(entity.getX(), entity.getZ(), movement.x, movement.z);
        return allowed == null ? null : new Vec3(allowed[0], movement.y, allowed[1]);
    }

    /**
     * Beschneidet eine waagerechte Bewegung so, dass sie nicht in die Zone hineinfuehrt.
     *
     * <p>Reine Rechnung, damit sie ohne Spielstart pruefbar ist — diese Methode laeuft fuer
     * jede bewegte Entitaet in jedem Tick, und ein Fehler darin legt die Fortbewegung der
     * halben Welt lahm. Genau das ist einmal passiert: die erste Fassung beschnitt
     * <em>ueberall</em> ausserhalb, nicht nur an der Grenze. Wer im Kampf war, kam von
     * keinem Punkt der Welt mehr Richtung 0,0, und kein Monster konnte sich der Mitte
     * naehern.
     *
     * @return die erlaubte Bewegung als {@code {dx, dz}}, oder {@code null}, wenn nichts zu
     *         beschneiden ist
     */
    @Nullable
    public static double[] slide(double x, double z, double dx, double dz) {
        // Weit weg ist nichts zu tun. Steht bewusst ganz vorn: der Rest kostet Wurzeln.
        double distance = Math.sqrt(x * x + z * z);
        if (distance > RADIUS + REACH || distance == 0.0) {
            return null;
        }

        // Wer drin ist, kommt raus. Gesperrt ist nur der Weg hinein.
        if (contains(x, z)) {
            return null;
        }

        // Und wer draussen bleibt, darf sich frei bewegen — auch auf die Grenze zu.
        if (!contains(x + dx, z + dz)) {
            return null;
        }

        // Nur der nach innen zeigende Anteil faellt weg, nicht die ganze Bewegung. Sonst
        // klebt man an der Wand fest, statt an ihr entlanggehen zu koennen.
        double outX = x / distance;
        double outZ = z / distance;
        double inward = dx * outX + dz * outZ;

        double slidX = dx - inward * outX;
        double slidZ = dz - inward * outZ;

        // Bei einem grossen Schritt kann die Sehne trotzdem in die Zone schneiden. Dann
        // wird das Ergebnis auf die Grenze zurueckgeschoben.
        double endX = x + slidX;
        double endZ = z + slidZ;
        double endDistance = Math.sqrt(endX * endX + endZ * endZ);

        if (endDistance < RADIUS && endDistance > 0.0) {
            double push = (RADIUS + EDGE_GAP) / endDistance;
            slidX = endX * push - x;
            slidZ = endZ * push - z;
        }

        return new double[] {slidX, slidZ};
    }

    /**
     * Wen haelt die Zone draußen?
     *
     * <p>Spieler nur im Kampf — sonst waere sie kein Treffpunkt. Monster immer: ein Ort,
     * an dem verhandelt wird, soll nachts nicht von Zombies belagert werden. Tiere duerfen
     * herein, die stoeren niemanden.
     */
    private static boolean isBarred(Entity entity) {
        if (entity instanceof Player player) {
            return !player.isCreative() && !player.isSpectator() && inCombat(player);
        }
        return entity.getType().getCategory() == MobCategory.MONSTER;
    }

    private static boolean inCombat(Player player) {
        return player.level().isClientSide()
                ? clientInCombat
                : CombatTracker.isInCombat(player.getUUID());
    }

    /**
     * Nimmt Angriffen in der Kugel die Wirkung.
     *
     * <p>Es genuegt, dass <em>einer</em> von beiden drinsteht. Tiere darf man drinnen
     * weiterhin schlachten — die Regel schuetzt Spieler, nicht jeden Grashalm.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !isActive(victim.level())) {
            return;
        }

        Entity attacker = event.getSource().getEntity();

        // Kein Lebewesen verletzt einen Spieler in der Zone — weder ein anderer Spieler noch
        // ein Monster, das hereingelaufen ist. Und keiner schlaegt aus der Zone heraus:
        // sonst waere sie kein neutraler Boden, sondern eine Schiessscharte.
        if (victim instanceof Player && attacker instanceof LivingEntity
                && (covers(victim) || covers(attacker))) {
            event.setCanceled(true);
            return;
        }

        // Feuer und Explosionen richten drinnen ebenfalls nichts aus — sonst waere die
        // Safezone mit einem Feuerzeug auszuhebeln.
        boolean elemental = event.getSource().is(DamageTypeTags.IS_FIRE)
                || event.getSource().is(DamageTypeTags.IS_EXPLOSION);
        if (elemental && covers(victim)) {
            event.setCanceled(true);
        }
    }

    /** Explosionen lassen die Kugel unberuehrt — weder Bloecke noch Lebewesen darin. */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!isActive(event.getLevel())) {
            return;
        }

        event.getAffectedBlocks().removeIf(pos -> contains(pos.getX() + 0.5, pos.getZ() + 0.5));
        event.getAffectedEntities().removeIf(SafeZone::covers);
    }

    /**
     * Laesst drinnen keine Monster entstehen.
     *
     * <p>Nur feindliche: Tiere duerfen weiterhin herkommen. Und nur beim <em>Entstehen</em>
     * — wer hereinlaeuft, laeuft herein. Ein Ort, an dem verhandelt wird, soll nachts nicht
     * von selbst voll Zombies stehen.
     *
     * <p>Angefasst wird der Abschluss des Spawnvorgangs, nicht das Betreten der Welt: so
     * bleiben Spawn-Eier, Spawner und Beschwoerungen unberuehrt, die jemand absichtlich
     * einsetzt.
     */
    public static void onSpawn(FinalizeSpawnEvent event) {
        if (event.getEntity().getType().getCategory() != MobCategory.MONSTER) {
            return;
        }

        if (isActive(event.getLevel().getLevel()) && contains(event.getX(), event.getZ())) {
            event.setSpawnCancelled(true);
        }
    }

    /** Kein Feuer legen, wo es ohnehin nicht brennen duerfte. */
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(Blocks.FIRE) || !(event.getLevel() instanceof Level level)) {
            return;
        }

        if (isActive(level) && contains(event.getPos().getX() + 0.5, event.getPos().getZ() + 0.5)) {
            event.setCanceled(true);
        }
    }

    /**
     * Sagt beim Uebertreten, woran man ist.
     *
     * <p>Eine unsichtbare Grenze, an der PvP aufhoert, muss man spueren koennen — gerade
     * wenn man jemanden verfolgt und ploetzlich nichts mehr trifft.
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        boolean inside = covers(player);
        boolean wasInside = INSIDE.contains(player.getUUID());

        if (inside != wasInside) {
            if (inside) {
                INSIDE.add(player.getUUID());
            } else {
                INSIDE.remove(player.getUUID());
            }
            player.displayClientMessage(
                    inside ? HeldenText.safeZoneEntered() : HeldenText.safeZoneLeft(), true);
            return;
        }

        denyEntry(player, inside);
    }

    /**
     * Sagt einem Kaempfenden vor der Kuppel, warum er nicht hineinkommt.
     *
     * <p>Sonst steht er an einer unsichtbaren Wand und haelt sie fuer einen Fehler. Nur in
     * Reichweite der Grenze, damit die Actionbar nicht dauerhaft belegt ist.
     */
    private static void denyEntry(ServerPlayer player, boolean inside) {
        if (inside || player.tickCount % DENIAL_GAP != 0
                || !CombatTracker.isInCombat(player.getUUID())) {
            return;
        }

        double distance = Math.sqrt(player.getX() * player.getX() + player.getZ() * player.getZ());

        if (distance < RADIUS + DENIAL_RANGE) {
            player.displayClientMessage(HeldenText.safeZoneDenied(), true);
        }
    }

    /** Beim Verlassen der Welt aufraeumen. */
    public static void forget(UUID uuid) {
        INSIDE.remove(uuid);
    }
}
