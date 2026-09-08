package net.minedevhd.bytebitshop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

public final class ByteBitConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String staticApiUrl = "https://api.grieferutils.l3g7.dev/v6/";
    public List<String> additionalBotSources = new ArrayList<String>();
    public int openKeyCode = Keyboard.KEY_RETURN;
    public int paymentDelayMs = 2500;
    public boolean requireBotZone = true;
    public boolean showHudHint = true;
    public int sourceRefreshSeconds = 300;
    public int botSyncSeconds = 20;

    public static ByteBitConfig load() {
        File file = file();
        if (!file.exists()) {
            ByteBitConfig config = new ByteBitConfig();
            config.save();
            return config;
        }

        try {
            FileReader reader = new FileReader(file);
            try {
                ByteBitConfig config = GSON.fromJson(reader, ByteBitConfig.class);
                if (config == null) config = new ByteBitConfig();
                if (config.additionalBotSources == null) config.additionalBotSources = new ArrayList<String>();
                config.paymentDelayMs = 2500;
                config.sourceRefreshSeconds = Math.max(30, config.sourceRefreshSeconds);
                config.botSyncSeconds = Math.max(5, config.botSyncSeconds);
                return config;
            } finally {
                reader.close();
            }
        } catch (Exception ignored) {
            ByteBitConfig config = new ByteBitConfig();
            config.save();
            return config;
        }
    }

    public void save() {
        File file = file();
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try {
            FileWriter writer = new FileWriter(file);
            try {
                GSON.toJson(this, writer);
            } finally {
                writer.close();
            }
        } catch (Exception ignored) {}
    }

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/bytebitshop.json");
    }
}
