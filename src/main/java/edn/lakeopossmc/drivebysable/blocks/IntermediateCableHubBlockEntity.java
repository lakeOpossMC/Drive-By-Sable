package edn.lakeopossmc.drivebysable.blocks;

import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.compat.GetCreativeCableServerHandler;
import edn.lakeopossmc.drivebysable.compat.keytranslator.ControllerChannelTranslator.Vocabulary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// --- SPECIFIC FOR INTERMEDIATE CABLE HUB --- //
// * Held button state lives in GetCreativeCableServerHandler
// * Remembers which device types have been bound, only those channel groups are open
public class IntermediateCableHubBlockEntity extends CableHubBlockEntity {
    private static final String OPEN_GROUPS_KEY = "OpenChannelGroups";

    private final Set<String> openGroups = new HashSet<>();

    public IntermediateCableHubBlockEntity(final BlockPos pos, final BlockState state) {
        this(CableBlockEntities.INTERMEDIATE_CABLE_HUB.get(), pos, state);
    }

    public IntermediateCableHubBlockEntity(
            final BlockEntityType<?> type,
            final BlockPos pos,
            final BlockState state
    ) {
        super(type, pos, state);
    }

    @Override
    protected Vocabulary getVocabulary() {
        return Vocabulary.GET_CREATIVE;
    }

    @Override
    protected boolean hasBulb() {
        return true;
    }

    //#region // --- OPEN CHANNEL GROUPS --- //
    // * True if this bind opened a group that was closed
    public boolean openGroup(final String group) {
        if (group == null || !GetCreativeCableServerHandler.GROUP_ORDER.contains(group) || !this.openGroups.add(group)) {
            return false;
        }

        this.setChanged();
        this.sendData();
        return true;
    }

    public boolean isGroupOpen(final String group) {
        return this.openGroups.contains(group);
    }

    // * Always in the fixed cycle order, whatever order the devices were bound in
    public List<String> getOpenGroups() {
        final List<String> groups = new ArrayList<>(this.openGroups.size());
        for (final String group : GetCreativeCableServerHandler.GROUP_ORDER) {
            if (this.openGroups.contains(group)) {
                groups.add(group);
            }
        }
        return List.copyOf(groups);
    }

    public List<String> getOpenChannels() {
        final List<String> channels = new ArrayList<>();
        for (final String group : this.getOpenGroups()) {
            channels.addAll(GetCreativeCableServerHandler.channelsInGroup(group));
        }
        return List.copyOf(channels);
    }
    //#endregion

    //#region // --- SAVE AND SYNC --- //
    // * Written for both saves and client packets, the client needs it for channel lists and hover tips
    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        final ListTag groups = new ListTag();
        for (final String group : this.getOpenGroups()) {
            groups.add(StringTag.valueOf(group));
        }
        tag.put(OPEN_GROUPS_KEY, groups);
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries, final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.openGroups.clear();
        for (final Tag entry : tag.getList(OPEN_GROUPS_KEY, Tag.TAG_STRING)) {
            this.openGroups.add(entry.getAsString());
        }
    }
    //#endregion
}