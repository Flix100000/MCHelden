package net.bananemdnsa.mchelden;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;

@Mod(MCHelden.MODID)
public class MCHelden {
    public static final String MODID = "mchelden";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MCHelden(IEventBus modEventBus, ModContainer modContainer) {
    }
}
