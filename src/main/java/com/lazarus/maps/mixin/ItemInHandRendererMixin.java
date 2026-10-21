package com.lazarus.maps.mixin;

import com.lazarus.maps.MapNotesView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the annotations on top of the first-person map; vanilla leaves the pose in map pixels at the end. */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @Inject(method = "renderMap", at = @At("TAIL"))
    private void lazarusmaps$notes(PoseStack pose, MultiBufferSource buffers, int light, ItemStack stack, CallbackInfo ci) {
        MapNotesView.render(pose, buffers, light, stack);
    }
}
