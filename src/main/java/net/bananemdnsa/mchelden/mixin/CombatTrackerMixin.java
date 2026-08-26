package net.bananemdnsa.mchelden.mixin;

import net.bananemdnsa.mchelden.combat.CombatDeathMessage;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Schreibt die Todesnachricht um, wenn jemand im Combat-Timer stirbt.
 *
 * <p>Ein Mixin ist hier nötig, weil NeoForge keinen Haken für die Todesnachricht anbietet.
 * Der Einstieg liegt am Anfang der Methode und bricht nur ab, wenn tatsächlich eine eigene
 * Nachricht vorliegt — in allen anderen Fällen läuft Vanilla unverändert weiter.
 */
@Mixin(CombatTracker.class)
public abstract class CombatTrackerMixin {
    @Shadow
    @Final
    private LivingEntity mob;

    @Inject(method = "getDeathMessage", at = @At("HEAD"), cancellable = true)
    private void mchelden$combatDeathMessage(CallbackInfoReturnable<Component> callback) {
        if (!(this.mob instanceof ServerPlayer player)) {
            return;
        }

        Component message = CombatDeathMessage.of(player);
        if (message != null) {
            callback.setReturnValue(message);
        }
    }
}
