package net.minedevhd.bytebitshop.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShoppingCart {
    private final LinkedHashMap<ShopOffer, Integer> quantities = new LinkedHashMap<ShopOffer, Integer>();

    public int getQuantity(ShopOffer offer) {
        Integer value = quantities.get(offer);
        return value == null ? 0 : value;
    }

    public boolean setQuantity(ShopOffer offer, int quantity) {
        quantity = Math.max(0, Math.min(64, quantity));
        LinkedHashMap<ShopOffer, Integer> copy = new LinkedHashMap<ShopOffer, Integer>(quantities);
        if (quantity == 0) copy.remove(offer);
        else copy.put(offer, quantity);
        if (!fitsStock(copy)) return false;
        quantities.clear();
        quantities.putAll(copy);
        return true;
    }

    public boolean add(ShopOffer offer, int amount) {
        return setQuantity(offer, getQuantity(offer) + amount);
    }

    public void clear() {
        quantities.clear();
    }

    public boolean isEmpty() {
        return quantities.isEmpty();
    }

    public int transactionCount() {
        int count = 0;
        for (Integer quantity : quantities.values()) count += quantity;
        return count;
    }

    public long totalCents() {
        long total = 0L;
        for (Map.Entry<ShopOffer, Integer> entry : quantities.entrySet())
            total += entry.getKey().getPriceCents() * (long) entry.getValue();
        return total;
    }

    public BigDecimal total() {
        return BigDecimal.valueOf(totalCents(), 2);
    }

    public List<Line> lines() {
        ArrayList<Line> list = new ArrayList<Line>();
        for (Map.Entry<ShopOffer, Integer> entry : quantities.entrySet())
            list.add(new Line(entry.getKey(), entry.getValue()));
        return list;
    }

    public void consumeLocally() {
        for (Map.Entry<ShopOffer, Integer> entry : quantities.entrySet()) {
            int quantity = entry.getValue();
            for (ShopComponent component : entry.getKey().getComponents()) {
                long amount = (long) component.amountPerPurchase() * quantity;
                component.getStock().consume(amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount);
            }
        }
    }

    private static boolean fitsStock(Map<ShopOffer, Integer> cart) {
        IdentityHashMap<StockRef, Long> reserved = new IdentityHashMap<StockRef, Long>();
        for (Map.Entry<ShopOffer, Integer> entry : cart.entrySet()) {
            int quantity = entry.getValue();
            for (ShopComponent component : entry.getKey().getComponents()) {
                int perPurchase = component.amountPerPurchase();
                if (perPurchase <= 0) continue;
                StockRef stock = component.getStock();
                if (stock.get() == Integer.MAX_VALUE) continue;
                Long old = reserved.get(stock);
                long next = (old == null ? 0L : old) + (long) perPurchase * quantity;
                if (next > stock.get()) return false;
                reserved.put(stock, next);
            }
        }
        return true;
    }

    public static final class Line {
        public final ShopOffer offer;
        public final int quantity;

        Line(ShopOffer offer, int quantity) {
            this.offer = offer;
            this.quantity = quantity;
        }
    }
}
