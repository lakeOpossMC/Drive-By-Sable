package edn.lakeopossmc.drivebysable.advancement;

import com.simibubi.create.AllItems;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlock;
import edn.lakeopossmc.drivebysable.blocks.IntegratedSensorBusBlockEntity;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.LinkedGearboxLinks;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.Objects;

// --- WHAT THE CODE REPORTS TO THE ADVANCEMENTS --- //
// * The advancements themselves are the files in data/drivebysable/advancement
// * Each name here is the "event" one of those files waits for
public final class CableAdvancements {

    public static final String FIRST_CONNECTION = "first_connection";
    public static final String LONG_CONNECTION = "long_connection";
    public static final String CROSS_LEVEL_CONNECTION = "cross_level_connection";
    public static final String ALL_SENSOR_CHANNELS = "all_sensor_channels";
    public static final String SENSOR_BUS_GOGGLES = "sensor_bus_goggles";
    public static final String TRANSCEIVER_POWERED = "transceiver_powered";
    public static final String TRANSCEIVER_DOUBLE_SPEED = "transceiver_double_speed";
    public static final String TRANSCEIVER_OPPOSED = "transceiver_opposed";
    public static final String TRANSCEIVER_OVERSTRESSED = "transceiver_overstressed";
    public static final String NETWORK_SAVED = "network_saved";
    public static final String HANDHELD_SIGNAL = "handheld_signal";
    public static final String NETWORK_CUT = "network_cut";
    public static final String CLIPBOARD_SECONDS = "clipboard_seconds";

    // * "At or near" the configured range: this share of it
    private static final double LONG_CONNECTION_SHARE = 0.9;

    // * With no range limit there is no maximum, so a fixed distance counts
    private static final int LONG_CONNECTION_UNLIMITED = 256;

    // * Connections one sneak click of the Cable Cutters has to remove for "Snip Snip"
    public static final int NETWORK_CUT_SIZE = 5;

    // * Honeyed Clipboards eaten for "Seconds?"
    private static final int CLIPBOARD_SECONDS_COUNT = 5;
    private static final String CLIPBOARDS_EATEN_KEY = "DriveBySableClipboardsEaten";

    // * Things no single player does (a network starting to turn) go to everyone this close
    private static final double NEARBY_RANGE = 32;

    // * How often a player wearing goggles is checked for looking at a Sensor Bus
    private static final int GOGGLE_CHECK_INTERVAL = 20;

    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, DriveBySableMod.MOD_ID);

    public static final DeferredHolder<CriterionTrigger<?>, CableEventTrigger> EVENT =
            TRIGGERS.register("event", CableEventTrigger::new);

    private CableAdvancements() {
    }

    public static void register(final IEventBus modEventBus) {
        TRIGGERS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(CableAdvancements::onPlayerTick);
        CableAdvancementLayout.register();
    }

    public static void award(@Nullable final Player player, final String event) {
        if (player instanceof final ServerPlayer serverPlayer) {
            EVENT.get().trigger(serverPlayer, event);
        }
    }

    // * Measured where the block appears in the world, so it works from inside a sublevel
    public static void awardNearby(@Nullable final Level level, final BlockPos pos, final String event) {
        if (!(level instanceof final ServerLevel serverLevel)) {
            return;
        }
        for (final ServerPlayer player : serverLevel.players()) {
            if (CableNetworkManager.worldSpaceDistanceSqr(level, pos, player.blockPosition())
                    <= NEARBY_RANGE * NEARBY_RANGE) {
                EVENT.get().trigger(player, event);
            }
        }
    }

    //#region // --- CONNECTIONS MADE BY HAND --- //
    public static void onConnectionMade(final ServerPlayer player, final BlockPos source, final BlockPos sink) {
        final Level level = player.level();
        award(player, FIRST_CONNECTION);

        if (!sameLevel(level, source, sink)) {
            award(player, CROSS_LEVEL_CONNECTION);
        }

        final double far = longConnectionDistance(level, source, sink);
        if (far > 0 && CableNetworkManager.worldSpaceDistanceSqr(level, source, sink) >= far * far) {
            award(player, LONG_CONNECTION);
        }

        if (level.getBlockState(source).getBlock() instanceof IntegratedSensorBusBlock) {
            final CableNetworkManager manager = CableNetworkManager.get(level);
            if (IntegratedSensorBusBlockEntity.TELEMETRY_CHANNELS.stream()
                    .allMatch(channel -> manager.hasSinks(source, channel))) {
                award(player, ALL_SENSOR_CHANNELS);
            }
        }
    }

    // * Transceivers always have a range of their own
    private static double longConnectionDistance(final Level level, final BlockPos source, final BlockPos sink) {
        final boolean enforced = CableConfig.CONFIG.rangeLimitEnforced.get();
        final int general = CableConfig.CONFIG.rangeLimit.get();
        if (LinkedGearboxLinks.isGearbox(level, source) && LinkedGearboxLinks.isGearbox(level, sink)) {
            final int own = CableConfig.CONFIG.linkedGearboxRange.get();
            return Math.min(LONG_CONNECTION_UNLIMITED, (enforced ? Math.min(general, own) : own) * LONG_CONNECTION_SHARE);
        }
        return enforced ? general * LONG_CONNECTION_SHARE : LONG_CONNECTION_UNLIMITED;
    }

    // * Both in the world, or both in the same sublevel
    private static boolean sameLevel(final Level level, final BlockPos first, final BlockPos second) {
        final SubLevel firstSubLevel = Sable.HELPER.getContaining(level, first);
        final SubLevel secondSubLevel = Sable.HELPER.getContaining(level, second);
        if (firstSubLevel == null || secondSubLevel == null) {
            return firstSubLevel == null && secondSubLevel == null;
        }
        return Objects.equals(firstSubLevel.getUniqueId(), secondSubLevel.getUniqueId());
    }
    //#endregion

    //#region // --- HONEYED CLIPBOARDS EATEN --- //
    // * Kept in the part of the player's data that survives dying
    public static void onClipboardEaten(final ServerPlayer player) {
        final CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        final int eaten = Math.min(persisted.getInt(CLIPBOARDS_EATEN_KEY) + 1, CLIPBOARD_SECONDS_COUNT);
        persisted.putInt(CLIPBOARDS_EATEN_KEY, eaten);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);

        if (eaten >= CLIPBOARD_SECONDS_COUNT) {
            award(player, CLIPBOARD_SECONDS);
        }
    }
    //#endregion

    //#region // --- LOOKING AT A SENSOR BUS THROUGH GOGGLES --- //
    private static void onPlayerTick(final PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof final ServerPlayer player)
                || player.tickCount % GOGGLE_CHECK_INTERVAL != 0
                || !AllItems.GOGGLES.isIn(player.getItemBySlot(EquipmentSlot.HEAD))) {
            return;
        }

        final HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK
                && hit instanceof final BlockHitResult blockHit
                && player.level().getBlockState(blockHit.getBlockPos()).getBlock() instanceof IntegratedSensorBusBlock) {
            award(player, SENSOR_BUS_GOGGLES);
        }
    }
    //#endregion
}