package net.minedevhd.bytebitshop;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(
    modid = ByteBitShopMod.MOD_ID,
    name = "ByteBitShop",
    version = ByteBitShopCore.VERSION,
    acceptedMinecraftVersions = "[1.8.9]",
    clientSideOnly = true
)
public final class ByteBitShopMod {
    public static final String MOD_ID = "bytebitshop";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ByteBitShopCore.get().start("Forge");
    }
}
