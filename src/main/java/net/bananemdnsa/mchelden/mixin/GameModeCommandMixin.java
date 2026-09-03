package net.bananemdnsa.mchelden.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.GameModeCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Haelt jede Rueckmeldung zu einem Spielmodus-Wechsel zurueck.
 *
 * <p>Vanilla schreibt hier drei Nachrichten: die Bestaetigung an den Ausfuehrenden, den
 * Hinweis an den betroffenen Spieler und — ueber {@code sendSuccess} mit Protokollierung —
 * die graue Admin-Zeile an alle anderen Operatoren. Auf dem Server soll ein
 * Spielmodus-Wechsel still bleiben, deshalb faellt die ganze Methode aus.
 *
 * <p>Ein Mixin ist hier noetig, weil die Methode privat im Befehl steckt und NeoForge keinen
 * Haken dafuer anbietet. Die Alternative — die Spielregel {@code sendCommandFeedback}
 * abzuschalten — wuerde die Rueckmeldung <em>aller</em> Befehle verschlucken.
 */
@Mixin(GameModeCommand.class)
public abstract class GameModeCommandMixin {

    @Inject(method = "logGamemodeChange", at = @At("HEAD"), cancellable = true)
    private static void mchelden$silentGameModeChange(
            CommandSourceStack source, ServerPlayer player, GameType gameType, CallbackInfo callback) {
        callback.cancel();
    }
}
