package org.example.module;


import com.github.rfresh2.EventConsumer;
import com.viaversion.nbt.io.MNBTIO;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.nbt.tag.Tag;
import com.zenith.Proxy;
import com.zenith.cache.data.chunk.Chunk;
import com.zenith.cache.data.chunk.ChunkCache;
import com.zenith.discord.Embed;
import com.zenith.event.client.ClientTickEvent;
import com.zenith.feature.player.World;
import com.zenith.mc.block.*;
import com.zenith.mc.block.properties.api.BlockStateProperties;
import com.zenith.module.api.Module;
import com.zenith.network.client.ClientSession;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.codec.PacketHandlerCodec;
import com.zenith.network.codec.PacketHandlerStateCodec;
import org.example.RedstoneLampNotifier;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.jspecify.annotations.Nullable;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.DISCORD;
import static com.zenith.util.ComponentSerializer.minimessage;

/*
 * @author IceTank
 * @since 01.11.2025
 */
public class RedstoneNotifierModule extends Module {
    private static CopyOnWriteArrayList<BlockPos> blockUpdates = new CopyOnWriteArrayList<>();
    private static final Set<BlockPos> activeRedstoneLamps = new HashSet<>();
    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                EventConsumer.of(ClientTickEvent.class, this::handleClientTick)
        );
    }

    @Override
    public boolean enabledSetting() {
        return RedstoneLampNotifier.PLUGIN_CONFIG.enabled;
    }

    private void handleClientTick(ClientTickEvent event) {
        try {
            while (!blockUpdates.isEmpty()) {
                BlockPos pos = blockUpdates.removeFirst();
                if (isRedstoneLamp(pos)) {
                    if (isActiveRedstoneLamp(pos)) {
                        if (activeRedstoneLamps.contains(pos)) {
                            continue;
                        }
                        activeRedstoneLamps.add(pos);
                        List<String> lines = getSignTextOnBlock(pos);
                        if (lines == null || lines.isEmpty()) return;
                        notify(lines);
                        Proxy.getInstance().getActiveConnections().forEach(c -> {
                            c.sendAsyncMessage(minimessage("<blue> Lamp Active with lines: " + lines));
                        });
                    } else {
                        activeRedstoneLamps.remove(pos);
                    }
                }
            }
        } catch (Exception e) {
            RedstoneLampNotifier.LOG.error("Error processing redstone lamp updates", e);
        }
    }

    private void notify(List<String> lines) {
        if (!DISCORD.isRunning()) return;
        if (lines.isEmpty()) return;

        DISCORD.sendEmbedMessage(Embed.builder()
                .title("Redstone Lamp Activated")
                .description("A redstone lamp has been activated with the following sign text:")
                .addField("Sign Text", String.join("\n", lines))
        );
    }

    private boolean isRedstoneLamp(BlockPos pos) {
        return World.getBlock(pos) == BlockRegistry.REDSTONE_LAMP;
    }

    private boolean isActiveRedstoneLamp(BlockPos pos) {
        if (World.getBlock(pos) == BlockRegistry.REDSTONE_LAMP) {
            BlockState state = World.getBlockState(pos);
            var active = state.getProperty(BlockStateProperties.LIT);
            return active != null && active;
        }
        return false;
    }

    private List<String> getSignTextOnBlock(BlockPos pos) {
        List<String> lines = new ArrayList<>();
        for (Direction direction : Direction.HORIZONTALS) {
            BlockPos neighborPos = pos.offset(direction.x(), 0, direction.z());
            Block neighborBlock = World.getBlock(neighborPos);
            if (neighborBlock.blockEntityType() == BlockEntityType.SIGN) {
                BlockEntityInfo info = getBlockEntityInfoAt(neighborPos);
                if (info != null) {
                    try {
                        Tag tag = MNBTIO.read(info.getNbt());
                        if (tag instanceof CompoundTag compound) {
                            Tag front = compound.get("front_text");
                            if (front instanceof CompoundTag frontCompound) {
                                Tag messagesTag = frontCompound.get("messages");
                                if (messagesTag instanceof ListTag<?> list) {
                                    for (Tag lineTag : list.getValue()) {
                                        if (lineTag instanceof StringTag stringTag) {
                                            lines.add(stringTag.getValue());
                                        }
                                    }
                                }
                                System.out.println(messagesTag);
                            }
                        }
                    } catch (UncheckedIOException e) {
                        return null;
                    }
                }
            }
        }
        return lines;
    }

    @Nullable
    private BlockEntityInfo getBlockEntityInfoAt(BlockPos pos) {
        ChunkCache chunkCache = CACHE.getChunkCache();
        Chunk chunk = chunkCache.get(pos.x() >> 4, pos.z() >> 4);
        int x = pos.x() & 0xF;
        int z = pos.z() & 0xF;
        return chunk.getBlockEntities().stream()
                .filter(be -> be.getX() == x && be.getY() == pos.y() && be.getZ() == z)
                .findFirst()
                .orElse(null);
    }

    @Override
    public @Nullable PacketHandlerCodec registerClientPacketHandlerCodec() {
        return PacketHandlerCodec.clientBuilder()
                .setId("RedstoneNotifierCodec")
                .setPriority(0)
                .state(ProtocolState.GAME, PacketHandlerStateCodec.clientBuilder()
                        .inbound(ClientboundBlockUpdatePacket.class, new RedstoneUpdatePacketHandler())
                        .inbound(ClientboundSectionBlocksUpdatePacket.class, new MultiRedstoneUpdatePacketHandler())
                        .build()
                )
                .build();
    }

    static class RedstoneUpdatePacketHandler implements PacketHandler<ClientboundBlockUpdatePacket, ClientSession> {
        @Override
        public ClientboundBlockUpdatePacket apply(final ClientboundBlockUpdatePacket packet, final ClientSession session) {
            var pos = new BlockPos(packet.getEntry().getX(), packet.getEntry().getY(), packet.getEntry().getZ());
            blockUpdates.add(pos);
            return packet;
        }
    }

    static class MultiRedstoneUpdatePacketHandler implements PacketHandler<ClientboundSectionBlocksUpdatePacket, ClientSession> {
        @Override
        public ClientboundSectionBlocksUpdatePacket apply(ClientboundSectionBlocksUpdatePacket packet, ClientSession session) {
            for (var entry : packet.getEntries()) {
                var pos = new BlockPos(entry.getX(), entry.getY(), entry.getZ());
                blockUpdates.add(pos);
            }
            return packet;
        }
    }
}
