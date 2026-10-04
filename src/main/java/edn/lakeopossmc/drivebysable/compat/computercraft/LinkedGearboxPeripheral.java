package edn.lakeopossmc.drivebysable.compat.computercraft;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.ryanhcode.sable.Sable;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlock;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxBlockEntity;
import edn.lakeopossmc.drivebysable.blocks.LinkedGearboxOutputMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// --- COMPUTER CRAFT API FOR THE LINKED GEARBOX --- //
// * Lives in the compat package so nothing outside it names a CC type
public final class LinkedGearboxPeripheral implements IPeripheral {

    public static final String TYPE = "radio_kinetic_transceiver";

    private final LinkedGearboxBlockEntity owner;

    // * Attached computers
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();

    public LinkedGearboxPeripheral(final LinkedGearboxBlockEntity owner) {
        this.owner = owner;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Object getTarget() {
        return owner;
    }

    @Override
    public boolean equals(@Nullable final IPeripheral other) {
        return other instanceof final LinkedGearboxPeripheral peripheral && peripheral.owner == owner;
    }

    //#region // --- COMPUTERS --- //
    // * attach and detach can come from the computer thread
    @Override
    public void attach(final IComputerAccess computer) {
        computers.add(computer);
    }

    // * The last computer letting go switches the transceiver back on
    @Override
    public void detach(final IComputerAccess computer) {
        computers.remove(computer);
        if (!computers.isEmpty()) {
            return;
        }

        final Level level = owner.getLevel();
        if (level instanceof final ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> {
                if (computers.isEmpty() && !owner.isRemoved()) {
                    owner.setComputerDisabled(false);
                }
            });
        }
    }

    boolean hasComputers() {
        return !computers.isEmpty();
    }

    // * Pushed to every attached computer
    void queueEvent(final String event, final Object... arguments) {
        for (final IComputerAccess computer : computers) {
            final Object[] full = new Object[arguments.length + 1];
            full[0] = computer.getAttachmentName();
            System.arraycopy(arguments, 0, full, 1, arguments.length);
            computer.queueEvent(event, full);
        }
    }
    //#endregion

    //#region // --- THIS TRANSCEIVER --- //
    // * "driver": turned by its own side and adding to the network
    // * "receiver": turned by the network
    // * "opposed": turned by its own side the other way to the network, left out
    // * "idle": unlinked, switched off, or nothing in the network is turning
    @LuaFunction(mainThread = true)
    public final String getRole() throws LuaException {
        return roleName(owner().getRole());
    }

    // * Has any link, by Cable or by frequency
    @LuaFunction(mainThread = true)
    public final boolean isLinked() throws LuaException {
        return owner().isLinked();
    }

    // * Switched off by redstone, the power frequency or setEnabled(false)
    @LuaFunction(mainThread = true)
    public final boolean isPowered() throws LuaException {
        return LinkedGearboxBlock.isDisconnected(owner().getBlockState());
    }

    @LuaFunction(mainThread = true)
    public final boolean isOverstressed() throws LuaException {
        return owner().isOverStressed();
    }

    // * RPM, negative when turning the other way. 0 while overstressed
    @LuaFunction(mainThread = true)
    public final float getSpeed() throws LuaException {
        return owner().getSpeed();
    }

    // * The three numbers the goggles show, in SU at the current speed
    @LuaFunction(mainThread = true)
    public final float getStressImpact() throws LuaException {
        return owner().getStressImpactNow();
    }

    // * Driver: what it adds to the network. Receiver: its share of the network's pool
    @LuaFunction(mainThread = true)
    public final float getStressCapacity() throws LuaException {
        return owner().getStressCapacityNow();
    }

    // * Receiver: what its side is drawing from that share. 0 otherwise
    @LuaFunction(mainThread = true)
    public final float getStressUsed() throws LuaException {
        return owner().getStressUsedNow();
    }
    //#endregion

    //#region // --- OUTPUT MODE --- //
    // * Returns the speed multiple (1 or 2) and whether it is reversed
    // * Only used while this transceiver is a receiver
    @LuaFunction(mainThread = true)
    public final MethodResult getMode() throws LuaException {
        final int ratio = owner().getOutputMode().ratio();
        return MethodResult.of(Math.abs(ratio), ratio < 0);
    }

