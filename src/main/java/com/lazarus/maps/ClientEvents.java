package com.lazarus.maps;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = LazarusMaps.ID, value = Dist.CLIENT)
public final class ClientEvents {
    /** Marks per map ID, as last sent by the server; drawn on the map in hand and seeded into the editor. */
    private static final Map<Integer, List<MapMark>> NOTES = new HashMap<>();
    /** Game time of the last request per map ID, so holding a map asks the server once, not every tick. */
    private static final Map<Integer, Long> REQUESTED = new HashMap<>();
    private static final long REQUEST_RETRY = 200;

    private ClientEvents() { }

    static List<MapMark> notes(int mapId) { return NOTES.getOrDefault(mapId, List.of()); }

    /** Asks for the notes of a held map the client has not seen yet (the server only answers holders). */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || mc.level == null) return;
        for (ItemStack stack : new ItemStack[]{mc.player.getMainHandItem(), mc.player.getOffhandItem()}) {
            Integer mapId = stack.getItem() instanceof MapItem ? MapItem.getMapId(stack) : null;
            if (mapId == null || NOTES.containsKey(mapId)) continue;
            long now = mc.level.getGameTime();
            Long last = REQUESTED.get(mapId);
            if (last != null && now >= last && now - last < REQUEST_RETRY) continue;
            REQUESTED.put(mapId, now);
            LazarusMaps.CHANNEL.sendToServer(new NotesNetwork.Request(mapId));
        }
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NOTES.clear();
        REQUESTED.clear();
    }

    @SubscribeEvent
    public static void useMap(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()) return;
        ItemStack stack = event.getItemStack();
        Integer mapId = stack.getItem() instanceof MapItem ? MapItem.getMapId(stack) : null;
        if (mapId == null) return;
        Minecraft.getInstance().setScreen(new MapEditorScreen(stack.copy(), mapId));
        LazarusMaps.CHANNEL.sendToServer(new NotesNetwork.Request(mapId));
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    static void receive(int mapId, CompoundTag data) {
        NOTES.put(mapId, MapMark.fromTag(data));
        if (Minecraft.getInstance().screen instanceof MapEditorScreen screen && screen.mapId() == mapId)
            screen.receive(data);
    }

    @Mod.EventBusSubscriber(modid = LazarusMaps.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Overlay {
        private Overlay() { }

        @SubscribeEvent
        public static void register(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("gps", (gui, graphics, partialTick, width, height) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
                if (!mc.player.getMainHandItem().is(LazarusMaps.GPS.get())
                        && !mc.player.getOffhandItem().is(LazarusMaps.GPS.get())) return;
                String coords = String.format("GPS  X:%d  Y:%d  Z:%d",
                        mc.player.blockPosition().getX(), mc.player.blockPosition().getY(),
                        mc.player.blockPosition().getZ());
                String dimension = mc.player.level().dimension().location().getPath();
                int boxWidth = Math.max(mc.font.width(coords), mc.font.width(dimension)) + 12;
                graphics.fill(5, 5, 5 + boxWidth, 32, 0xB0131C18);
                graphics.drawString(mc.font, coords, 11, 9, 0xB7E4BA, false);
                graphics.drawString(mc.font, dimension, 11, 20, 0xA2AAA0, false);
            });
        }
    }
}
