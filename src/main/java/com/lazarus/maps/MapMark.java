package com.lazarus.maps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.List;

/** Coordinates are pixels of a vanilla 128x128 map, independent of GUI scale. */
public final class MapMark {
    public static final int STROKE = 0, NOTE = 1, DANGER = 2, SHELTER = 3;
    public int id, type, color;
    public String text = "";
    public final List<Integer> points = new ArrayList<>(); // x,y pairs

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putInt("Type", type);
        tag.putInt("Color", color);
        tag.putString("Text", text);
        tag.putIntArray("Points", points);
        return tag;
    }

    public static MapMark load(CompoundTag tag) {
        MapMark mark = new MapMark();
        mark.id = tag.getInt("Id");
        mark.type = tag.getInt("Type");
        mark.color = tag.getInt("Color");
        mark.text = tag.getString("Text");
        for (int value : tag.getIntArray("Points")) mark.points.add(value);
        return mark;
    }

    public boolean valid() {
        if (type < STROKE || type > SHELTER || points.size() < 2 || points.size() > 512
                || points.size() % 2 != 0 || text.length() > 48) return false;
        if (type != STROKE && points.size() != 2) return false;
        for (int value : points) if (value < 0 || value > 127) return false;
        return true;
    }

    public static List<MapMark> fromTag(CompoundTag tag) {
        List<MapMark> result = new ArrayList<>();
        ListTag list = tag.getList("Marks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < 256; i++) {
            MapMark mark = load(list.getCompound(i));
            if (mark.valid()) result.add(mark);
        }
        return result;
    }
}
