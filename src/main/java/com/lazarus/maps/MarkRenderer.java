package com.lazarus.maps;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Draws the annotations on a map rendered in the world (the map in hand), in the pose vanilla leaves after drawing
 * the map: one unit per map pixel, 128 x 128, closer pixels at more negative z. The map quad is at -0.01 and its
 * markers (player, frames) from -0.02 down by 0.001 each; the marks stay clear of both so they never z-fight, and
 * are opaque so nothing behind shows through.
 */
final class MarkRenderer {
    private static final RenderType FLAT = RenderType.text(new ResourceLocation(LazarusMaps.ID, "textures/misc/white.png"));
    private static final float MARK_Z = -0.05f, TEXT_Z = -0.06f, TEXT_SCALE = 0.5f;

    private MarkRenderer() { }

    static void render(PoseStack pose, MultiBufferSource buffers, int light, List<MapMark> marks) {
        if (marks.isEmpty()) return;
        // Two passes: non-fixed render types share one buffer, so asking for the font's buffer in the middle would
        // close the quads' buffer and the next quads would be lost (flicker). All quads first, then all text.
        VertexConsumer flat = buffers.getBuffer(FLAT);
        Matrix4f matrix = pose.last().pose();
        for (MapMark mark : marks) {
            int x = mark.points.get(0), y = mark.points.get(1);
            if (mark.type == MapMark.STROKE) {
                for (int i = 2; i < mark.points.size(); i += 2) {
                    int nx = mark.points.get(i), ny = mark.points.get(i + 1);
                    line(flat, matrix, x, y, nx, ny, mark.color, light);
                    x = nx;
                    y = ny;
                }
                square(flat, matrix, x, y, 1, mark.color, light);
            } else if (mark.type == MapMark.NOTE) {
                square(flat, matrix, x - 1, y - 1, 2, 0xFFF6E4AE, light);
            } else {
                square(flat, matrix, x - 2, y - 2, 5, mark.type == MapMark.DANGER ? 0xFFAA3432 : 0xFF317048, light);
            }
        }
        Font font = Minecraft.getInstance().font;
        for (MapMark mark : marks) {
            int x = mark.points.get(0), y = mark.points.get(1);
            if (mark.type == MapMark.NOTE) text(pose, buffers, font, mark.text, x + 2, y - 2, mark.color, light);
            else if (mark.type == MapMark.DANGER) text(pose, buffers, font, "!", x - 0.5f, y - 2, 0xFFFFFFFF, light);
            else if (mark.type == MapMark.SHELTER) text(pose, buffers, font, "H", x - 1f, y - 2, 0xFFFFFFFF, light);
        }
    }

    /** One-pixel squares along the segment (Bresenham), like the editor's pen. */
    private static void line(VertexConsumer out, Matrix4f m, int x0, int y0, int x1, int y1, int color, int light) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        while (true) {
            square(out, m, x0, y0, 1, color, light);
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice > -dy) { error -= dy; x0 += sx; }
            if (twice < dx) { error += dx; y0 += sy; }
        }
    }

    /** Same corner order as the vanilla map quad, so it faces the player like the map does. */
    private static void square(VertexConsumer out, Matrix4f m, float x, float y, float size, int color, int light) {
        int a = color >>> 24, r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        out.vertex(m, x, y + size, MARK_Z).color(r, g, b, a).uv(0, 1).uv2(light).endVertex();
        out.vertex(m, x + size, y + size, MARK_Z).color(r, g, b, a).uv(1, 1).uv2(light).endVertex();
        out.vertex(m, x + size, y, MARK_Z).color(r, g, b, a).uv(1, 0).uv2(light).endVertex();
        out.vertex(m, x, y, MARK_Z).color(r, g, b, a).uv(0, 0).uv2(light).endVertex();
    }

    private static void text(PoseStack pose, MultiBufferSource buffers, Font font, String text, float x, float y,
                             int color, int light) {
        pose.pushPose();
        pose.translate(x, y, TEXT_Z);
        pose.scale(TEXT_SCALE, TEXT_SCALE, 1);
        font.drawInBatch(text, 0, 0, color, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
