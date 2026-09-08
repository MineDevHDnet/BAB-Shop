package net.minedevhd.bytebitshop.model;

import java.util.concurrent.atomic.AtomicInteger;

public final class StockRef {
    private final AtomicInteger value;

    public StockRef(int value) {
        this.value = new AtomicInteger(value);
    }

    public int get() {
        return value.get();
    }

    public void consume(int amount) {
        if (amount <= 0 || value.get() == Integer.MAX_VALUE) return;
        while (true) {
            int old = value.get();
            int next = Math.max(0, old - amount);
            if (value.compareAndSet(old, next)) return;
        }
    }
}
