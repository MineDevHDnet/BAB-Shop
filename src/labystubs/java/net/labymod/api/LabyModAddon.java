package net.labymod.api;

import java.util.List;
import net.labymod.settings.elements.SettingsElement;

public abstract class LabyModAddon {
    public abstract void onEnable();
    public abstract void onDisable();
    public abstract void loadConfig();
    protected abstract void fillSettings(List<SettingsElement> settings);
}
