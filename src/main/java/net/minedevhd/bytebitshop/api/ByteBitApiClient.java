package net.minedevhd.bytebitshop.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minedevhd.bytebitshop.ByteBitConfig;
import net.minedevhd.bytebitshop.model.ShopBot;
import net.minedevhd.bytebitshop.model.ShopComponent;
import net.minedevhd.bytebitshop.model.ShopOffer;
import net.minedevhd.bytebitshop.model.StockRef;

public final class ByteBitApiClient {
    private static final Gson GSON = new Gson();

    private final ByteBitConfig config;
    private final PlayerCertificateService certificates;
    private final NtpClock clock;

    public ByteBitApiClient(ByteBitConfig config, PlayerCertificateService certificates, NtpClock clock) {
        this.config = config;
        this.certificates = certificates;
        this.clock = clock;
    }

    public CompletableFuture<Map<String, String>> discoverBots() {
        return CompletableFuture.supplyAsync(() -> {
            LinkedHashMap<String, String> result = new LinkedHashMap<String, String>();
            List<SourceDescriptor> sources = new ArrayList<SourceDescriptor>();

            try {
                JsonObject root = getJson(config.staticApiUrl);
                JsonArray sourceArray = root == null ? null : array(root, "botSources");
                if (sourceArray != null) {
                    for (JsonElement element : sourceArray) {
                        if (!element.isJsonObject()) continue;
                        JsonObject object = element.getAsJsonObject();
                        String url = string(object, "url", null);
                        if (url == null || url.trim().isEmpty()) continue;
                        SourceDescriptor descriptor = new SourceDescriptor(url, string(object, "trustLevel", "HIGH"));
                        JsonArray members = array(object, "members");
                        if (members != null) {
                            for (JsonElement memberElement : members) {
                                if (!memberElement.isJsonObject()) continue;
                                JsonObject member = memberElement.getAsJsonObject();
                                String uuid = normalizeUuid(string(member, "uuid", ""));
                                if (!uuid.isEmpty())
                                    descriptor.memberTrust.put(uuid, string(member, "trustLevel", "LOW"));
                            }
                        }
                        sources.add(descriptor);
                    }
                }
            } catch (Exception ignored) {}

            for (String source : config.additionalBotSources) {
                if (source != null && !source.trim().isEmpty())
                    sources.add(new SourceDescriptor(source.trim(), "HIGH"));
            }

            for (SourceDescriptor source : sources) {
                try {
                    JsonObject response = getJson(join(source.url, "scope/getBots"));
                    if (response == null || !bool(response, "success", false)) continue;
                    JsonArray bots = array(response, "bots");
                    if (bots == null) continue;
                    for (JsonElement botElement : bots) {
                        String uuid = normalizeUuid(botElement.getAsString());
                        if (uuid.isEmpty() || !source.isTrusted(uuid)) continue;
                        result.put(uuid, source.url);
                    }
                } catch (Exception ignored) {}
            }
            return result;
        });
    }

