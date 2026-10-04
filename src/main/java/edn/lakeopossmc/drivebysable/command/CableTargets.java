package edn.lakeopossmc.drivebysable.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlockEntity;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.cable.LinkedGearboxFrequencies;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// --- TURNS A @target INTO ENDPOINTS --- //
public final class CableTargets {

    // * Further than hand reach
    private static final double LOOK_REACH = 64.0;

    private static final SimpleCommandExceptionType NONE_FOUND = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.target.none"));
    private static final SimpleCommandExceptionType NOT_LOOKING = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.target.not_looking"));
    private static final DynamicCommandExceptionType RADIUS_TOO_LARGE = new DynamicCommandExceptionType(
            limit -> Component.translatableEscape("commands.drivebysable.target.radius_too_large", limit));
    private static final DynamicCommandExceptionType NO_SUCH_MODULE = new DynamicCommandExceptionType(
            module -> Component.translatableEscape("commands.drivebysable.info.no_such_module", module));
    private static final SimpleCommandExceptionType SINGLE_ONLY = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.target.single_only"));
    private static final SimpleCommandExceptionType NO_MODULES_HERE = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.target.no_modules"));
    private static final SimpleCommandExceptionType NO_TRANSCEIVERS = new SimpleCommandExceptionType(
            Component.translatable("commands.drivebysable.target.none.transceiver"));

    private CableTargets() {
    }

    // * Everything a command needs after the target has been read
    public record Resolved(
            ServerLevel level,
            CableNetworkManager manager,
            CableTarget target,
            @Nullable String module,
            List<CableEndpoint> endpoints
    ) {
        public boolean modules() {
            return module != null;
        }
    }

    public static Resolved resolve(
            final CommandSourceStack source,
            final CableTarget target,
            @Nullable final String module
    ) throws CommandSyntaxException {
        final ServerLevel level = source.getLevel();
        final CableNetworkManager manager = CableNetworkManager.get(level);

        final List<BlockPos> blocks = blocksFor(source, level, manager, target);
        final List<CableEndpoint> endpoints = new ArrayList<>();

        for (final BlockPos pos : blocks) {
            if (target.side().wantsSources()) {
                endpoints.addAll(endpointsOn(level, manager, pos, module, true));
            }
            if (target.side().wantsOutputs()) {
                endpoints.addAll(endpointsOn(level, manager, pos, module, false));
            }
        }

        if (endpoints.isEmpty()) {
            throw (module == null ? NONE_FOUND : NO_MODULES_HERE).create();
        }
        return new Resolved(level, manager, target, module, endpoints);
    }

    // * Commands that report on one thing only accept @coord, @look and @n
    public static void requireSingle(final CableTarget target) throws CommandSyntaxException {
        if (!target.kind().single()) {
            throw SINGLE_ONLY.create();
        }
    }

    //#region // --- WHICH BLOCKS --- //
    private static List<BlockPos> blocksFor(
            final CommandSourceStack source,
            final ServerLevel level,
            final CableNetworkManager manager,
            final CableTarget target
    ) throws CommandSyntaxException {
        return switch (target.kind()) {
            case COORD -> List.of(atCoords(level, manager, target.coords()));
            case LOOK -> List.of(lookedAt(source, level, manager));
            case NEAREST -> {
                final List<BlockPos> sorted = sortedByDistance(source, level, manager, target);
                if (sorted.isEmpty()) {
                    throw NONE_FOUND.create();
                }
                yield List.of(sorted.get(0));
            }
            case RADIUS -> withinRadius(source, level, manager, target);
            case ALL -> sortedByDistance(source, level, manager, target);
        };
    }

