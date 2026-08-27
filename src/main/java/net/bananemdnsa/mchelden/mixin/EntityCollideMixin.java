package net.bananemdnsa.mchelden.mixin;

import net.bananemdnsa.mchelden.world.DividerWall;
import net.bananemdnsa.mchelden.world.SafeZone;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Laesst Trennwand und Safezone sich wie Waende anfuehlen.
 *
 * <p>Ein Mixin ist hier noetig, weil Minecraft pro Dimension nur eine Worldborder kennt und
 * NeoForge keinen Haken fuer zusaetzliche Kollisionsflaechen anbietet.
 *
 * <p><b>Der Einstiegspunkt ist mit Bedacht gewaehlt.</b> {@code collide} laeuft auf Server
 * <em>und</em> Client. Wuerde die Wand nur serverseitig durchgesetzt, liefe der Spieler
 * lokal hindurch und wuerde jeden Tick zurueckgeholt — das bekannte Gummiband an
 * unsichtbaren Grenzen. So bremst der Client von sich aus ab, genau wie an der Weltgrenze.
 *
 * <p>Projektile gehen hier nicht durch: Pfeile und Perlen bewegen sich ueber ihren eigenen
 * Strahlentest statt ueber die Kollision. Die faengt {@code DividerWall.onEntityTick} ab.
 */
@Mixin(Entity.class)
public abstract class EntityCollideMixin {

    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void mchelden$stopAtWall(Vec3 movement, CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        Vec3 original = callback.getReturnValue();
        Vec3 allowed = original;

        Vec3 atWall = DividerWall.limit(entity, allowed);
        if (atWall != null) {
            allowed = atWall;
        }

        Vec3 atZone = SafeZone.limit(entity, allowed);
        if (atZone != null) {
            allowed = atZone;
        }

        if (allowed != original) {
            callback.setReturnValue(allowed);
        }
    }
}
