package com.lazarus.maps;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import java.util.ArrayList;
import java.util.List;

public final class MapEditorScreen extends Screen {
    /** Tools 0-3 create the MapMark type of the same number. */
    private static final int MOVE = 4, PAINT = 5, ERASE = 6;
    private static final String[] TOOLS = {"pen", "note", "danger", "shelter", "move", "paint", "erase"};
    /** Writing colors: ink, red, blue, green, yellow, orange, purple, white. */
    static final int[] COLORS = {0xFF26221F, 0xFFAA3432, 0xFF31578B, 0xFF3E7B3A, 0xFFE0B83A, 0xFFD9772B,
            0xFF7A3E8F, 0xFFF2EEE4};
    private static final int SWATCH = 14, PICK_RADIUS = 6;

    private final ItemStack mapStack;
    private final int mapId;
    private List<MapMark> marks = List.of();
    private MapMark pendingStroke;
    /** Mark being dragged by the move tool: a moved copy drawn in place of the original until the server answers. */
    private MapMark moving;
    private int moveFromX, moveFromY;
    private EditBox noteBox;
    private Button saveButton;
    private final List<Button> toolButtons = new ArrayList<>();
    private int tool = MapMark.STROKE;
    private int colorIndex;
    private int mapX, mapY, mapPixels, paletteX, paletteY;
    private double scale;
    private int pendingX, pendingY;

    public MapEditorScreen(ItemStack mapStack, int mapId) {
        super(Component.translatable("screen.lazarusmaps.map"));
        this.mapStack = mapStack;
        this.mapId = mapId;
        this.marks = ClientEvents.notes(mapId); // refreshed by the snapshot the screen requests
    }

    public int mapId() { return mapId; }
    public void receive(CompoundTag data) {
        marks = MapMark.fromTag(data);
        moving = null;
    }

