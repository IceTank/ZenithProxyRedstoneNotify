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
import kotlin.Pair;
import org.example.RedstoneLampNotifier;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundBlockUpdatePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSectionBlocksUpdatePacket;
import org.jspecify.annotations.Nullable;

import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.DISCORD;
import static com.zenith.util.ComponentSerializer.minimessage;
import static org.example.RedstoneLampNotifier.LOG;
import static org.example.RedstoneLampNotifier.PLUGIN_CONFIG;

/*
 * @author IceTank
 * @since 01.11.2025
 */
public class RedstoneNotifierModule extends Module {
    private static CopyOnWriteArrayList<BlockPos> blockUpdates = new CopyOnWriteArrayList<>();
    private static final Set<BlockPos> activeRedstoneLamps = new HashSet<>();
    private List<Pair<Integer, BlockPos>> pendingActiveLamps = new CopyOnWriteArrayList<>();
    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
                EventConsumer.of(ClientTickEvent.class, this::handleClientTick)
        );
    }

    @Override
    public boolean enabledSetting() {
        return PLUGIN_CONFIG.enabled;
    }

    private void handleClientTick(ClientTickEvent event) {
        try {
            pendingActiveLamps.replaceAll(e -> new Pair<>(e.getFirst() - 1, e.getSecond()));
            ArrayList<Pair<Integer, BlockPos>> toRemove = new ArrayList<>(); // CopyOnWriteArrayList does not support remove() on iteration
            for (Pair<Integer, BlockPos> entry : pendingActiveLamps) {
                if (!isActiveRedstoneLamp(entry.getSecond())) {
                    toRemove.add(entry);
                    continue;
                }
                if (entry.getFirst() <= 0) {
                    BlockPos pos = entry.getSecond();
                    List<String> lines = getSignTextOnBlock(pos);
                    notify(lines);
                    toRemove.add(entry);
                }
            }
            pendingActiveLamps.removeAll(toRemove);

            while (!blockUpdates.isEmpty()) {
                BlockPos pos = blockUpdates.removeFirst();
                if (isRedstoneLamp(pos)) {
                    if (isActiveRedstoneLamp(pos)) {
                        if (activeRedstoneLamps.contains(pos)) {
                            continue;
                        }
                        activeRedstoneLamps.add(pos);
                        if (PLUGIN_CONFIG.triggerDelay > 0) {
                            pendingActiveLamps.add(new Pair<>(PLUGIN_CONFIG.triggerDelay, pos));
                            continue;
                        }
                        List<String> lines = getSignTextOnBlock(pos);
                        notify(lines);
                    } else {
                        activeRedstoneLamps.remove(pos);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error processing redstone lamp updates", e);
        }
    }

    private void notify(List<String> lines) {
        if (lines != null && !lines.isEmpty()) {
            discordNotify(lines);
            chatNotify(lines);
        }
    }

    private void chatNotify(List<String> lines) {
        Proxy.getInstance().getActiveConnections().forEach(c -> {
            c.sendAsyncMessage(minimessage("<blue> Lamp Active with lines: " + String.join(" ", lines.stream()
                    .map(s -> s.replaceAll("\"", "")).filter(s -> !s.isEmpty()).toList()
            )));
        });
    }

    private void discordNotify(List<String> lines) {
        if (!DISCORD.isRunning()) return;
        if (lines.isEmpty()) return;

        Embed embed = Embed.builder()
                .title("Redstone Lamp Activated")
                .description("A redstone lamp has been activated with the following sign text:")
                .addField("Sign Text", String.join("\n", lines.stream()
                        .map(s -> s.replaceAll("\"", "")).filter(s -> !s.isEmpty()).toList()
                ));

        if (!PLUGIN_CONFIG.rolesToPing.isEmpty()) {
            StringBuilder pingBuilder = new StringBuilder();
            for (Long roleId : PLUGIN_CONFIG.rolesToPing) {
                pingBuilder.append("<@&").append(roleId).append("> ");
            }
            embed.addField("Ping:", pingBuilder.toString().trim());
        }

        DISCORD.sendEmbedMessage(embed);
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
                if (info == null) {
                    continue;
                }
                try {
                    assert info.getNbt() != null;
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
                        }
                    }
                } catch (UncheckedIOException e) {
                    LOG.error("Failed to read NBT for sign at {}", neighborPos, e);
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
