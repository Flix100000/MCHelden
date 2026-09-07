package net.bananemdnsa.mchelden.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Laesst Endermen in der Wueste auch bei Tageslicht entstehen.
 *
 * <p>Die Wueste ist in diesem Mod das Enderman-Gebiet: alle anderen Monster sind dort
 * entfernt. Nur spawnen Monster in Vanilla ausschliesslich im Dunkeln, und das ist keine
 * Frage der Wahrscheinlichkeit, sondern hart. Der Overworld-Dimensionstyp gibt fuer
 * {@code monster_spawn_light_level} eine Gleichverteilung von 0 bis 7 vor; an der
 * Oberflaeche steht am Tag eine Helligkeit von 15, und 15 liegt nie darunter. Ohne diesen
 * Handler ist die Wueste also den halben Tag lang schlicht leer — mangels Husks jetzt
 * leerer als in Vanilla.
 *
 * <p>Angefasst wird die Spawnpruefung, nicht der Enderman: {@code EnderMan} laesst sich
 * nicht umschreiben, ohne dass es auch fuer das End und den Rest der Welt gilt.
 *
 * <p><b>Es wird nur der Lichttest uebersprungen, nichts sonst.</b> {@code SUCCEED} hebt die
 * gesamte Platzierungspruefung auf, also auch den Block unter den Fuessen und den
 * Friedlich-Modus. Beides wird deswegen hier von Hand nachgeholt. Uebrig bleibt genau
 * {@code Monster#checkMonsterSpawnRules} ohne seinen Dunkelheitsteil — nicht "alles
 * erlaubt".
 *
 * <p>Die Rate bleibt, wie sie ist: dasselbe Gewicht, dieselben Rudel von vier, dieselbe
 * Mob-Obergrenze. Es faellt nur die Tageszeit als Bedingung weg.
 */
public final class DesertEndermen {

    private DesertEndermen() {
    }

    /**
     * Haengt sich in die Platzierungspruefung, dort wo der Lichttest sitzt.
     *
     * <p>Nur natuerliche Spawns. Spawner, Spawn-Eier und Beschwoerungen setzt jemand
     * absichtlich; die haben ihre eigenen Regeln und gehen diesen Weg gar nicht erst oder
     * sollen ihn nicht ueber uns abkuerzen.
     */
    public static void onSpawnPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType() != EntityType.ENDERMAN
                || event.getSpawnType() != MobSpawnType.NATURAL
                || event.getDefaultResult()) {
            return;
        }

        ServerLevelAccessor level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.getBiome(pos).is(Biomes.DESERT)) {
            return;
        }

        // Was Vanilla ausser dem Licht noch prueft. Ohne das koennte hier ein Enderman auf
        // Blaettern, auf Magma oder im Friedlich-Modus stehen.
        if (level.getDifficulty() == Difficulty.PEACEFUL
                || !Mob.checkMobSpawnRules(EntityType.ENDERMAN, level, event.getSpawnType(), pos,
                        event.getRandom())) {
            return;
        }

        event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.SUCCEED);
    }
}
