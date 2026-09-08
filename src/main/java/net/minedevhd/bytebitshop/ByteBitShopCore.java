package net.minedevhd.bytebitshop;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minedevhd.bytebitshop.api.ByteBitApiClient;
import net.minedevhd.bytebitshop.api.NtpClock;
import net.minedevhd.bytebitshop.api.PlayerCertificateService;
import net.minedevhd.bytebitshop.command.ByteBitShopCommand;
import net.minedevhd.bytebitshop.gui.ShopGui;
import net.minedevhd.bytebitshop.model.ShopBot;
import net.minedevhd.bytebitshop.runtime.BotRegistry;
import net.minedevhd.bytebitshop.runtime.PaymentQueue;
import net.minedevhd.bytebitshop.util.Chat;
import org.lwjgl.input.Keyboard;

public final class ByteBitShopCore {
    public static final String VERSION = "1.0.2";
    private static final ByteBitShopCore INSTANCE = new ByteBitShopCore();

    private final AtomicBoolean started = new AtomicBoolean(false);
    private ByteBitConfig config;
    private BotRegistry registry;
    private PaymentQueue paymentQueue;
    private NtpClock clock;
    private KeyBinding openKey;
    private int tickCounter;

    public static ByteBitShopCore get() {
        return INSTANCE;
    }

    private ByteBitShopCore() {}

    public void start(String loader) {
        if (!started.compareAndSet(false, true)) return;

        config = ByteBitConfig.load();
        clock = new NtpClock();
        PlayerCertificateService certificates = new PlayerCertificateService();
        ByteBitApiClient api = new ByteBitApiClient(config, certificates, clock);
        registry = new BotRegistry(config, api);
        paymentQueue = new PaymentQueue(config);

        openKey = new KeyBinding("Byte & Bit Shop öffnen", config.openKeyCode, "ByteBitShop");
        ClientRegistry.registerKeyBinding(openKey);
        ClientCommandHandler.instance.registerCommand(new ByteBitShopCommand(this));
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);

        clock.syncAsync();
        registry.refreshSources(null);
    }

    public void stop() {
        if (!started.compareAndSet(true, false)) return;
        try { FMLCommonHandler.instance().bus().unregister(this); } catch (Throwable ignored) {}
        try { MinecraftForge.EVENT_BUS.unregister(this); } catch (Throwable ignored) {}
        if (paymentQueue != null && paymentQueue.isBusy()) paymentQueue.cancel();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !started.get()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (paymentQueue != null) paymentQueue.tick();
        if (mc.isGamePaused()) return;

        tickCounter++;
        if (registry != null && tickCounter % 10 == 0) registry.tickScan();

        while (openKey != null && openKey.isPressed())
            openNearbyShop();
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        if (!started.get() || config == null || !config.showHudHint || registry == null) return;
        ShopBot bot = registry.getActiveBot();
        if (bot == null) return;
        String keyName = Keyboard.getKeyName(openKey == null ? config.openKeyCode : openKey.getKeyCode());
        event.left.add("§bByte & Bit§7: §f" + bot.getName() + " §8[§b" + keyName + "§8]");
    }

    public void openNearbyShop() {
        if (registry == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            Chat.error("Du musst auf einem Server sein.");
            return;
        }

        ShopBot active = registry.getActiveBot();
        if (active != null && !active.getOffers().isEmpty()) {
            mc.displayGuiScreen(new ShopGui(this, active));
            return;
        }

        final ShopBot nearest = registry.getNearestRecognizedBot(16D);
        if (nearest == null) {
            if (registry.isRefreshingSources())
                Chat.info("Bot-Liste wird gerade geladen. Versuch es gleich erneut.");
            else
                Chat.error("Kein unterstützter Byte-&-Bit-Bot in der Nähe erkannt.");
            return;
        }

        Chat.info("Lade Shopdaten von §f" + nearest.getName() + "§7 ...");
        registry.ensureSynced(nearest, true, new Runnable() {
            @Override public void run() {
                Minecraft mc = Minecraft.getMinecraft();
                if (nearest.getLastError() != null) {
                    Chat.error("Shop konnte nicht geladen werden: " + nearest.getLastError());
                    return;
                }
                boolean inZone = nearest.contains(mc.thePlayer.getPositionVector());
                if (config.requireBotZone && !inZone) {
                    Chat.error("Shopdaten gefunden, aber du stehst nicht in der Bot-Zone.");
                    return;
                }
                mc.displayGuiScreen(new ShopGui(ByteBitShopCore.this, nearest));
            }
        });
    }

    public void refreshSources(final boolean chatFeedback) {
        if (registry == null) return;
        if (chatFeedback) Chat.info("Aktualisiere Byte-&-Bit-Botquellen ...");
        registry.refreshSources(chatFeedback ? new Runnable() {
            @Override public void run() {
                Chat.info("Botquellen aktualisiert: §f" + registry.knownBotCount() + " §7Bot(s) bekannt.");
            }
        } : null);
    }

    public ByteBitConfig getConfig() { return config; }
    public BotRegistry getRegistry() { return registry; }
    public PaymentQueue getPaymentQueue() { return paymentQueue; }
}