    @Override
    protected void init() {
        mapPixels = Math.max(128, Math.min(width - 32, height - 90));
        scale = mapPixels / 128.0;
        mapX = (width - mapPixels) / 2;
        mapY = 25;
        int toolbarY = Math.min(height - 45, mapY + mapPixels + 6);
        int left = (width - TOOLS.length * 48) / 2;
        toolButtons.clear();
        for (int i = 0; i < TOOLS.length; i++) {
            final int choice = i;
            toolButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.lazarusmaps." + TOOLS[i]),
                    button -> tool = choice).bounds(left + i * 48, toolbarY, 46, 20).build()));
        }
        paletteX = (width - COLORS.length * (SWATCH + 3)) / 2;
        paletteY = toolbarY + 23;
        noteBox = new EditBox(font, (width - 220) / 2, toolbarY + 22, 162, 18, Component.literal("Note"));
        noteBox.setMaxLength(48);
        noteBox.setVisible(false);
        addRenderableWidget(noteBox);
        saveButton = addRenderableWidget(Button.builder(Component.translatable("screen.lazarusmaps.save"),
                button -> saveNote()).bounds((width + 110) / 2, toolbarY + 22, 54, 18).build());
        saveButton.visible = false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xE2E0D2);
        graphics.fill(mapX - 3, mapY - 3, mapX + mapPixels + 3, mapY + mapPixels + 3, 0xFF28251F);
        graphics.fill(mapX, mapY, mapX + mapPixels, mapY + mapPixels, 0xFFDDD3AE);
        MapItemSavedData map = minecraft == null || minecraft.level == null
                ? null : MapItem.getSavedData(mapStack, minecraft.level);
        if (map != null) {
            // The vanilla map quad sits at z -0.01, behind the parchment fill at z 0: lift it in front. Drawn as a
            // held map (not framed), so the player marker shows.
            graphics.pose().pushPose();
            graphics.pose().translate(mapX, mapY, 1);
            graphics.pose().scale((float) scale, (float) scale, 1);
            minecraft.gameRenderer.getMapRenderer().render(graphics.pose(), graphics.bufferSource(), mapId,
                    map, false, 0xF000F0);
            graphics.flush();
            graphics.pose().popPose();
        }
        // Marks in front of the lifted map and its markers.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 2);
        graphics.enableScissor(mapX, mapY, mapX + mapPixels, mapY + mapPixels);
        for (MapMark mark : marks) drawMark(graphics, moving != null && moving.id == mark.id ? moving : mark);
        if (pendingStroke != null) drawMark(graphics, pendingStroke);
        graphics.disableScissor();
        graphics.pose().popPose();
        super.render(graphics, mouseX, mouseY, partialTick);
        Button active = toolButtons.get(tool);
        graphics.renderOutline(active.getX() - 1, active.getY() - 1, active.getWidth() + 2, active.getHeight() + 2, 0xFFE8C66A);
        if (!noteBox.isVisible()) {
            for (int i = 0; i < COLORS.length; i++) {
                int x = paletteX + i * (SWATCH + 3);
                graphics.fill(x, paletteY, x + SWATCH, paletteY + SWATCH, COLORS[i]);
                graphics.renderOutline(x - 1, paletteY - 1, SWATCH + 2, SWATCH + 2, i == colorIndex ? 0xFFE8C66A : 0xFF4A463E);
            }
        }
        graphics.drawCenteredString(font, Component.translatable("screen.lazarusmaps.tip"),
                width / 2, height - 12, 0xA9B0A5);
    }

    private void drawMark(GuiGraphics graphics, MapMark mark) {
        if (mark.points.size() < 2) return;
        int x = screenX(mark.points.get(0)), y = screenY(mark.points.get(1));
        if (mark.type == MapMark.STROKE) {
            for (int i = 2; i < mark.points.size(); i += 2) {
                int nx = screenX(mark.points.get(i)), ny = screenY(mark.points.get(i + 1));
                line(graphics, x, y, nx, ny, mark.color);
                x = nx; y = ny;
            }
            graphics.fill(x, y, x + 2, y + 2, mark.color);
        } else if (mark.type == MapMark.NOTE) {
            graphics.fill(x - 2, y - 2, x + 3, y + 3, 0xFFF6E4AE);
            graphics.drawString(font, mark.text, x + 5, y - 4, mark.color, true);
        } else {
            graphics.fill(x - 5, y - 5, x + 6, y + 6, mark.type == MapMark.DANGER ? 0xFFAA3432 : 0xFF317048);
            graphics.drawCenteredString(font, mark.type == MapMark.DANGER ? "!" : "H", x, y - 4, 0xFFFFFFFF);
        }
    }

    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int twice = 2 * error;
            if (twice > -dy) { error -= dy; x0 += sx; }
            if (twice < dx) { error += dx; y0 += sy; }
        }
    }

    private int screenX(int x) { return mapX + (int) (x * scale); }
    private int screenY(int y) { return mapY + (int) (y * scale); }
    private boolean onMap(double x, double y) {
        return x >= mapX && y >= mapY && x < mapX + mapPixels && y < mapY + mapPixels;
    }
    private int localX(double x) { return Math.max(0, Math.min(127, (int) ((x - mapX) / scale))); }
    private int localY(double y) { return Math.max(0, Math.min(127, (int) ((y - mapY) / scale))); }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        moving = null; // a move the server refused (edit too fast) must not stay drawn forever
        if (button == 0 && !noteBox.isVisible() && y >= paletteY && y < paletteY + SWATCH && x >= paletteX) {
            int i = (int) ((x - paletteX) / (SWATCH + 3));
            if (i < COLORS.length && x < paletteX + i * (SWATCH + 3) + SWATCH) { colorIndex = i; return true; }
        }
        if (!onMap(x, y) || noteBox.isVisible()) return super.mouseClicked(x, y, button);
        int px = localX(x), py = localY(y);
        if (button == 1 || tool == ERASE) {
            MapMark target = pick(px, py);
            if (target != null) LazarusMaps.CHANNEL.sendToServer(new NotesNetwork.Edit(mapId, target.id, null));
            return true;
        }
        if (button != 0) return super.mouseClicked(x, y, button);
        if (tool == MOVE) {
            MapMark target = pick(px, py);
            if (target != null) {
                moving = MapMark.load(target.save());
                moveFromX = px;
                moveFromY = py;
            }
        } else if (tool == PAINT) {
            MapMark target = pick(px, py);
            // Symbols keep their meaning colors (red danger, green shelter); writing takes the chosen color.
            if (target != null && (target.type == MapMark.STROKE || target.type == MapMark.NOTE)
                    && target.color != COLORS[colorIndex]) {
                MapMark painted = MapMark.load(target.save());
                painted.color = COLORS[colorIndex];
                replace(painted);
            }
        } else if (tool == MapMark.NOTE) {
            pendingX = px; pendingY = py;
            noteBox.setValue("");
            noteBox.setVisible(true);
            saveButton.visible = true;
            setFocused(noteBox);
            noteBox.setFocused(true);
        } else if (tool == MapMark.STROKE) {
            pendingStroke = createMark(tool, px, py);
        } else {
            send(createMark(tool, px, py));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (moving != null && button == 0) {
            MapMark original = find(moving.id);
            if (original != null) translateFrom(original, localX(x) - moveFromX, localY(y) - moveFromY);
            return true;
        }
        if (pendingStroke != null && button == 0 && onMap(x, y)
                && pendingStroke.points.size() < 512) {
            int px = localX(x), py = localY(y);
            int end = pendingStroke.points.size();
            if (pendingStroke.points.get(end - 2) != px || pendingStroke.points.get(end - 1) != py) {
                pendingStroke.points.add(px);
                pendingStroke.points.add(py);
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (moving != null && button == 0) {
            MapMark original = find(moving.id);
            if (original != null && !original.points.equals(moving.points)) replace(moving);
            else moving = null;
            return true;
        }
        if (pendingStroke != null && button == 0) {
            send(pendingStroke);
            pendingStroke = null;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    /** Sets the moved copy to the original shifted by (dx, dy), clamped so every point stays on the map. */
    private void translateFrom(MapMark original, int dx, int dy) {
        int minX = 127, minY = 127, maxX = 0, maxY = 0;
        for (int i = 0; i < original.points.size(); i += 2) {
            minX = Math.min(minX, original.points.get(i)); maxX = Math.max(maxX, original.points.get(i));
            minY = Math.min(minY, original.points.get(i + 1)); maxY = Math.max(maxY, original.points.get(i + 1));
        }
        dx = Math.max(-minX, Math.min(127 - maxX, dx));
        dy = Math.max(-minY, Math.min(127 - maxY, dy));
        moving.points.clear();
        for (int i = 0; i < original.points.size(); i += 2) {
            moving.points.add(original.points.get(i) + dx);
            moving.points.add(original.points.get(i + 1) + dy);
        }
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (noteBox.isVisible() && key == 257) { saveNote(); return true; }
        if (noteBox.isVisible() && key == 256) {
            noteBox.setVisible(false); saveButton.visible = false; return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private MapMark createMark(int type, int x, int y) {
        MapMark mark = new MapMark();
        mark.type = type;
        mark.color = COLORS[colorIndex];
        mark.points.add(x);
        mark.points.add(y);
        return mark;
    }

    private void saveNote() {
        String text = noteBox.getValue().trim();
        if (!text.isEmpty()) {
            MapMark mark = createMark(MapMark.NOTE, pendingX, pendingY);
            mark.text = text;
            send(mark);
        }
        noteBox.setVisible(false);
        saveButton.visible = false;
        setFocused(null);
    }

    private MapMark find(int id) {
        for (MapMark mark : marks) if (mark.id == id) return mark;
        return null;
    }

    /** The mark under the cursor: a point within a few map pixels, or the text of a note. */
    private MapMark pick(int px, int py) {
        MapMark nearest = null;
        double best = PICK_RADIUS * PICK_RADIUS;
        for (MapMark mark : marks) {
            if (mark.type == MapMark.NOTE) {
                int x = mark.points.get(0), y = mark.points.get(1);
                double right = x + (5 + font.width(mark.text)) / scale, half = 5 / scale;
                if (px >= x - 1 && px <= right && py >= y - half && py <= y + half) return mark;
            }
            for (int i = 0; i < mark.points.size(); i += 2) {
                int dx = mark.points.get(i) - px, dy = mark.points.get(i + 1) - py;
                double distance = dx * dx + dy * dy;
                if (distance < best) { best = distance; nearest = mark; }
            }
        }
        return nearest;
    }

    private void send(MapMark mark) {
        LazarusMaps.CHANNEL.sendToServer(new NotesNetwork.Edit(mapId, 0, mark.save()));
    }

    /** Replaces an existing mark (same id) with an edited copy: moved or recolored. */
    private void replace(MapMark mark) {
        LazarusMaps.CHANNEL.sendToServer(new NotesNetwork.Edit(mapId, mark.id, mark.save()));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
