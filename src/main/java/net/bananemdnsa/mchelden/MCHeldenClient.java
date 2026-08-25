package net.bananemdnsa.mchelden;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// Wird auf dedizierten Servern nicht geladen. Client-Code ist hier sicher.
@Mod(value = MCHelden.MODID, dist = Dist.CLIENT)
public class MCHeldenClient {
    public MCHeldenClient(ModContainer container) {
    }
}
