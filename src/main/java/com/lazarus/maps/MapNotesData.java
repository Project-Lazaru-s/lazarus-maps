package com.lazarus.maps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/** Saved on the server, keyed by the vanilla map ID shared by copied maps. */
public final class MapNotesData extends SavedData {
    private final Map<Integer, List<MapMark>> maps = new HashMap<>();
    private int nextId = 1;

    public static MapNotesData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                MapNotesData::load, MapNotesData::new, "lazarusmaps_notes");
    }

    private static MapNotesData load(CompoundTag tag) {
        MapNotesData data = new MapNotesData();
        data.nextId = Math.max(1, tag.getInt("NextId"));
        ListTag maps = tag.getList("Maps", Tag.TAG_COMPOUND);
        for (int i = 0; i < maps.size(); i++) {
            CompoundTag entry = maps.getCompound(i);
            int mapId = entry.getInt("MapId");
            if (mapId >= 0) data.maps.put(mapId, MapMark.fromTag(entry));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("Version", 1);
        tag.putInt("NextId", nextId);
        ListTag list = new ListTag();
        maps.forEach((mapId, marks) -> {
            CompoundTag entry = snapshot(mapId);
            list.add(entry);
        });
        tag.put("Maps", list);
        return tag;
    }

    public CompoundTag snapshot(int mapId) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MapId", mapId);
        ListTag list = new ListTag();
        for (MapMark mark : maps.getOrDefault(mapId, List.of())) list.add(mark.save());
        tag.put("Marks", list);
        return tag;
    }

    public boolean add(int mapId, MapMark mark) {
        if (!mark.valid()) return false;
        List<MapMark> marks = maps.computeIfAbsent(mapId, ignored -> new ArrayList<>());
        if (marks.size() >= 256) return false;
        mark.id = nextId++;
        marks.add(mark);
        setDirty();
        return true;
    }

    /** Moves or recolors a mark: same id and type, new points and color (and text for notes). */
    public boolean replace(int mapId, int markId, MapMark edited) {
        List<MapMark> marks = maps.get(mapId);
        if (marks == null || !edited.valid()) return false;
        for (int i = 0; i < marks.size(); i++) {
            MapMark old = marks.get(i);
            if (old.id != markId) continue;
            if (old.type != edited.type) return false;
            edited.id = markId;
            marks.set(i, edited);
            setDirty();
            return true;
        }
        return false;
    }

    public boolean remove(int mapId, int markId) {
        List<MapMark> marks = maps.get(mapId);
        if (marks == null || !marks.removeIf(mark -> mark.id == markId)) return false;
        setDirty();
        return true;
    }
}