    private static BlockPos atCoords(
            final ServerLevel level,
            final CableNetworkManager manager,
            final BlockPos typed
    ) throws CommandSyntaxException {
        // * CableChannels leaves Linked Gearbox links out
        if (CableChannels.isEndpoint(manager, typed)) {
            return typed;
        }

        final Vec3 centre = Vec3.atCenterOf(typed);
        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(
                centre.x - 0.5, centre.y - 0.5, centre.z - 0.5,
                centre.x + 0.5, centre.y + 0.5, centre.z + 0.5))) {
            final BlockPos local = BlockPos.containing(subLevel.logicalPose().transformPositionInverse(centre));
            if (CableChannels.isEndpoint(manager, local)) {
                return local;
            }
        }
        throw NONE_FOUND.create();
    }

    private static BlockPos lookedAt(
            final CommandSourceStack source,
            final ServerLevel level,
            final CableNetworkManager manager
    ) throws CommandSyntaxException {
        final ServerPlayer player = source.getPlayerOrException();

        // * Sable routes block raycasts through sublevels, so this lands on ship blocks too
        final HitResult hit = player.pick(LOOK_REACH, 1.0F, false);
        if (!(hit instanceof final BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            throw NOT_LOOKING.create();
        }

        final BlockPos pos = blockHit.getBlockPos();
        if (!CableChannels.isEndpoint(manager, pos)) {
            throw NONE_FOUND.create();
        }
        return pos.immutable();
    }

    private static List<BlockPos> withinRadius(
            final CommandSourceStack source,
            final ServerLevel level,
            final CableNetworkManager manager,
            final CableTarget target
    ) throws CommandSyntaxException {
        final int limit = CableConfig.CONFIG.commandRadiusLimit.get();
        if (target.radius() > limit) {
            throw RADIUS_TOO_LARGE.create(limit);
        }

        final Vec3 origin = origin(source);
        final double maxDistanceSqr = (double) target.radius() * target.radius();

        final List<BlockPos> inside = new ArrayList<>();
        for (final BlockPos pos : sortedByDistance(source, level, manager, target)) {
            if (SourceText.worldCentre(level, pos).distanceToSqr(origin) > maxDistanceSqr) {
                break;
            }
            inside.add(pos);
        }
        if (inside.isEmpty()) {
            throw NONE_FOUND.create();
        }
        return inside;
    }

    private static List<BlockPos> sortedByDistance(
            final CommandSourceStack source,
            final ServerLevel level,
            final CableNetworkManager manager,
            final CableTarget target
    ) {
        final List<BlockPos> blocks = new ArrayList<>();
        for (final BlockPos pos : manager.getEndpointPositions()) {
            if (target.side().wantsSources() && CableChannels.isSource(manager, pos)) {
                blocks.add(pos);
                continue;
            }
            if (target.side().wantsOutputs() && CableChannels.isOutput(manager, pos)) {
                blocks.add(pos);
            }
        }
        return byDistance(source, level, blocks);
    }

    // * Nearest to whoever ran the command first
    private static List<BlockPos> byDistance(
            final CommandSourceStack source,
            final ServerLevel level,
            final List<BlockPos> blocks
    ) {
        final Vec3 origin = origin(source);
        final Map<BlockPos, Double> distances = new HashMap<>(blocks.size());
        for (final BlockPos pos : blocks) {
            distances.put(pos, SourceText.worldCentre(level, pos).distanceToSqr(origin));
        }
        blocks.sort(Comparator.<BlockPos>comparingDouble(distances::get));
        return blocks;
    }

    private static Vec3 origin(final CommandSourceStack source) {
        return Sable.HELPER.projectOutOfSubLevel(source.getLevel(), source.getPosition());
    }
    //#endregion

    //#region // --- TRANSCEIVERS --- //
    // * @target[transceiver]: Linked Gearboxes
    public static List<BlockPos> resolveTransceivers(
            final CommandSourceStack source,
            final CableTarget target
    ) throws CommandSyntaxException {
        final ServerLevel level = source.getLevel();
        final CableNetworkManager manager = CableNetworkManager.get(level);

        return switch (target.kind()) {
            case COORD -> List.of(transceiverAtCoords(level, manager, target.coords()));
            case LOOK -> {
                final BlockHitResult hit = lookHit(source);
                if (!isLinkedTransceiver(level, manager, hit.getBlockPos())) {
                    throw NO_TRANSCEIVERS.create();
                }
                yield List.of(hit.getBlockPos().immutable());
            }
            case NEAREST -> {
                final List<BlockPos> sorted = byDistance(source, level, linkedTransceivers(level, manager));
                if (sorted.isEmpty()) {
                    throw NO_TRANSCEIVERS.create();
                }
                yield List.of(sorted.get(0));
            }
            case RADIUS -> {
                final int limit = CableConfig.CONFIG.commandRadiusLimit.get();
                if (target.radius() > limit) {
                    throw RADIUS_TOO_LARGE.create(limit);
                }

                final Vec3 origin = origin(source);
                final double maxDistanceSqr = (double) target.radius() * target.radius();
                final List<BlockPos> inside = new ArrayList<>();
                for (final BlockPos pos : byDistance(source, level, linkedTransceivers(level, manager))) {
                    if (SourceText.worldCentre(level, pos).distanceToSqr(origin) > maxDistanceSqr) {
                        break;
                    }
                    inside.add(pos);
                }
                if (inside.isEmpty()) {
                    throw NO_TRANSCEIVERS.create();
                }
                yield inside;
            }
            case ALL -> {
                final List<BlockPos> sorted = byDistance(source, level, linkedTransceivers(level, manager));
                if (sorted.isEmpty()) {
                    throw NO_TRANSCEIVERS.create();
                }
                yield sorted;
            }
        };
    }

    private static boolean isLinkedTransceiver(
            final ServerLevel level,
            final CableNetworkManager manager,
            final BlockPos pos
    ) {
        if (CableChannels.hasGearboxLinks(manager, pos)) {
            return true;
        }
        return level.isLoaded(pos)
                && level.getBlockEntity(pos) instanceof final LinkedGearboxBlockEntity gearbox
                && gearbox.isLinked();
    }

    private static List<BlockPos> linkedTransceivers(final ServerLevel level, final CableNetworkManager manager) {
        final Set<BlockPos> linked = new LinkedHashSet<>();
        for (final BlockPos pos : manager.getEndpointPositions()) {
            if (CableChannels.hasGearboxLinks(manager, pos)) {
                linked.add(pos);
            }
        }
        linked.addAll(LinkedGearboxFrequencies.linked(level));
        return new ArrayList<>(linked);
    }

    private static BlockPos transceiverAtCoords(
            final ServerLevel level,
            final CableNetworkManager manager,
            final BlockPos typed
    ) throws CommandSyntaxException {
        if (isLinkedTransceiver(level, manager, typed)) {
            return typed;
        }

        final Vec3 centre = Vec3.atCenterOf(typed);
        for (final SubLevel subLevel : Sable.HELPER.getAllIntersecting(level, new BoundingBox3d(
                centre.x - 0.5, centre.y - 0.5, centre.z - 0.5,
                centre.x + 0.5, centre.y + 0.5, centre.z + 0.5))) {
            final BlockPos local = BlockPos.containing(subLevel.logicalPose().transformPositionInverse(centre));
            if (isLinkedTransceiver(level, manager, local)) {
                return local;
            }
        }
        throw NO_TRANSCEIVERS.create();
    }

    private static BlockHitResult lookHit(final CommandSourceStack source) throws CommandSyntaxException {
        final ServerPlayer player = source.getPlayerOrException();

        final HitResult hit = player.pick(LOOK_REACH, 1.0F, false);
        if (!(hit instanceof final BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            throw NOT_LOOKING.create();
        }
        return blockHit;
    }
    //#endregion

    private static List<CableEndpoint> endpointsOn(
            final ServerLevel level,
            final CableNetworkManager manager,
            final BlockPos pos,
            @Nullable final String module,
            final boolean source
    ) throws CommandSyntaxException {
        if (module == null) {
            final boolean plays = source ? CableChannels.isSource(manager, pos) : CableChannels.isOutput(manager, pos);
            return plays ? List.of(new CableEndpoint(pos, "", source)) : List.of();
        }

        final List<CableEndpoint> matching = new ArrayList<>();
        for (final CableEndpoint endpoint : CableEndpoint.at(level, manager, pos, source)) {
            if (!endpoint.hasModule()) {
                continue;
            }
            if (ModuleArgument.isAny(module) || endpoint.module().equalsIgnoreCase(module)) {
                matching.add(endpoint);
            }
        }

        if (matching.isEmpty() && !ModuleArgument.isAny(module)
                && !SourceModules.of(level, pos).contains(module)) {
            throw NO_SUCH_MODULE.create(module);
        }
        return matching;
    }
}