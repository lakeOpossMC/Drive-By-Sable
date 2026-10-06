package edn.lakeopossmc.drivebysable.blocks;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.infrastructure.config.AllConfigs;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.advancement.CableAdvancements;
import edn.lakeopossmc.drivebysable.CableConfig;
import edn.lakeopossmc.drivebysable.cable.LinkedGearboxFrequencies;
import edn.lakeopossmc.drivebysable.cable.LinkedGearboxLinks;
import edn.lakeopossmc.drivebysable.compat.computercraft.ComputerCraftCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// --- BLOCK ENTITY FOR LINKED GEARBOX --- //
// * Works like a wireless Torsion Spring
public class LinkedGearboxBlockEntity extends GeneratingKineticBlockEntity {

    public enum Role {
        IDLE, DRIVER, RECEIVER,
        OPPOSED
    }

    // * How often (ticks) links and roles are re-checked on the server
    private static final int LINK_UPDATE_INTERVAL = 4;
    private static final int OVERSTRESS_AWARD_TICKS = 20;
    private static final float EPSILON = 0.01F;
    private static final float SPEED_EPSILON = 1.0E-3F;
    private static final String LINKED_KEY = "Linked";
    private static final String PARTNERS_KEY = "Partners";
    private static final String ROLE_KEY = "LinkRole";
    private static final String DRIVER_KEY = "LinkDriver";
    private static final String SPEED_KEY = "LinkSpeed";
    private static final String CAPACITY_KEY = "LinkCapacity";
    private static final String LOAD_KEY = "LinkLoad";
    private static final String TRANSFER_KEY = "LinkTransfer";
    private static final String FLOW_KEY = "LinkFlow";
    private static final String CAP_KEY = "LinkCap";
    private static final String AWARDED_KEY = "LinkAwarded";

    @Nullable
    private LinkedGearboxModeBehaviour modeBehaviour;

    private boolean awardedReceiving;
    private boolean awardedDoubled;
    private boolean awardedOpposed;
    private boolean awardedOverstressed;
    private int overstressedTicks;
    @Nullable
    private LinkBehaviour frequency;
    @Nullable
    private LinkedGearboxPowerLink powerLink;
    private int wirelessSignal;

    // * ComputerCraft peripheral
    @Nullable
    private final Object peripheral;
    private boolean computerDisabled;
    private boolean reportedOverstressed;
    @Nullable
    private Set<BlockPos> reportedNetwork;

    private boolean linked;
    private Set<BlockPos> partners = Set.of();
    private Set<BlockPos> activePartners = Set.of();
    @Nullable
    private LinkedGearboxFrequencies.Key registeredKey;

    private Role role = Role.IDLE;
    @Nullable
    private BlockPos driverPos;
    private int receiverCount = 1;
    private float linkSpeed;
    private float capacityPerRpm;
    private float drivenLoad;
    private float transferLoad;
    private float poolShare;
    private float linkFlow;
    private float linkCap;

    private int linkUpdateCooldown;

    public LinkedGearboxBlockEntity(final BlockPos pos, final BlockState state) {
        this(CableBlockEntities.LINKED_GEARBOX.get(), pos, state);
    }

    public LinkedGearboxBlockEntity(final BlockEntityType<?> type, final BlockPos pos, final BlockState state) {
        super(type, pos, state);
        this.peripheral = ComputerCraftCompat.newTransceiverPeripheral(this);
    }

    //#region // --- BEHAVIOURS --- //
    @Override
    public void addBehaviours(final List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        modeBehaviour = new LinkedGearboxModeBehaviour(this);
        modeBehaviour.onlyActiveWhen(() -> linked && role != Role.DRIVER && role != Role.OPPOSED);
        behaviours.add(modeBehaviour);

        frequency = LinkBehaviour.receiver(this,
                ValueBoxTransform.Dual.makeSlots(LinkedGearboxFrequencySlot::new),
                signal -> {
                });
        behaviours.add(frequency);

        powerLink = new LinkedGearboxPowerLink(this,
                ValueBoxTransform.Dual.makeSlots(LinkedGearboxFrequencySlot::back),
                this::setWirelessSignal);
        behaviours.add(powerLink);
    }

    // * Server side
    private void setWirelessSignal(final int signal) {
        if (signal == wirelessSignal) {
            return;
        }
        wirelessSignal = signal;
        if (level != null && !level.isClientSide) {
            LinkedGearboxBlock.updatePower(level, worldPosition);
        }
    }

    public boolean isWirelesslyPowered() {
        return wirelessSignal > 0;
    }

