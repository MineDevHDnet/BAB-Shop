package net.minedevhd.bytebitshop.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minedevhd.bytebitshop.ByteBitConfig;
import net.minedevhd.bytebitshop.api.ByteBitApiClient;
import net.minedevhd.bytebitshop.model.ShopBot;

public final class BotRegistry {
    private final ByteBitConfig config;
    private final ByteBitApiClient api;
    private final Map<String, String> sources = new ConcurrentHashMap<String, String>();
    private final Map<String, ShopBot> bots = new ConcurrentHashMap<String, ShopBot>();
    private volatile boolean refreshingSources;
    private volatile long lastSourceRefresh;

    public BotRegistry(ByteBitConfig config, ByteBitApiClient api) {
        this.config = config;
        this.api = api;
    }

    public void refreshSources(final Runnable callback) {
        if (refreshingSources) return;
        refreshingSources = true;
        api.discoverBots().whenComplete((discovered, error) -> {
            if (error == null && discovered != null) {
                sources.clear();
                sources.putAll(discovered);
                lastSourceRefresh = System.currentTimeMillis();
            }
            refreshingSources = false;
            if (callback != null)
                Minecraft.getMinecraft().addScheduledTask(callback);
        });
    }

    public void tickScan() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        if (!refreshingSources && System.currentTimeMillis() - lastSourceRefresh > config.sourceRefreshSeconds * 1000L)
            refreshSources(null);

        @SuppressWarnings("unchecked")
        List<EntityPlayer> players = new ArrayList<EntityPlayer>(mc.theWorld.playerEntities);
        for (EntityPlayer entity : players) {
            String uuid = normalize(entity.getUniqueID().toString());
            String source = sources.get(uuid);
            if (source == null) continue;

            ShopBot bot = bots.get(uuid);
            if (bot == null || !source.equals(bot.getSourceUrl())) {
                bot = new ShopBot(uuid, source);
                bots.put(uuid, bot);
            }
            bot.setName(entity.getName());

            double distanceSq = entity.getDistanceSqToEntity(mc.thePlayer);
            if (distanceSq <= 64D * 64D)
                ensureSynced(bot, false, null);
        }
    }

    public void ensureSynced(final ShopBot bot, boolean force, final Runnable callback) {
        if (bot == null) return;
        long staleAfter = config.botSyncSeconds * 1000L;
        if (!force && bot.getZone() != null && System.currentTimeMillis() - bot.getLastSync() < staleAfter) {
            if (callback != null) Minecraft.getMinecraft().addScheduledTask(callback);
            return;
        }
        if (bot.isSyncing()) return;

        bot.beginSync();
        api.fetchShop(bot).whenComplete((data, error) -> {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (error != null || data == null) {
                    Throwable cause = error;
                    while (cause != null && cause.getCause() != null) cause = cause.getCause();
                    bot.fail(cause == null ? "Unbekannter API-Fehler" : String.valueOf(cause.getMessage()));
                } else {
                    bot.apply(data.zone, data.offers);
                }
                if (callback != null) callback.run();
            });
        });
    }

    public ShopBot getActiveBot() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return null;
        Vec3 position = mc.thePlayer.getPositionVector();

        List<ShopBot> candidates = new ArrayList<ShopBot>();
        for (ShopBot bot : bots.values()) {
            if (bot.contains(position)) candidates.add(bot);
        }
        if (candidates.isEmpty()) return null;
        Collections.sort(candidates, new Comparator<ShopBot>() {
            @Override public int compare(ShopBot a, ShopBot b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return candidates.get(0);
    }

    public ShopBot getNearestRecognizedBot(double maxDistance) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return null;
        ShopBot nearest = null;
        double nearestDistance = maxDistance * maxDistance;

        @SuppressWarnings("unchecked")
        List<EntityPlayer> players = new ArrayList<EntityPlayer>(mc.theWorld.playerEntities);
        for (EntityPlayer entity : players) {
            String uuid = normalize(entity.getUniqueID().toString());
            String source = sources.get(uuid);
            if (source == null) continue;
            double distance = entity.getDistanceSqToEntity(mc.thePlayer);
            if (distance > nearestDistance) continue;

            ShopBot bot = bots.get(uuid);
            if (bot == null || !source.equals(bot.getSourceUrl())) {
                bot = new ShopBot(uuid, source);
                bots.put(uuid, bot);
            }
            bot.setName(entity.getName());
            nearest = bot;
            nearestDistance = distance;
        }
        return nearest;
    }

    public List<ShopBot> loadedBots() {
        return new ArrayList<ShopBot>(bots.values());
    }

    public int knownBotCount() {
        return sources.size();
    }

    public boolean isRefreshingSources() {
        return refreshingSources;
    }

    private static String normalize(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").toLowerCase();
    }
}