    public CompletableFuture<ShopData> fetchShop(ShopBot bot) {
        return certificates.get().thenApplyAsync(certificate -> {
            try {
                long timestamp = clock.now();
                Signature signature = Signature.getInstance("SHA256withRSA");
                signature.initSign(certificate.privateKey);
                signature.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
                String signedTimestamp = Base64.getEncoder().encodeToString(signature.sign());

                Minecraft mc = Minecraft.getMinecraft();
                UUID userUuid = mc.thePlayer != null ? mc.thePlayer.getUniqueID() : parseUuid(mc.getSession().getPlayerID());
                if (userUuid == null) throw new IllegalStateException("Spieler-UUID nicht verfügbar");

                LinkedHashMap<String, String> form = new LinkedHashMap<String, String>();
                form.put("publickey", certificate.publicKey);
                form.put("signature", signedTimestamp);
                form.put("keySignature", certificate.keySignature == null ? "" : certificate.keySignature);
                form.put("timestamp", Long.toString(timestamp));
                form.put("expirationTime", Long.toString(certificate.expirationTime));
                form.put("uuid", userUuid.toString().replace("-", ""));

                JsonObject response = postForm(join(bot.getSourceUrl(), "item/getItems/" + bot.getUuid()), form);
                if (response == null) throw new IllegalStateException("Leere Shop-Antwort");
                if (!bool(response, "success", false))
                    throw new IllegalStateException(string(response, "message", "Byte-&-Bit-API meldet keinen Erfolg"));

                JsonObject zoneObject = object(response, "zone");
                if (zoneObject == null || zoneObject.entrySet().isEmpty())
                    throw new IllegalStateException("Dieser Bot verwendet aktuell keine einzelne unterstützte Shop-Zone");

                ShopBot.Zone zone = new ShopBot.Zone(
                    number(zoneObject, "x1", 0), number(zoneObject, "y1", 0), number(zoneObject, "z1", 0),
                    number(zoneObject, "x2", 0), number(zoneObject, "y2", 0), number(zoneObject, "z2", 0)
                );

                JsonArray items = array(response, "items");
                List<ShopOffer> offers = items == null ? Collections.<ShopOffer>emptyList() : parseOffers(items);
                return new ShopData(zone, offers);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private List<ShopOffer> parseOffers(JsonArray input) {
        LinkedHashMap<Long, ShopOffer> grouped = new LinkedHashMap<Long, ShopOffer>();

        for (JsonElement itemElement : input) {
            if (!itemElement.isJsonObject()) continue;
            JsonObject raw = itemElement.getAsJsonObject();
            JsonObject material = object(raw, "material");
            String materialName = material == null ? null : string(material, "name", null);
            int subId = material == null ? 0 : integer(material, "subID", 0);
            String displayName = string(raw, "displayName", "");
            int warehouse = integer(raw, "warehouseCount", Integer.MAX_VALUE);
            int repairCost = integer(raw, "repairCost", 0);
            StockRef stock = new StockRef(warehouse);

            JsonArray prices = array(raw, "prices");
            if (prices == null) continue;
            for (JsonElement priceElement : prices) {
                if (!priceElement.isJsonObject()) continue;
                JsonObject priceObject = priceElement.getAsJsonObject();
                String priceText = string(priceObject, "price", null);
                if (priceText == null) continue;
                BigDecimal price;
                try {
                    price = new BigDecimal(priceText);
                } catch (Exception ignored) {
                    continue;
                }
                if (price.signum() < 0) continue;
                long priceCents = price.multiply(new BigDecimal("100")).longValue();
                int amount = integer(priceObject, "amount", 1);

                Item item = materialName == null ? null : Item.getByNameOrId(materialName);
                if (item == null) item = Item.getItemFromBlock(Blocks.stone);
                ItemStack stack = new ItemStack(item, Math.max(0, amount), subId);
                stack.setRepairCost(repairCost);
                if (displayName != null && !displayName.isEmpty()) stack.setStackDisplayName("§r" + displayName);
                applyLore(stack, array(raw, "lore"));
                applyEnchantments(stack, array(raw, "enchantments"));

                ShopOffer offer = grouped.get(priceCents);
                if (offer == null) {
                    offer = new ShopOffer(priceCents);
                    grouped.put(priceCents, offer);
                }
                offer.addComponent(new ShopComponent(stack, stock));
            }
        }

        ArrayList<ShopOffer> offers = new ArrayList<ShopOffer>(grouped.values());
        Collections.sort(offers, new Comparator<ShopOffer>() {
            @Override
            public int compare(ShopOffer a, ShopOffer b) {
                if (a.isAvailable() != b.isAvailable()) return a.isAvailable() ? -1 : 1;
                ItemStack as = a.getDisplayStack();
                ItemStack bs = b.getDisplayStack();
                int ai = as == null ? Integer.MAX_VALUE : Item.getIdFromItem(as.getItem());
                int bi = bs == null ? Integer.MAX_VALUE : Item.getIdFromItem(bs.getItem());
                if (ai != bi) return ai < bi ? -1 : 1;
                return Long.compare(a.getPriceCents(), b.getPriceCents());
            }
        });
        return offers;
    }

    private static void applyLore(ItemStack stack, JsonArray loreArray) {
        if (loreArray == null || loreArray.size() == 0) return;
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound display = tag.hasKey("display", 10) ? tag.getCompoundTag("display") : new NBTTagCompound();
        NBTTagList lore = new NBTTagList();
        for (JsonElement element : loreArray) {
            if (!element.isJsonObject()) continue;
            lore.appendTag(new NBTTagString(string(element.getAsJsonObject(), "lineContent", "")));
        }
        display.setTag("Lore", lore);
        tag.setTag("display", display);
        stack.setTagCompound(tag);
    }

    private static void applyEnchantments(ItemStack stack, JsonArray enchArray) {
        if (enchArray == null || enchArray.size() == 0) return;
        NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (JsonElement element : enchArray) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            int id = integer(object, "id", 0);
            int level = integer(object, "level", 0);
            if (Enchantment.getEnchantmentById(id) == null && id == 0 && level == 0) continue;
            NBTTagCompound ench = new NBTTagCompound();
            ench.setShort("id", (short) id);
            ench.setShort("lvl", (short) level);
            list.appendTag(ench);
        }
        if (list.tagCount() > 0) {
            tag.setTag("ench", list);
            stack.setTagCompound(tag);
        }
    }

    private static JsonObject getJson(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "ByteBitShop/1.0.2");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        if (connection.getResponseCode() >= 400)
            throw new IllegalStateException("HTTP " + connection.getResponseCode());
        return readJson(connection.getInputStream());
    }

    private static JsonObject postForm(String url, Map<String, String> form) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", "ByteBitShop/1.0.2");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setDoOutput(true);

        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            body.append('=');
            body.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), "UTF-8"));
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        OutputStream out = connection.getOutputStream();
        try {
            out.write(bytes);
            out.flush();
        } finally {
            out.close();
        }
        if (connection.getResponseCode() >= 400)
            throw new IllegalStateException("HTTP " + connection.getResponseCode());
        return readJson(connection.getInputStream());
    }

    private static JsonObject readJson(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        try {
            return GSON.fromJson(reader, JsonObject.class);
        } finally {
            reader.close();
        }
    }

    private static String join(String base, String path) {
        if (base.endsWith("/") && path.startsWith("/")) return base + path.substring(1);
        if (!base.endsWith("/") && !path.startsWith("/")) return base + "/" + path;
        return base + path;
    }

    private static String normalizeUuid(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").trim().toLowerCase();
    }

    private static UUID parseUuid(String value) {
        if (value == null) return null;
        String raw = value.replace("-", "");
        if (raw.length() != 32) return null;
        try {
            return UUID.fromString(raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-" + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-" + raw.substring(20));
        } catch (Exception ex) {
            return null;
        }
    }

    private static JsonArray array(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonObject object(JsonObject object, String key) {
        JsonElement element = object == null ? null : object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object == null ? null : object.get(key);
        try { return element == null || element.isJsonNull() ? fallback : element.getAsString(); }
        catch (Exception ignored) { return fallback; }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = object == null ? null : object.get(key);
        try { return element == null || element.isJsonNull() ? fallback : element.getAsInt(); }
        catch (Exception ignored) { return fallback; }
    }

    private static double number(JsonObject object, String key, double fallback) {
        JsonElement element = object == null ? null : object.get(key);
        try { return element == null || element.isJsonNull() ? fallback : element.getAsDouble(); }
        catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject object, String key, boolean fallback) {
        JsonElement element = object == null ? null : object.get(key);
        try { return element == null || element.isJsonNull() ? fallback : element.getAsBoolean(); }
        catch (Exception ignored) { return fallback; }
    }

    public static final class ShopData {
        public final ShopBot.Zone zone;
        public final List<ShopOffer> offers;

        ShopData(ShopBot.Zone zone, List<ShopOffer> offers) {
            this.zone = zone;
            this.offers = offers;
        }
    }

    private static final class SourceDescriptor {
        final String url;
        final String trustLevel;
        final Map<String, String> memberTrust = new LinkedHashMap<String, String>();

        SourceDescriptor(String url, String trustLevel) {
            this.url = url;
            this.trustLevel = trustLevel == null ? "HIGH" : trustLevel.toUpperCase();
        }

        boolean isTrusted(String uuid) {
            if (!"LOW".equals(trustLevel)) return true;
            String trust = memberTrust.get(uuid);
            return trust != null && !"LOW".equalsIgnoreCase(trust);
        }
    }
}
