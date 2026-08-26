package net.bananemdnsa.mchelden.grave;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Das schwebende Namensschild über einem Grab, als Text-Display-Entität.
 *
 * <p>Ursprünglich habe ich den Text selbst im Block-Renderer gezeichnet. Das hat trotz
 * korrekter Daten, gültiger Schriftart und ausgegebenem Puffer nie etwas angezeigt, und drei
 * Erklärungsversuche haben nicht getragen. Minecraft bringt für schwebenden Text im Raum eine
 * eigene Entität mit, die sich um Ausrichtung zur Kamera, Sichtweite und Hintergrund selbst
 * kümmert — die zu benutzen ist verlässlicher, als eigenen Zeichencode zu reparieren, dessen
 * Fehler ich nicht finde.
 *
 * <p>Auf dem Schild stehen Name und Todeszeitpunkt. Die Zeit muss nachgezogen werden, das
 * übernimmt der Ticker des Grabes in grossen Abständen — die Anzeige geht in ganzen Minuten,
 * öfter als alle zehn Sekunden nachzusehen wäre also verschwendet.
 */
public final class GraveNameplate {
    /** Höhe über dem Grabstein, oberhalb des schwebenden Kopfes. */
    private static final double HEIGHT = 1.55;
    /** Vielfaches von 64 Blöcken. 0.2 ergibt gut zwölf Blöcke Sichtweite. */
    private static final float VIEW_RANGE = 0.2f;
    private static final int BACKGROUND = 0x50000000;

    private GraveNameplate() {
    }

    /** Setzt das Schild und liefert seine Kennung zurück, damit es später entfernt werden kann. */
    @Nullable
    public static UUID spawn(ServerLevel level, BlockPos pos, String ownerName) {
        if (ownerName.isEmpty()) {
            return null;
        }

        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.put("Pos", position(pos));

        tag.putString("text", Component.Serializer.toJson(text(ownerName, 0L), level.registryAccess()));
        tag.putString("billboard", "center");
        tag.putFloat("view_range", VIEW_RANGE);
        tag.putInt("background", BACKGROUND);
        tag.putByte("shadow", (byte) 1);

        Entity entity = EntityType.loadEntityRecursive(tag, level, spawned -> {
            level.addFreshEntity(spawned);
            return spawned;
        });
        return entity != null ? entity.getUUID() : null;
    }

    /**
     * Zieht den Todeszeitpunkt nach.
     *
     * <p>Die Setter der Entität sind nicht öffentlich. Der gangbare Weg ist, ihr den eigenen
     * Zustand zu entnehmen, darin nur den Text zu ersetzen und ihn zurückzuschreiben —
     * Position und Ausrichtung bleiben dabei unverändert, weil sie aus ihr selbst stammen.
     */
    public static void updateText(ServerLevel level, @Nullable UUID nameplateId,
                                  String ownerName, long ageInTicks) {
        if (nameplateId == null || ownerName.isEmpty()) {
            return;
        }

        Entity entity = level.getEntity(nameplateId);
        if (entity == null) {
            return;
        }

        CompoundTag tag = entity.saveWithoutId(new CompoundTag());
        tag.putString("text", Component.Serializer.toJson(text(ownerName, ageInTicks), level.registryAccess()));
        entity.load(tag);
    }

    /** Name in der ersten Zeile, wie lange der Tod her ist in der zweiten. */
    private static Component text(String ownerName, long ageInTicks) {
        int minutes = (int) Math.max(0, ageInTicks / (20 * 60));
        Component since = minutes < 1
                ? Component.translatable("mchelden.grave.just_now")
                : Component.translatable("mchelden.grave.minutes_ago", minutes);

        return Component.translatable("mchelden.grave.owner", ownerName)
                .copy()
                .append(Component.literal("\n"))
                .append(since);
    }

    /** Entfernt das Schild. Ohne das bliebe Text in der Luft stehen, wo kein Grab mehr ist. */
    public static void remove(ServerLevel level, @Nullable UUID nameplateId) {
        if (nameplateId == null) {
            return;
        }

        Entity entity = level.getEntity(nameplateId);
        if (entity != null) {
            entity.discard();
        }
    }

    private static ListTag position(BlockPos pos) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(pos.getX() + 0.5));
        list.add(DoubleTag.valueOf(pos.getY() + HEIGHT));
        list.add(DoubleTag.valueOf(pos.getZ() + 0.5));
        return list;
    }
}