    //#region // --- COMPUTERCRAFT --- //
    // * Everything LinkedGearboxPeripheral reads or does
    @Nullable
    public Object getPeripheral() {
        return peripheral;
    }

    // * Switched off by a computer: drops out of its network
    public void setComputerDisabled(final boolean disabled) {
        if (computerDisabled == disabled) {
            return;
        }
        computerDisabled = disabled;
        if (level != null && !level.isClientSide) {
            LinkedGearboxBlock.updatePower(level, worldPosition);
        }
    }

    public boolean isComputerDisabled() {
        return computerDisabled;
    }

    public Role getRole() {
        return role;
    }

    public boolean isLeadDriver() {
        return isLead();
    }

    // * Same as the mode panel
    public void setOutputMode(final LinkedGearboxOutputMode mode) {
        if (modeBehaviour != null) {
            modeBehaviour.setValue(mode.ordinal());
        }
    }

    // * The three goggle numbers, in SU at the current speed
    public float getStressImpactNow() {
        return calculateStressApplied() * Math.abs(getTheoreticalSpeed());
    }

    // * Receiver: its share of the pool. Driver: what it adds to the pool
    public float getStressCapacityNow() {
        return role == Role.RECEIVER
                ? calculateAddedStressCapacity() * Math.abs(getTheoreticalSpeed())
                : role == Role.DRIVER ? linkCap : 0;
    }

    // * Receiver: what its side is drawing from its share
    public float getStressUsedNow() {
        return role == Role.RECEIVER ? linkFlow : 0;
    }

    // * Driver: SU it adds to the pool, and its part of the network's bill
    public float getPoolContribution() {
        return role == Role.DRIVER ? contribution() : 0;
    }

    public float getDrivenLoad() {
        return role == Role.DRIVER ? drivenLoad : 0;
    }

    public List<LinkedGearboxBlockEntity> getNetworkMembers() {
        return level == null || level.isClientSide ? List.of(this) : group();
    }

    // * Only a change counts, and what was already awarded is saved
    private void awardAdvancements() {
        final boolean receiving = role == Role.RECEIVER;
        final boolean doubled = receiving && Math.abs(getOutputMode().ratio()) == 2;
        final boolean opposed = role == Role.OPPOSED;

        if (receiving && !awardedReceiving) {
            CableAdvancements.awardNearby(level, worldPosition, CableAdvancements.TRANSCEIVER_POWERED);
        }
        if (doubled && !awardedDoubled) {
            CableAdvancements.awardNearby(level, worldPosition, CableAdvancements.TRANSCEIVER_DOUBLE_SPEED);
        }
        if (opposed && !awardedOpposed) {
            CableAdvancements.awardNearby(level, worldPosition, CableAdvancements.TRANSCEIVER_OPPOSED);
        }

        awardedReceiving = receiving;
        awardedDoubled = doubled;
        awardedOpposed = opposed;
    }

    private void reportOverstress() {
        final boolean overstressed = isOverStressed();
        // * A side can be overstressed for a tick or two while the pool is shared out again, which does not count
        if (!overstressed) {
            overstressedTicks = 0;
            awardedOverstressed = false;
        } else if (!awardedOverstressed && ++overstressedTicks >= OVERSTRESS_AWARD_TICKS) {
            CableAdvancements.awardNearby(level, worldPosition, CableAdvancements.TRANSCEIVER_OVERSTRESSED);
            awardedOverstressed = true;
        }

        if (overstressed != reportedOverstressed) {
            reportedOverstressed = overstressed;
            ComputerCraftCompat.queueTransceiverEvent(
                    this, ComputerCraftCompat.TRANSCEIVER_OVERSTRESSED_EVENT, overstressed);
        }
    }

    // * The network is only walked while a computer is listening
    private void reportNetwork() {
        if (!ComputerCraftCompat.hasTransceiverComputers(this)) {
            reportedNetwork = null;
            return;
        }

        final Set<BlockPos> members = new HashSet<>();
        for (final LinkedGearboxBlockEntity member : group()) {
            members.add(member.getBlockPos());
        }
        if (reportedNetwork != null && !members.equals(reportedNetwork)) {
            ComputerCraftCompat.queueTransceiverEvent(
                    this, ComputerCraftCompat.TRANSCEIVER_NETWORK_EVENT, members.size());
        }
        reportedNetwork = members;
    }
    //#endregion

    @Nullable
    public LinkedGearboxPowerLink getPowerLink() {
        return powerLink;
    }

