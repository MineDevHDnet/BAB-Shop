package net.minedevhd.bytebitshop.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.Vec3;

public final class ShopBot {
    private final String uuid;
    private final String sourceUrl;
    private volatile String name;
    private volatile Zone zone;
    private volatile List<ShopOffer> offers = Collections.emptyList();
    private volatile long lastSync;
    private volatile boolean syncing;
    private volatile String lastError;

    public ShopBot(String uuid, String sourceUrl) {
        this.uuid = uuid;
        this.sourceUrl = sourceUrl;
    }

    public String getUuid() { return uuid; }
    public String getSourceUrl() { return sourceUrl; }
    public String getName() { return name == null || name.isEmpty() ? uuid.substring(0, Math.min(8, uuid.length())) : name; }
    public void setName(String name) { this.name = name; }
    public Zone getZone() { return zone; }
    public List<ShopOffer> getOffers() { return new ArrayList<ShopOffer>(offers); }
    public long getLastSync() { return lastSync; }
    public boolean isSyncing() { return syncing; }
    public String getLastError() { return lastError; }

    public void beginSync() { syncing = true; lastError = null; }

    public void apply(Zone zone, List<ShopOffer> offers) {
        this.zone = zone;
        this.offers = Collections.unmodifiableList(new ArrayList<ShopOffer>(offers));
        this.lastSync = System.currentTimeMillis();
        this.syncing = false;
        this.lastError = null;
    }

    public void fail(String error) {
        this.syncing = false;
        this.lastError = error;
        this.lastSync = System.currentTimeMillis();
    }

    public boolean contains(Vec3 vec) {
        return zone != null && zone.contains(vec);
    }

    public static final class Zone {
        public final double minX, minY, minZ, maxX, maxY, maxZ;

        public Zone(double x1, double y1, double z1, double x2, double y2, double z2) {
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
        }

        public boolean contains(Vec3 vec) {
            return vec.xCoord >= minX && vec.xCoord <= maxX
                && vec.yCoord >= minY && vec.yCoord <= maxY
                && vec.zCoord >= minZ && vec.zCoord <= maxZ;
        }
    }
}
