package net.bananemdnsa.mchelden.mixin;

import net.bananemdnsa.mchelden.client.ClientState;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Laesst den Duellgegner leuchten — aber nur fuer mich.
 *
 * <p>Ein Mixin ist hier noetig, weil der Vanilla-Leuchteffekt ein Entity-Flag ist und damit
 * fuer alle sichtbar waere. Die Entscheidung faellt stattdessen im Client, aus einer UUID,
 * die nur die beiden Duellanten haben: der Server schickt sie jeweils nur an den einen
 * Gegner. Niemand sonst sieht etwas, weil bei niemandem sonst etwas ankommt.
 */
@Mixin(Minecraft.class)
public abstract class DuelGlowMixin {

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void mchelden$duelGlow(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (ClientState.isDuelOpponent(entity)) {
            callback.setReturnValue(true);
        }
    }
}
