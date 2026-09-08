package net.minedevhd.bytebitshop.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minedevhd.bytebitshop.ByteBitConfig;
import net.minedevhd.bytebitshop.model.ShoppingCart;
import net.minedevhd.bytebitshop.util.Chat;

public final class PaymentQueue {
    private final ByteBitConfig config;
    private final Deque<String> queue = new ArrayDeque<String>();
    private long nextSend;
    private int total;
    private int sent;
    private String botName;

    public PaymentQueue(ByteBitConfig config) {
        this.config = config;
    }

    public synchronized boolean start(String botName, ShoppingCart cart) {
        if (!queue.isEmpty()) return false;
        if (botName == null || botName.trim().isEmpty()) return false;
        if (cart.isEmpty()) return false;
        if (cart.transactionCount() > 128) return false;

        List<ShoppingCart.Line> lines = cart.lines();
        for (ShoppingCart.Line line : lines) {
            for (int i = 0; i < line.quantity; i++)
                queue.addLast("/pay " + botName + " " + line.offer.getPayAmount());
        }
        this.botName = botName;
        this.total = queue.size();
        this.sent = 0;
        this.nextSend = 0L;
        return true;
    }

    public synchronized void tick() {
        if (queue.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            cancelInternal(false);
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextSend) return;

        String command = queue.pollFirst();
        if (command == null) return;
        mc.thePlayer.sendChatMessage(command);
        sent++;
        nextSend = now + 2500L;

        if (queue.isEmpty()) {
            Chat.info("Checkout an §f" + botName + " §7gesendet: §f" + sent + "§7/§f" + total + " §7Zahlung(en).§r");
            botName = null;
            total = sent = 0;
        }
    }

    public synchronized void cancel() {
        if (queue.isEmpty()) {
            Chat.info("Keine laufende Zahlung.");
            return;
        }
        int left = queue.size();
        cancelInternal(true);
        Chat.info("Checkout abgebrochen. §f" + left + " §7Zahlung(en) wurden nicht mehr gesendet.");
    }

    private void cancelInternal(boolean keepMessageState) {
        queue.clear();
        botName = null;
        total = sent = 0;
        nextSend = 0L;
    }

    public synchronized boolean isBusy() {
        return !queue.isEmpty();
    }

    public synchronized int getRemaining() {
        return queue.size();
    }
}