    // * setMode(2) is double speed, setMode(1, true) or setMode(-1) is input speed reversed
    @LuaFunction(mainThread = true)
    public final boolean setMode(final int multiple, final Optional<Boolean> reversed) throws LuaException {
        final int size = Math.abs(multiple);
        if (size != 1 && size != 2) {
            throw new LuaException("Expected a speed multiple of 1 or 2 (negative for reversed)");
        }

        final int ratio = size * (reversed.orElse(multiple < 0) ? -1 : 1);
        for (final LinkedGearboxOutputMode mode : LinkedGearboxOutputMode.values()) {
            if (mode.ratio() == ratio) {
                owner().setOutputMode(mode);
                return true;
            }
        }
        throw new LuaException("No output mode turns at " + ratio + " times the input speed");
    }
    //#endregion

    //#region // --- SWITCHING IT OFF --- //
    // * setEnabled(false) takes the transceiver out of its network
    // * It is not saved and is let go when the last computer detaches
    @LuaFunction(mainThread = true)
    public final boolean setEnabled(final boolean enabled) throws LuaException {
        owner().setComputerDisabled(!enabled);
        return true;
    }

    // * False only after setEnabled(false). Redstone is reported by isPowered
    @LuaFunction(mainThread = true)
    public final boolean isEnabled() throws LuaException {
        return !owner().isComputerDisabled();
    }
    //#endregion

    //#region // --- THE NETWORK --- //
    // * Every transceiver linked to this one that is loaded and in range, this one included:
    // * { x, y, z, role, speed, powered, isSelf }, positions as they appear in the world
    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getNetwork() throws LuaException {
        final LinkedGearboxBlockEntity self = owner();
        final Level level = self.getLevel();

        final List<Map<String, Object>> network = new ArrayList<>();
        for (final LinkedGearboxBlockEntity member : self.getNetworkMembers()) {
            final BlockPos world = BlockPos.containing(
                    Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(member.getBlockPos())));

            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("x", world.getX());
            entry.put("y", world.getY());
            entry.put("z", world.getZ());
            entry.put("role", roleName(member.getRole()));
            entry.put("speed", member.getSpeed());
            entry.put("powered", LinkedGearboxBlock.isDisconnected(member.getBlockState()));
            entry.put("isSelf", member == self);
            network.add(entry);
        }
        return network;
    }

    // * { transceivers, drivers, receivers, opposed, idle, speed, capacity, cost }
    // * speed: the lead driver's RPM, which the receivers follow
    // * capacity: SU the drivers add to the network in total
    // * cost: SU the network's transceivers cost their drivers in total
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getNetworkStats() throws LuaException {
        final List<LinkedGearboxBlockEntity> members = owner().getNetworkMembers();

        int drivers = 0;
        int receivers = 0;
        int opposed = 0;
        int idle = 0;
        float speed = 0;
        float capacity = 0;
        float cost = 0;
        for (final LinkedGearboxBlockEntity member : members) {
            switch (member.getRole()) {
                case DRIVER -> drivers++;
                case RECEIVER -> receivers++;
                case OPPOSED -> opposed++;
                case IDLE -> idle++;
            }
            if (member.isLeadDriver()) {
                speed = member.getSpeed();
            }
            capacity += member.getPoolContribution();
            cost += member.getDrivenLoad();
        }

        final Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("transceivers", members.size());
        stats.put("drivers", drivers);
        stats.put("receivers", receivers);
        stats.put("opposed", opposed);
        stats.put("idle", idle);
        stats.put("speed", speed);
        stats.put("capacity", capacity);
        stats.put("cost", cost);
        return stats;
    }
    //#endregion

    // * A main thread task can still be queued when the block is broken or its sublevel goes
    private LinkedGearboxBlockEntity owner() throws LuaException {
        if (owner.isRemoved() || owner.getLevel() == null) {
            throw new LuaException("The Transceiver is no longer there");
        }
        return owner;
    }

    static String roleName(final LinkedGearboxBlockEntity.Role role) {
        return role.name().toLowerCase(Locale.ROOT);
    }
}