package net.bananemdnsa.mchelden.client.hud;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

/**
 * Eine Kachel des Bounty-Rades: ein Spielerkopf oder das Fragezeichen.
 *
 * <p>Kasten und Gluecksrad zeichnen dieselbe Kachel in verschiedenen Groessen. Wuerde jeder
 * sie selbst zeichnen, liefen die beiden mit der Zeit auseinander — und der Kopf, der aus
 * dem Band in den Kasten wandert, muss unterwegs derselbe bleiben.
 */
public final class PlayerHead {
    /** Kachel ohne Ziel. Steht vor dem Roll im Kasten und beim Leer-Ausgehen im Band. */
    private static final Component UNKNOWN = Component.translatable("mchelden.bounty.unknown");

    /** Die Vanilla-Schrift ist neun Pixel hoch — daraus ergibt sich der Massstab je Kachelgroesse. */
    private static final float FONT_HEIGHT = 9f;

    /** Skins von Spielern, die gerade nicht online sind. */
    private static final Map<UUID, PlayerSkin> RESOLVED = new ConcurrentHashMap<>();
    /** Laufende Anfragen, damit jede UUID nur einmal nachgeschlagen wird. */
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private PlayerHead() {
    }

    /**
     * Zeichnet eine Kachel des Streifens.
     *
     * <p>Schlaegt bewusst nichts nach: der Streifen wird mit erfundenen UUIDs aufgefuellt,
     * und fuer jede davon eine Anfrage an Mojang zu stellen waere Unfug.
     *
     * @param uuid der Spieler, oder {@code null} fuer das Fragezeichen
     * @param color Fragezeichen-Farbe; Koepfe zeichnen ihre eigene
     */
    public static void draw(GuiGraphics graphics, @Nullable UUID uuid, int x, int y, int size, int color) {
        if (uuid == null) {
            drawUnknown(graphics, x, y, size, color);
            return;
        }
        PlayerFaceRenderer.draw(graphics, skinOf(uuid), x, y, size);
    }

    /**
     * Zeichnet das eigene Bounty-Ziel und holt seinen Skin notfalls nach.
     *
     * <p>Das Ziel ist die meiste Zeit offline — beim Tageslimit von einer Stunde ist das der
     * Regelfall, nicht die Ausnahme. Ohne das Nachschlagen wuerde der Kopf im HUD seine
     * Identitaet wechseln, je nachdem wer gerade eingeloggt ist.
     */
    public static void drawTarget(GuiGraphics graphics, @Nullable UUID uuid, int x, int y, int size, int color) {
        if (uuid != null) {
            request(uuid);
        }
        draw(graphics, uuid, x, y, size, color);
    }

    /**
     * Der Skin des Spielers, mit dem Vanilla-Standardskin als Rueckfall.
     *
     * <p>Die Spielerliste geht vor: sie ist immer aktuell, auch wenn jemand seinen Skin
     * gerade gewechselt hat.
     */
    public static PlayerSkin skinOf(UUID uuid) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection != null ? connection.getPlayerInfo(uuid) : null;
        if (info != null) {
            return info.getSkin();
        }

        PlayerSkin resolved = RESOLVED.get(uuid);
        return resolved != null ? resolved : DefaultPlayerSkin.get(uuid);
    }

    /**
     * Schlaegt einen Skin ueber das Profil nach, denselben Weg, den ein Spielerkopf-Item geht.
     *
     * <p>Laeuft im Hintergrund; bis die Antwort da ist, steht der Standardskin. Genau so
     * verhalten sich auch Vanilla-Schaedel.
     */
    private static void request(UUID uuid) {
        if (RESOLVED.containsKey(uuid) || !PENDING.add(uuid)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        SkullBlockEntity.fetchGameProfile(uuid).thenAcceptAsync(profile -> profile.ifPresent(
                resolved -> minecraft.getSkinManager().getOrLoad(resolved)
                        .thenAccept(skin -> RESOLVED.put(uuid, skin))), minecraft);
    }

    /** Beim Verlassen einer Welt aufrufen. */
    public static void forget() {
        RESOLVED.clear();
        PENDING.clear();
    }

    /** Das Fragezeichen, auf Kachelgroesse gebracht und mittig gesetzt. */
    private static void drawUnknown(GuiGraphics graphics, int x, int y, int size, int color) {
        Font font = Minecraft.getInstance().font;
        float scale = size / FONT_HEIGHT;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + size / 2f, y + size / 2f, 0f);
        pose.scale(scale, scale, 1f);
        graphics.drawString(font, UNKNOWN, -font.width(UNKNOWN) / 2, -font.lineHeight / 2, color, false);
        pose.popPose();
    }
}