    public LinkedGearboxOutputMode getOutputMode() {
        return modeBehaviour == null ? LinkedGearboxOutputMode.INPUT_SPEED : modeBehaviour.getMode();
    }

    public boolean isLinked() {
        return linked;
    }

    // * Configured partners as last synced, used by the client highlight
    public Set<BlockPos> getPartners() {
        return partners;
    }

    @Override
    public boolean addToGoggleTooltip(final List<Component> tooltip, final boolean isPlayerSneaking) {
        boolean added = false;

        final float impact = calculateStressApplied();
        if (StressImpact.isEnabled() && !Mth.equal(impact, 0)) {
            CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);
            addStressImpactStats(tooltip, impact);
            added = true;
        }

        final float capacity = role == Role.RECEIVER
                ? calculateAddedStressCapacity() * Math.abs(getTheoreticalSpeed())
                : role == Role.DRIVER ? linkCap : 0;
        final boolean generator = StressImpact.isEnabled() && linked && capacity > 0;
        if (generator) {
            if (added) {
                tooltip.add(Component.empty());
            }
            CreateLang.translate("gui.goggles.generator_stats").forGoggles(tooltip);
            CreateLang.translate("tooltip.capacityProvided").style(ChatFormatting.GRAY).forGoggles(tooltip);
            CreateLang.number(capacity)
                    .translate("generic.unit.stress")
                    .style(ChatFormatting.AQUA)
                    .space()
                    .add(CreateLang.translate("gui.goggles.at_current_speed").style(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 1);
            added = true;
        }

        if (!linked) {
            return added;
        }

        if (role != Role.DRIVER && role != Role.OPPOSED) {
            if (!generator && added) {
                tooltip.add(Component.empty());
            }
            CreateLang.builder()
                    .add(Component.translatable("drivebysable.linked_gearbox.goggles.output_speed"))
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);
            CreateLang.builder()
                    .add(getOutputMode().goggleText())
                    .style(ChatFormatting.GOLD)
                    .forGoggles(tooltip, 1);
            added = true;
        }

