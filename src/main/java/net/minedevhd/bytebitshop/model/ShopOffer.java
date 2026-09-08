package net.minedevhd.bytebitshop.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.item.ItemStack;

public final class ShopOffer {
    private final long priceCents;
    private final List<ShopComponent> components = new ArrayList<ShopComponent>();

    public ShopOffer(long priceCents) {
        this.priceCents = priceCents;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public BigDecimal getPrice() {
        return BigDecimal.valueOf(priceCents, 2);
    }

    public String getPayAmount() {
        return getPrice().stripTrailingZeros().toPlainString();
    }

    public List<ShopComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }

    public void addComponent(ShopComponent component) {
        components.add(component);
    }

    public ItemStack getDisplayStack() {
        if (components.isEmpty()) return null;
        return components.get(0).getStack();
    }

    public String getDisplayName() {
        ItemStack stack = getDisplayStack();
        if (stack == null) return "Unbekanntes Item";
        return stack.getDisplayName();
    }

    public int getMaximumPurchases() {
        int max = Integer.MAX_VALUE;
        for (ShopComponent component : components) {
            int amount = component.amountPerPurchase();
            if (amount <= 0) continue;
            int stock = component.getStock().get();
            if (stock == Integer.MAX_VALUE) continue;
            max = Math.min(max, stock / amount);
        }
        return max;
    }

    public boolean isUnlimited() {
        return getMaximumPurchases() == Integer.MAX_VALUE;
    }

    public boolean isAvailable() {
        return getMaximumPurchases() > 0;
    }

    @Override
    public int hashCode() {
        return (int) (priceCents ^ (priceCents >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ShopOffer && ((ShopOffer) obj).priceCents == priceCents;
    }
}
