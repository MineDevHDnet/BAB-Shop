package net.minedevhd.bytebitshop;

import java.util.List;
import net.labymod.api.LabyModAddon;
import net.labymod.settings.elements.SettingsElement;

public final class ByteBitShopAddon extends LabyModAddon {
    @Override
    public void onEnable() {
        ByteBitShopCore.get().start("LabyMod 3");
    }

    @Override
    public void onDisable() {
        ByteBitShopCore.get().stop();
    }

    @Override public void loadConfig() {}
    @Override protected void fillSettings(List<SettingsElement> settings) {}
}