        if (added) {
            tooltip.add(Component.empty());
        }
        CreateLang.builder()
                .add(Component.translatable(role == Role.RECEIVER
                        ? "drivebysable.linked_gearbox.goggles.receiver"
                        : "drivebysable.linked_gearbox.goggles.transmitter"))
                .forGoggles(tooltip);
        final String state = switch (role) {
            case DRIVER -> "adding";
            case RECEIVER -> "receiving";
            case OPPOSED -> "opposed";
            case IDLE -> "idle";
        };
        final ChatFormatting colour = switch (role) {
            case DRIVER -> ChatFormatting.GREEN;
            case RECEIVER -> ChatFormatting.GREEN;
            case OPPOSED -> ChatFormatting.RED;
            case IDLE -> ChatFormatting.GRAY;
        };
        CreateLang.builder()
                .add(Component.translatable("drivebysable.linked_gearbox.goggles." + state).withStyle(colour))
                .add(Component.translatable("drivebysable.linked_gearbox.goggles." + state + "_rest")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);
        return true;
    }
    //#endregion

    //#region // --- TICK --- //
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        if (--linkUpdateCooldown <= 0) {
            linkUpdateCooldown = LINK_UPDATE_INTERVAL;
            updateFrequencyRegistration();
            updatePartners();
            updateRole();
            reportNetwork();
            awardAdvancements();
        }
        reportOverstress();

        if (role == Role.RECEIVER) {
            followDriver();
        }
        if (role == Role.DRIVER) {
            updateDriver();
        }
    }

    private void updateFrequencyRegistration() {
        final LinkedGearboxFrequencies.Key key = LinkedGearboxFrequencies.keyOf(frequency);
        if (!Objects.equals(key, registeredKey)) {
            LinkedGearboxFrequencies.move(level, worldPosition, registeredKey, key);
            registeredKey = key;
        }
    }

    private void updatePartners() {
        final Set<BlockPos> configured = new LinkedHashSet<>(LinkedGearboxLinks.cableNeighbours(level, worldPosition));
        configured.addAll(LinkedGearboxFrequencies.partners(level, worldPosition, registeredKey));

        final boolean nowLinked = !configured.isEmpty();
        if (nowLinked != linked || !configured.equals(partners)) {
            linked = nowLinked;
            partners = Set.copyOf(configured);
            setChanged();
            sendData();
        }

        final Set<BlockPos> active = new LinkedHashSet<>();
        for (final BlockPos partner : configured) {
            if (!level.isLoaded(partner)
                    || !(level.getBlockEntity(partner) instanceof LinkedGearboxBlockEntity)
                    || !LinkedGearboxLinks.checkPlacement(level, worldPosition, partner).isSuccess()) {
                continue;
            }
            active.add(partner.immutable());
        }
        activePartners = Set.copyOf(active);
    }

    public void refreshLinks() {
        if (level == null || level.isClientSide) {
            return;
        }
        updateFrequencyRegistration();
        updatePartners();
        linkUpdateCooldown = 0;
    }

    public boolean clearLinkingFrequency() {
        if (level == null || level.isClientSide || frequency == null) {
            return false;
        }
        final LinkedGearboxFrequencies.Key key = LinkedGearboxFrequencies.keyOf(frequency);
        if (key == null || LinkedGearboxFrequencies.partners(level, worldPosition, key).isEmpty()) {
            return false;
        }
        frequency.setFrequency(true, net.minecraft.world.item.ItemStack.EMPTY);
        frequency.setFrequency(false, net.minecraft.world.item.ItemStack.EMPTY);
        updateFrequencyRegistration();
        setChanged();
        sendData();
        return true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && !level.isClientSide && registeredKey != null) {
            LinkedGearboxFrequencies.move(level, worldPosition, registeredKey, null);
            registeredKey = null;
        }
    }
    //#endregion

    //#region // --- ROLES --- //
    private List<LinkedGearboxBlockEntity> group() {
        final List<LinkedGearboxBlockEntity> members = new ArrayList<>();
        final Set<BlockPos> seen = new HashSet<>();
        final Deque<LinkedGearboxBlockEntity> queue = new ArrayDeque<>();
        seen.add(worldPosition);
        queue.add(this);
        while (!queue.isEmpty()) {
            final LinkedGearboxBlockEntity current = queue.poll();
            members.add(current);
            for (final BlockPos partner : current.activePartners) {
                if (seen.add(partner) && level.isLoaded(partner)
                        && level.getBlockEntity(partner) instanceof final LinkedGearboxBlockEntity other) {
                    queue.add(other);
                }
            }
        }
        return members;
    }

    private boolean turnedExternally() {
        return role != Role.RECEIVER && hasSource() && getTheoreticalSpeed() != 0;
    }

    private record GroupState(@Nullable LinkedGearboxBlockEntity lead,
                              List<LinkedGearboxBlockEntity> drivers,
                              List<LinkedGearboxBlockEntity> opposed,
                              List<LinkedGearboxBlockEntity> receivers) {
    }

    private GroupState groupState() {
        final List<LinkedGearboxBlockEntity> members = group();
        final Set<Long> receiverNetworks = new HashSet<>();
        for (final LinkedGearboxBlockEntity member : members) {
            if (member.role == Role.RECEIVER && member.network != null) {
                receiverNetworks.add(member.network);
            }
        }

        final List<LinkedGearboxBlockEntity> selfTurned = new ArrayList<>();
        final List<LinkedGearboxBlockEntity> receivers = new ArrayList<>();
        for (final LinkedGearboxBlockEntity member : members) {
            if (LinkedGearboxBlock.isDisconnected(member.getBlockState())) {
                continue;
            }
            if (!member.turnedExternally()) {
                receivers.add(member);
            } else if (member.network == null || !receiverNetworks.contains(member.network)) {
                selfTurned.add(member);
            }
        }

        final LinkedGearboxBlockEntity lead = selfTurned.stream()
                .max(Comparator.<LinkedGearboxBlockEntity>comparingDouble(member -> Math.abs(member.getTheoreticalSpeed()))
                        .thenComparing(Comparator.<LinkedGearboxBlockEntity>comparingLong(
                                member -> member.getBlockPos().asLong()).reversed()))
                .orElse(null);
        if (lead == null) {
            return new GroupState(null, List.of(), List.of(), List.of());
        }

        final List<LinkedGearboxBlockEntity> drivers = new ArrayList<>();
        final List<LinkedGearboxBlockEntity> opposed = new ArrayList<>();
        final float leadSign = Math.signum(lead.getTheoreticalSpeed());
        for (final LinkedGearboxBlockEntity member : selfTurned) {
            (Math.signum(member.getTheoreticalSpeed()) == leadSign ? drivers : opposed).add(member);
        }
        return new GroupState(lead, drivers, opposed, receivers);
    }

    private void updateRole() {
        if (activePartners.isEmpty() || LinkedGearboxBlock.isDisconnected(getBlockState())) {
            becomeIdle();
            return;
        }

        if (role == Role.RECEIVER && hasOwnPower()) {
            becomeIdle();
            return;
        }

        final GroupState state = groupState();
        if (state.lead() == null) {
            becomeIdle();
            return;
        }

        final BlockPos lead = state.lead().getBlockPos();
        if (state.drivers().contains(this)) {
            setRole(Role.DRIVER, state.lead() == this ? null : lead, 1);
        } else if (state.opposed().contains(this)) {
            setRole(Role.OPPOSED, lead, 1);
        } else if (state.receivers().contains(this)) {
            setRole(Role.RECEIVER, lead, Math.max(1, state.receivers().size()));
        } else {
            becomeIdle();
        }
    }

    private void regroup() {
        final List<LinkedGearboxBlockEntity> members = group();
        for (final LinkedGearboxBlockEntity member : members) {
            if (member.role != Role.RECEIVER) {
                member.updateRole();
            }
        }
        for (final LinkedGearboxBlockEntity member : members) {
            if (member.role == Role.RECEIVER) {
                member.updateRole();
            }
        }
        for (final LinkedGearboxBlockEntity member : members) {
            if (member.role == Role.DRIVER) {
                member.updateDriver();
            }
        }
    }

    private boolean hasOwnPower() {
        if (!hasNetwork()) {
            return false;
        }
        for (final KineticBlockEntity source : getOrCreateNetwork().sources.keySet()) {
            if (source == this || source.getGeneratedSpeed() == 0) {
                continue;
            }
            if (source instanceof final LinkedGearboxBlockEntity gearbox && gearbox.role == Role.RECEIVER) {
                continue;
            }
            return true;
        }
        return false;
    }

    // * The fastest driver of the group
    private boolean isLead() {
        return role == Role.DRIVER && driverPos == null;
    }

    private void becomeIdle() {
        setRole(Role.IDLE, null, 1);
    }

    private void setRole(final Role newRole, @Nullable final BlockPos newDriver, final int receivers) {
        final boolean changed = newRole != role || !Objects.equals(newDriver, driverPos) || receivers != receiverCount;
        final Role oldRole = role;
        role = newRole;
        driverPos = newDriver == null ? null : newDriver.immutable();
        receiverCount = receivers;
        if (!changed) {
            return;
        }

        // * Leaving a role clears what it added to the networks
        if (oldRole == Role.RECEIVER && newRole != Role.RECEIVER) {
            setGeneration(0, 0);
        }
        if (oldRole == Role.DRIVER && newRole != Role.DRIVER) {
            setDrivenLoad(0);
            setTransfer(0, 0);
        }
        if (newRole == Role.IDLE || newRole == Role.OPPOSED) {
            setLinkStats(0, 0);
        }
        if (newRole != oldRole) {
            ComputerCraftCompat.queueTransceiverEvent(
                    this, ComputerCraftCompat.TRANSCEIVER_ROLE_EVENT, newRole.name().toLowerCase(java.util.Locale.ROOT));
        }
        setChanged();
        sendData();
    }

    public void stopLinkRole() {
        if (level != null && !level.isClientSide) {
            becomeIdle();
        }
    }
    //#endregion

    //#region // --- RECEIVER --- //
    @Nullable
    private LinkedGearboxBlockEntity driver() {
        if (driverPos == null || level == null || !level.isLoaded(driverPos)) {
            return null;
        }
        return level.getBlockEntity(driverPos) instanceof final LinkedGearboxBlockEntity driver
                && driver.role == Role.DRIVER ? driver : null;
    }

    private void followDriver() {
        LinkedGearboxBlockEntity driver = driver();
        // * The lead stopped, look for another driver straight away
        // * stopping until each member's next update
        if (driver == null || !driver.turnedExternally()) {
            regroup();
            driver = role == Role.RECEIVER ? driver() : null;
        }
        // * No driver left
        if (driver == null || (driver.network != null && driver.network.equals(network) && linkSpeed != 0)) {
            becomeIdle();
            return;
        }

        final float input = driver.getTheoreticalSpeed();
        final float ratio = getOutputMode().ratio();
        final float speed = input * ratio;

        if (Math.abs(speed) > AllConfigs.server().kinetics.maxRotationSpeed.get()) {
            level.destroyBlock(worldPosition, true);
            return;
        }

        final float share = driver.allocationFor(worldPosition, receiverCount);
        final float capacity = speed == 0 ? 0 : divideUp(share, Math.abs(speed));

        setGeneration(speed, capacity);

        final float demand = hasNetwork() ? getOrCreateNetwork().calculateStress() : 0;
        setLinkStats(demand, share);
    }

    private void setGeneration(final float speed, final float capacity) {
        final boolean speedChanged = Math.abs(speed - linkSpeed) > SPEED_EPSILON;
        final boolean capacityChanged = capacity != capacityPerRpm;
        linkSpeed = speed;
        capacityPerRpm = capacity;

        if (speedChanged) {
            updateGeneratedRotation();
            return;
        }
        if (capacityChanged) {
            sendData();
        }
        shareCapacity();
    }

    // * Checked against what the side holds every time
    // * more capacity is the only thing that can get it moving again
    private void shareCapacity() {
        if (!hasNetwork() || getGeneratedSpeed() == 0) {
            return;
        }
        final KineticNetwork kineticNetwork = getOrCreateNetwork();
        final Float stored = kineticNetwork.sources.get(this);
        final float wanted = calculateAddedStressCapacity();
        if (stored != null && stored != wanted) {
            kineticNetwork.updateCapacityFor(this, wanted);
        }
    }

    @Override
    public float getGeneratedSpeed() {
        return role == Role.RECEIVER ? linkSpeed : 0;
    }

    @Override
    public float calculateAddedStressCapacity() {
        final float capacity = role == Role.RECEIVER ? capacityPerRpm : 0;
        this.lastCapacityProvided = capacity;
        return capacity;
    }
    //#endregion

    //#region // --- DRIVER --- //
    private final Map<BlockPos, Float> allocations = new HashMap<>();
    private float groupPool;

    private float allocationFor(final BlockPos receiver, final int receivers) {
        final Float allocated = allocations.get(receiver);
        return allocated != null ? allocated : groupPool / Math.max(1, receivers);
    }

    // * What this driver adds to the pool
    private float contribution() {
        return poolShare;
    }

    // * Every driver runs this for its own part of the bill
    private void updateDriver() {
        final GroupState state = groupState();
        final LinkedGearboxBlockEntity lead = state.lead();
        if (lead == null || !state.drivers().contains(this)) {
            return;
        }

        final Map<Long, KineticNetwork> networks = new LinkedHashMap<>();
        final Map<Long, List<BlockPos>> receiversOf = new HashMap<>();
        float cost = 0;
        for (final LinkedGearboxBlockEntity member : state.receivers()) {
            if (member.role != Role.RECEIVER || !lead.getBlockPos().equals(member.driverPos)) {
                continue;
            }
            cost += (float) BlockStressValues.getImpact(member.getBlockState().getBlock()) * Math.abs(member.linkSpeed);
            if (!member.hasNetwork() || member.network.equals(lead.network)) {
                continue;
            }
            networks.putIfAbsent(member.network, member.getOrCreateNetwork());
            receiversOf.computeIfAbsent(member.network, ignored -> new ArrayList<>()).add(member.getBlockPos());
        }

        float speedSum = 0;
        for (final LinkedGearboxBlockEntity driver : state.drivers()) {
            final float speed = Math.abs(driver.getTheoreticalSpeed());
            speedSum += speed;
            cost += (float) BlockStressValues.getImpact(driver.getBlockState().getBlock()) * speed;
        }
        setDrivenLoad(costShare(this, cost, speedSum, state.drivers().size()));
        setLinkStats(0, poolShare);
        if (lead != this) {
            return;
        }

        // * Each side driving the group offers what it has spare once the bill is paid, up to the limit
        final double perRpm = CableConfig.CONFIG.linkedGearboxStressPerRpm.get();
        final Map<Long, List<LinkedGearboxBlockEntity>> driversOn = new LinkedHashMap<>();
        for (final LinkedGearboxBlockEntity driver : state.drivers()) {
            if (driver.hasNetwork()) {
                driversOn.computeIfAbsent(driver.network, ignored -> new ArrayList<>()).add(driver);
            }
        }

        final Map<Long, Float> offered = new HashMap<>();
        float pool = 0;
        for (final Map.Entry<Long, List<LinkedGearboxBlockEntity>> entry : driversOn.entrySet()) {
            final KineticNetwork side = entry.getValue().getFirst().getOrCreateNetwork();
            float spare = side.calculateCapacity() - side.calculateStress();
            float limit = 0;
            for (final LinkedGearboxBlockEntity driver : entry.getValue()) {
                final float speed = Math.abs(driver.getTheoreticalSpeed());
                final Float billed = side.members.get(driver);
                if (billed != null) {
                    spare += billed * speed;
                }
                spare -= costShare(driver, cost, speedSum, state.drivers().size());
                limit += perRpm > 0 ? (float) (perRpm * speed) : Float.POSITIVE_INFINITY;
            }
            final float offer = Math.max(0, Math.min(limit, spare));
            offered.put(entry.getKey(), offer);
            pool += offer;
        }

        groupPool = pool;
        final Map<Long, Float> demand = new HashMap<>();
        for (final Map.Entry<Long, KineticNetwork> entry : networks.entrySet()) {
            demand.put(entry.getKey(), entry.getValue().calculateStress());
        }

        final List<Long> order = new ArrayList<>(networks.keySet());
        order.sort(Comparator.comparingDouble(demand::get));
        final Map<Long, Float> given = new HashMap<>();
        float remaining = pool;
        for (int i = 0; i < order.size(); i++) {
            final long id = order.get(i);
            final float give = Math.min(demand.get(id), remaining / (order.size() - i));
            given.put(id, give);
            remaining -= give;
        }
        // * What is left over is split evenly, so the far sides always add up to the pool
        final float spare = order.isEmpty() ? 0 : Math.max(0, remaining) / order.size();

        allocations.clear();
        float drawn = 0;
        for (final long id : order) {
            final List<BlockPos> receivers = receiversOf.get(id);
            final float allocated = given.get(id) + spare;
            final float perReceiver = divideUp(allocated, receivers.size());
            for (final BlockPos receiver : receivers) {
                allocations.put(receiver, perReceiver);
            }
            drawn += drawnBy(networks.get(id), receivers, demand.get(id), allocated);
        }

        // * What the far sides draw is billed back to the sides that offered it
        for (final Map.Entry<Long, List<LinkedGearboxBlockEntity>> entry : driversOn.entrySet()) {
            final float offer = offered.get(entry.getKey());
            final float bill = pool <= 0 ? 0
                    : driversOn.size() == 1 ? Math.min(offer, drawn)
                    : Math.min(offer, drawn * offer / pool);

            final List<LinkedGearboxBlockEntity> drivers = entry.getValue();
            float speedOn = 0;
            for (final LinkedGearboxBlockEntity driver : drivers) {
                speedOn += Math.abs(driver.getTheoreticalSpeed());
            }
            for (final LinkedGearboxBlockEntity driver : drivers) {
                final float part = drivers.size() == 1 || speedOn <= 0 ? 1.0F / drivers.size()
                        : Math.abs(driver.getTheoreticalSpeed()) / speedOn;
                driver.setTransfer(bill * part, offer * part);
            }
            settle(drivers.getFirst().getOrCreateNetwork(), drivers);
        }
    }

    private static float costShare(final LinkedGearboxBlockEntity driver, final float cost,
                                   final float speedSum, final int drivers) {
        return CableConfig.CONFIG.linkedGearboxCostBySpeed.get()
                ? (speedSum > 0 ? cost * Math.abs(driver.getTheoreticalSpeed()) / speedSum : 0)
                : cost / drivers;
    }

    // * What one receiving side takes of its allocation, stalled or not
    // * Where another group feeds the same side, only this group's part of it
    private float drawnBy(final KineticNetwork side, final List<BlockPos> receivers,
                          final float demand, final float allocated) {
        float ours = 0;
        for (final BlockPos pos : receivers) {
            if (level.getBlockEntity(pos) instanceof final LinkedGearboxBlockEntity receiver
                    && side.sources.containsKey(receiver)) {
                ours += side.getActualCapacityOf(receiver);
            }
        }
        final float others = side.calculateCapacity() - ours;
        if (others < EPSILON) {
            return Math.min(demand, allocated);
        }
        final float total = others + allocated;
        return total > 0 ? allocated * Math.min(1, demand / total) : 0;
    }

    // * A side used right up to its capacity must not tip into overstress
    private static void settle(final KineticNetwork side, final List<LinkedGearboxBlockEntity> drivers) {
        for (int pass = 0; pass < 3; pass++) {
            float excess = side.calculateStress() - side.calculateCapacity();
            if (excess <= 0) {
                return;
            }
            excess *= 2;
            for (final LinkedGearboxBlockEntity driver : drivers) {
                final float cut = Math.min(driver.transferLoad, excess);
                if (cut > 0) {
                    driver.setTransfer(driver.transferLoad - cut, driver.poolShare);
                    excess -= cut;
                }
            }
        }
    }

    private void setDrivenLoad(final float load) {
        if (load != drivenLoad) {
            final boolean notable = Math.abs(load - drivenLoad) > EPSILON;
            drivenLoad = load;
            setChanged();
            if (notable) {
                sendData();
            }
        }
        billSide();
    }

    private void setTransfer(final float load, final float share) {
        poolShare = share;
        if (load != transferLoad) {
            final boolean notable = Math.abs(load - transferLoad) > EPSILON;
            transferLoad = load;
            setChanged();
            if (notable) {
                sendData();
            }
        }
        billSide();
    }

    private void billSide() {
        if (!hasNetwork()) {
            return;
        }
        final KineticNetwork kineticNetwork = getOrCreateNetwork();
        final Float stored = kineticNetwork.members.get(this);
        final float wanted = calculateStressApplied();
        if (stored != null && stored != wanted) {
            kineticNetwork.updateStressFor(this, wanted);
        }
    }

    @Override
    public float calculateStressApplied() {
        if (role == Role.RECEIVER) {
            this.lastStressApplied = 0;
            return 0;
        }
        final float base = super.calculateStressApplied();
        final float speed = Math.abs(getTheoreticalSpeed());
        if (role != Role.DRIVER || speed == 0) {
            return base;
        }
        final float applied = divideDown(drivenLoad + transferLoad, speed);
        this.lastStressApplied = applied;
        return applied;
    }

    // * SU spread over a speed or a count
    private static float divideUp(final float amount, final float by) {
        final float result = amount / by;
        return result * by < amount ? Math.nextUp(result) : result;
    }

    private static float divideDown(final float amount, final float by) {
        final float result = amount / by;
        return result * by > amount ? Math.nextDown(result) : result;
    }
    //#endregion

    private void setLinkStats(final float flow, final float cap) {
        if (Math.abs(flow - linkFlow) > 0.5F || Math.abs(cap - linkCap) > 0.5F) {
            linkFlow = flow;
            linkCap = cap;
            sendData();
        }
    }

    //#region // --- SAVE / SYNC --- //
    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean(LINKED_KEY, linked);
        tag.putString(ROLE_KEY, role.name());
        if (driverPos != null) {
            tag.put(DRIVER_KEY, NbtUtils.writeBlockPos(driverPos));
        }
        tag.putInt("LinkReceivers", receiverCount);
        tag.putFloat(SPEED_KEY, linkSpeed);
        tag.putFloat(CAPACITY_KEY, capacityPerRpm);
        tag.putFloat(LOAD_KEY, drivenLoad);
        tag.putFloat(TRANSFER_KEY, transferLoad);
        if (clientPacket) {
            tag.putLongArray(PARTNERS_KEY, partners.stream().mapToLong(BlockPos::asLong).toArray());
            tag.putFloat(FLOW_KEY, linkFlow);
            tag.putFloat(CAP_KEY, linkCap);
        } else {
            tag.putByte(AWARDED_KEY, (byte) ((awardedReceiving ? 1 : 0) | (awardedDoubled ? 2 : 0)
                    | (awardedOpposed ? 4 : 0) | (awardedOverstressed ? 8 : 0)));
        }
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        linked = tag.getBoolean(LINKED_KEY);
        try {
            role = Role.valueOf(tag.getString(ROLE_KEY));
        } catch (final IllegalArgumentException ignored) {
            role = Role.IDLE;
        }
        driverPos = NbtUtils.readBlockPos(tag, DRIVER_KEY).orElse(null);
        receiverCount = Math.max(1, tag.getInt("LinkReceivers"));
        linkSpeed = tag.getFloat(SPEED_KEY);
        capacityPerRpm = tag.getFloat(CAPACITY_KEY);
        drivenLoad = tag.getFloat(LOAD_KEY);
        transferLoad = tag.getFloat(TRANSFER_KEY);
        if (clientPacket) {
            final Set<BlockPos> synced = new LinkedHashSet<>();
            for (final long key : tag.getLongArray(PARTNERS_KEY)) {
                synced.add(BlockPos.of(key));
            }
            partners = Set.copyOf(synced);
            linkFlow = tag.getFloat(FLOW_KEY);
            linkCap = tag.getFloat(CAP_KEY);
        } else {
            final byte awarded = tag.getByte(AWARDED_KEY);
            awardedReceiving = (awarded & 1) != 0;
            awardedDoubled = (awarded & 2) != 0;
            awardedOpposed = (awarded & 4) != 0;
            awardedOverstressed = (awarded & 8) != 0;
        }
        super.read(tag, registries, clientPacket);
    }
    //#endregion
}