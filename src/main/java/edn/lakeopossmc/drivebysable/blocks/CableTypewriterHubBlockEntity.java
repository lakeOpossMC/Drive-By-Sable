package edn.lakeopossmc.drivebysable.blocks;

import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlockEntity;
import edn.lakeopossmc.drivebysable.CableBlockEntities;
import edn.lakeopossmc.drivebysable.DriveBySableMod;
import edn.lakeopossmc.drivebysable.cable.CableNetworkManager;
import edn.lakeopossmc.drivebysable.compat.CableTypewriterHubServerHandler;
import net.createmod.catnip.lang.Lang;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CableTypewriterHubBlockEntity extends LinkedTypewriterBlockEntity {

    private Set<String> connectedChannels = Collections.emptySet();

    private static CableTypewriterHubBlockEntity clientInstance;

    public CableTypewriterHubBlockEntity(final BlockPos pos, final BlockState state) {
        super(CableBlockEntities.CABLE_TYPEWRITER_HUB.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level instanceof final ServerLevel level) {
            updateConnectedChannels(level);
        }
    }

    private void updateConnectedChannels(final ServerLevel level) {
        final CableNetworkManager manager = CableNetworkManager.get(level);
        final Set<String> updated = new HashSet<>();
        for (final String channel : CableTypewriterHubServerHandler.CHANNELS) {
            if (manager.hasSinks(this.getBlockPos(), channel)) {
                updated.add(channel);
            }
        }
        if (!updated.equals(this.connectedChannels)) {
            this.connectedChannels = updated;
            this.sendData();
        }
    }

    public boolean hasConnectionForChannel(final String channel) {
        return this.connectedChannels.contains(channel);
    }

    @Override
    public boolean checkAndStartUsing(final UUID userID) {
        final boolean result = super.checkAndStartUsing(userID);
        if (result && this.level != null && this.level.isClientSide) {
            clientInstance = this;
        }
        return result;
    }

    @Override
    public void pressKey(final int key) {
        super.pressKey(key);
        if (this.level instanceof final ServerLevel level) {
            CableTypewriterHubServerHandler.receiveKey(level, this.getBlockPos(), key, true);
        }
    }

    @Override
    public void releaseKey(final int key) {
        super.releaseKey(key);
        if (this.level instanceof final ServerLevel level) {
            CableTypewriterHubServerHandler.receiveKey(level, this.getBlockPos(), key, false);
        }
    }

    @Override
    public void disconnectUser() {
        if (this.level != null && this.level.isClientSide) {
            clientInstance = null;
        }
        if (this.level instanceof final ServerLevel level) {
            CableTypewriterHubServerHandler.KEY_TO_CHANNEL.values().forEach(channel ->
                    CableNetworkManager.trySetSignalAt(level, this.getBlockPos(), channel, 0));
        }
        super.disconnectUser();
    }

    @Override
    protected void write(final CompoundTag tag, final HolderLookup.Provider registries,
                         final boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            final ListTag list = new ListTag();
            this.connectedChannels.forEach(ch -> list.add(StringTag.valueOf(ch)));
            tag.put("ConnectedChannels", list);
        }
    }

    @Override
    protected void read(final CompoundTag tag, final HolderLookup.Provider registries,
                        final boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket && tag.contains("ConnectedChannels")) {
            final Set<String> channels = new HashSet<>();
            final ListTag list = tag.getList("ConnectedChannels", 8);
            for (int i = 0; i < list.size(); i++) {
                channels.add(list.getString(i));
            }
            this.connectedChannels = channels;
        }
    }

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public void sendConnectMessage(final Player player) {
        player.displayClientMessage(
                Lang.builder(DriveBySableMod.MOD_ID).translate("typewriter_hub.start_controlling").component(),
                true
        );
    }

    @Override
    public void sendDisconnectMessage(final Player player) {
        player.displayClientMessage(
                Lang.builder(DriveBySableMod.MOD_ID).translate("typewriter_hub.stop_controlling").component(),
                true
        );
    }

    public static CableTypewriterHubBlockEntity getClientInstance() {
        return clientInstance;
    }
}