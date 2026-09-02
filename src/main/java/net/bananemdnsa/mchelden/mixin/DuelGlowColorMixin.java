package net.bananemdnsa.mchelden.mixin;

import net.bananemdnsa.mchelden.client.ClientState;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Faerbt den Umriss des Duellgegners blau.
 *
 * <p>Der Umriss nimmt die Teamfarbe der Entitaet. Ein eigenes Scoreboard-Team waere fuer
 * alle sichtbar und wuerde nebenbei Namensfarben umstellen — hier wird stattdessen nur die
 * Farbe zurueckgegeben, und auch das nur auf dem Client des Gegners.
 *
 * <p>Die Pruefung auf die Client-Seite ist kein Beiwerk: im Einzelspieler laeuft der
 * integrierte Server im selben Prozess und teilt sich die Klasse.
 */
@Mixin(Entity.class)
public abstract class DuelGlowColorMixin {
    /** Dasselbe Blau wie der Duell-Balken. */
    private static final int MCHELDEN$DUEL_GLOW = 0x4FA8E8;

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void mchelden$duelGlowColor(CallbackInfoReturnable<Integer> callback) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide() && ClientState.isDuelOpponent(entity)) {
            callback.setReturnValue(MCHELDEN$DUEL_GLOW);
        }
    }
}
