package com.lazarus.maps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.Optional;

@Mod(LazarusMaps.ID)
public final class LazarusMaps {
    public static final String ID = "lazarusmaps";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final RegistryObject<Item> GPS = ITEMS.register("gps", () -> new Item(new Item.Properties().stacksTo(1)));
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ID, "notes"), () -> "1", "1"::equals, "1"::equals);

    public LazarusMaps() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        CHANNEL.registerMessage(0, NotesNetwork.Request.class, NotesNetwork.Request::encode,
                NotesNetwork.Request::decode, NotesNetwork.Request::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, NotesNetwork.Edit.class, NotesNetwork.Edit::encode,
                NotesNetwork.Edit::decode, NotesNetwork.Edit::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, NotesNetwork.Snapshot.class, NotesNetwork.Snapshot::encode,
                NotesNetwork.Snapshot::decode, NotesNetwork.Snapshot::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
