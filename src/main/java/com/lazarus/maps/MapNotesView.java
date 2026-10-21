package com.lazarus.maps;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;

/** Entry point for the client mixins: the annotations of a map item, drawn in the map's pixel space. */
public final class MapNotesView {
    private MapNotesView() { }

    public static void render(PoseStack pose, MultiBufferSource buffers, int light, ItemStack stack) {
        Integer mapId = MapItem.getMapId(stack);
        if (mapId != null) MarkRenderer.render(pose, buffers, light, ClientEvents.notes(mapId));
    }
}
