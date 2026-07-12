package edn.lakeopossmc.drivebysable;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

// --- MOD CONFIG DEF --- //
public class CableConfig {

    public static final CableConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    public final ModConfigSpec.BooleanValue shouldConsumeCables;

    private CableConfig(ModConfigSpec.Builder builder) {
        shouldConsumeCables = builder.define("shouldConsumeCables", false);
    }

    // * Build config and spec together
    static {
        Pair<CableConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(CableConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

}
