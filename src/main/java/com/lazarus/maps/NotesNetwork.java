package com.lazarus.maps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

public final class NotesNetwork {
    private NotesNetwork() { }

    private static boolean hasMap(ServerPlayer player, int mapId) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            Integer heldId = stack.getItem() instanceof MapItem ? MapItem.getMapId(stack) : null;
            if (heldId != null && heldId == mapId && MapItem.getSavedData(stack, player.level()) != null) return true;
        }
        return false;
    }

    private static void sendSnapshot(ServerPlayer player, int mapId) {
        if (hasMap(player, mapId)) LazarusMaps.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new Snapshot(mapId, MapNotesData.get(player.server).snapshot(mapId)));
    }

    private static void broadcast(ServerPlayer editor, int mapId) {
        for (ServerPlayer player : editor.server.getPlayerList().getPlayers()) sendSnapshot(player, mapId);
    }

    public record Request(int mapId) {
        static void encode(Request packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.mapId); }
        static Request decode(FriendlyByteBuf buf) { return new Request(buf.readVarInt()); }
        static void handle(Request packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player != null && packet.mapId >= 0) sendSnapshot(player, packet.mapId);
            });
            ctx.setPacketHandled(true);
        }
    }

    public record Edit(int mapId, int removeId, CompoundTag mark) {
        static void encode(Edit packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.mapId);
            buf.writeVarInt(packet.removeId);
            buf.writeNbt(packet.mark);
        }
        static Edit decode(FriendlyByteBuf buf) {
            return new Edit(buf.readVarInt(), buf.readVarInt(), buf.readNbt());
        }
        static void handle(Edit packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || packet.mapId < 0 || !hasMap(player, packet.mapId)) return;
                long now = player.server.overworld().getGameTime();
                long last = player.getPersistentData().getLong("LazarusMapsLastEdit");
                if (now > last && now - last < 2) return;
                player.getPersistentData().putLong("LazarusMapsLastEdit", now);
                MapNotesData data = MapNotesData.get(player.server);
                // removeId alone erases; removeId with a mark replaces it (move, recolor); a mark alone is new
                boolean changed = packet.removeId > 0
                        ? packet.mark == null ? data.remove(packet.mapId, packet.removeId)
                                : data.replace(packet.mapId, packet.removeId, MapMark.load(packet.mark))
                        : packet.mark != null && data.add(packet.mapId, MapMark.load(packet.mark));
                if (changed) broadcast(player, packet.mapId);
            });
            ctx.setPacketHandled(true);
        }
    }

    public record Snapshot(int mapId, CompoundTag data) {
        static void encode(Snapshot packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.mapId);
            buf.writeNbt(packet.data);
        }
        static Snapshot decode(FriendlyByteBuf buf) {
            return new Snapshot(buf.readVarInt(), buf.readNbt());
        }
        static void handle(Snapshot packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context ctx = supplier.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ClientEvents.receive(packet.mapId, packet.data)));
            ctx.setPacketHandled(true);
        }
    }
}
