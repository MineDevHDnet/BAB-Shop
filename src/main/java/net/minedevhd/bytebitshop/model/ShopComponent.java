package net.minedevhd.bytebitshop.model;

import net.minecraft.item.ItemStack;

public final class ShopComponent {
    private final ItemStack stack;
    private final StockRef stock;

    public ShopComponent(ItemStack stack, StockRef stock) {
        this.stack = stack;
        this.stock = stock;
    }

    public ItemStack getStack() {
        return stack.copy();
    }

    public StockRef getStock() {
        return stock;
    }

    public int amountPerPurchase() {
        return stack.stackSize;
    }
}
