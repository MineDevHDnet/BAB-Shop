package net.minedevhd.bytebitshop.gui;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minedevhd.bytebitshop.ByteBitShopCore;
import net.minedevhd.bytebitshop.model.ShopBot;
import net.minedevhd.bytebitshop.model.ShopComponent;
import net.minedevhd.bytebitshop.model.ShopOffer;
import net.minedevhd.bytebitshop.model.ShoppingCart;
import net.minedevhd.bytebitshop.model.StockRef;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * GrieferUtils-style Byte & Bit bot shop. A standalone implementation, without
 * depending on GrieferUtils or replacing its classes/resources.
 *
 * 7 rows x 9 columns: cart / action column / 7x7 shop offers.
 * Unlike the original GU GUI, the payment queue sends /pay commands at a safe
 * interval rather than emitting the entire cart in the same client tick.
 */
public final class ClassicShopGui extends GuiScreen {
    private static final ResourceLocation CHEST =
        new ResourceLocation("textures/gui/container/generic_54.png");
    private static final DecimalFormat PRICE =
        new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.GERMANY));

    private static final int ROWS = 7;
    private static final int SHOP_COLUMNS = 7;
    private static final int W = 176;
    private static final int H = 17 + ROWS * 18 + 7;
    private static final int SLOT = 18;
    private static final int SCROLL_X = 180;
    private static final int SCROLL_Y = 18;
    private static final int SCROLL_H = ROWS * SLOT - 1;

    private final ByteBitShopCore core;
    private final ShopBot bot;
    private final ShoppingCart cart = new ShoppingCart();
    private final List<CatalogEntry> filtered = new ArrayList<CatalogEntry>();
    private final List<CatalogEntry> cartSlots = new ArrayList<CatalogEntry>();
    private final ItemStack divider = new ItemStack(Blocks.stained_glass_pane, 1, 15);
    private final ItemStack empty = new ItemStack(Blocks.stained_glass_pane, 1, 14);
    private final ItemStack buy = new ItemStack(Items.emerald, 1);

    private GuiTextField search;
    private int left;
    private int top;
    private int scrollRow;
    private boolean draggingScroll;
    private String feedback = "";
    private long feedbackUntil;

    public ClassicShopGui(ByteBitShopCore core, ShopBot bot) {
        this.core = core;
        this.bot = bot;
        divider.setStackDisplayName("§f§l⬅ Einkaufsliste");
        empty.setStackDisplayName("§cAusverkauft");
        buy.setStackDisplayName("§a§lKaufen");
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        left = (width - (W + 16)) / 2;
        top = (height - H) / 2;
        String oldSearch = search == null ? "" : search.getText();
        search = new GuiTextField(0, fontRendererObj, left + 85, top + 4, 78, 12);
        search.setMaxStringLength(80);
        search.setEnableBackgroundDrawing(false);
        search.setTextColor(0xFFFFFF);
        search.setText(oldSearch);
        search.setFocused(true);
        applyFilter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        draggingScroll = false;
    }

    @Override
    public void updateScreen() {
        if (search != null) search.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(CHEST);
        drawTexturedModalRect(left, top, 0, 0, W, 17);
        for (int row = 0; row < ROWS; row++)
            drawTexturedModalRect(left, top + 17 + row * SLOT, 0, 17, W, SLOT);
        drawTexturedModalRect(left, top + 17 + ROWS * SLOT, 0, 125, W, 7);

        renderTitle();
        if (search != null) {
            search.drawTextBox();
            if (search.getText().isEmpty() && !search.isFocused())
                fontRendererObj.drawString("§8Suchen", left + 87, top + 6, 0x888888);
        }

        ItemStack tooltipStack = null;
        ShopOffer tooltipOffer = null;
        String customTooltip = null;
        int hoverRow = slotRow(mouseY);
        int hoverCol = slotColumn(mouseX);
        List<CatalogEntry> currentCart = expandedCart();

        for (int row = 0; row < ROWS; row++) {
            // Column 0: shopping list, one purchase per slot.
            int cartX = slotX(0);
            int sy = slotY(row);
            if (row < currentCart.size()) {
                CatalogEntry entry = currentCart.get(row);
                ItemStack stack = entry.component.getStack();
                renderItem(stack, cartX, sy);
                if (hoverRow == row && hoverCol == 0) {
                    tooltipStack = stack;
                    tooltipOffer = entry.offer;
                }
            }

            // Column 1: dividing panes and checkout button in the center.
            if (row == 3) {
                if (!currentCart.isEmpty()) {
                    renderItem(buy, slotX(1), sy);
                    if (hoverRow == row && hoverCol == 1)
                        customTooltip = core.getPaymentQueue().isBusy()
                            ? "§cEine Zahlung läuft bereits."
                            : "§aKaufen §7(" + money(cart.total()) + "§7)";
                }
            } else {
                renderItem(divider, slotX(1), sy);
                if (hoverRow == row && hoverCol == 1)
                    customTooltip = "§f§l⬅ Einkaufsliste";
            }

            // Columns 2-8: the 49 visible shop items.
            for (int col = 0; col < SHOP_COLUMNS; col++) {
                int index = (scrollRow + row) * SHOP_COLUMNS + col;
                if (index >= filtered.size()) continue;
                CatalogEntry entry = filtered.get(index);
                int x = slotX(col + 2);
                ItemStack stack = entry.component.getStack();
                if (stack == null) continue;
                renderItem(stack, x, sy);

                int available = remainingPurchases(entry.offer);
                if (available <= 0)
                    drawRect(x, sy, x + 16, sy + 16, 0x77E04C4C);
                if (hoverRow == row && hoverCol == col + 2) {
                    tooltipStack = stack;
                    tooltipOffer = entry.offer;
                }
            }
        }

        renderScrollBar(mouseX, mouseY);
        if (feedbackUntil > System.currentTimeMillis())
            drawCenteredString(fontRendererObj, feedback, left + 88, top + H + 15, 0xFFFFFF);
        else
            drawCenteredString(fontRendererObj,
                "§7" + filtered.size() + " Angebote  §8|  §7Mausrad: Blättern",
                left + 88, top + H + 15, 0xBBBBBB);

        if (tooltipStack != null) {
            List<String> lines = new ArrayList<String>(tooltipStack.getTooltip(mc.thePlayer,
                mc.gameSettings.advancedItemTooltips));
            if (tooltipOffer != null) {
                lines.add("§7Preis: §e" + money(tooltipOffer.getPrice()));
                int leftStock = remainingPurchases(tooltipOffer);
                lines.add(leftStock == Integer.MAX_VALUE ? "§aVerfügbar"
                    : (leftStock > 7 ? "§aVerfügbar (" + leftStock + ")"
                    : leftStock > 0 ? "§eKnapp (" + leftStock + ")" : "§cAusverkauft"));
                if (tooltipOffer.getComponents().size() > 1) {
                    lines.add("§7Paketinhalt:");
                    for (ShopComponent component : tooltipOffer.getComponents())
                        lines.add("§8- §f" + component.getStack().stackSize + "x "
                            + clean(component.getStack().getDisplayName()));
                }
                lines.add(hoverCol == 0 ? "§eKlicken: aus Einkaufsliste entfernen"
                    : "§eKlicken: zur Einkaufsliste hinzufügen");
            }
            drawHoveringText(lines, mouseX, mouseY);
        } else if (customTooltip != null) {
            drawHoveringText(Collections.singletonList(customTooltip), mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void renderTitle() {
        String total = money(cart.total());
        boolean busy = core.getPaymentQueue().isBusy();
        fontRendererObj.drawString(
            busy ? "§cZahlung..." : (cart.isEmpty() ? "§0" : "§0" + total),
            left + 8, top + 6, 0x202020
        );
    }

    private void renderScrollBar(int mouseX, int mouseY) {
        int max = maxScroll();
        int x = left + SCROLL_X;
        int y = top + SCROLL_Y;
        drawRect(x, y, x + 12, y + SCROLL_H, 0xCC202020);
        if (max == 0) {
            drawRect(x + 2, y + 1, x + 10, y + SCROLL_H - 1, 0xFF555555);
            return;
        }
        int thumb = Math.max(16, (SCROLL_H * ROWS) / (max + ROWS));
        int pos = (SCROLL_H - thumb) * scrollRow / max;
        drawRect(x + 2, y + pos, x + 10, y + pos + thumb,
            draggingScroll ? 0xFFBDBDBD : 0xFF878787);
        if (mouseX >= x && mouseX <= x + 12 && mouseY >= y && mouseY <= y + SCROLL_H)
            drawCenteredString(fontRendererObj, "§7" + (scrollRow + 1)
                + "/" + (max + 1), x + 6, y - 13, 0xCCCCCC);
    }

    private void renderItem(ItemStack stack, int x, int y) {
        if (stack == null) return;
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        mc.getRenderItem().renderItemOverlays(fontRendererObj, stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
    }

    private void applyFilter() {
        if (search == null) return;
        String query = clean(search.getText()).toLowerCase(Locale.ROOT);
        filtered.clear();
        for (ShopOffer offer : bot.getOffers()) {
            for (ShopComponent component : offer.getComponents()) {
                CatalogEntry entry = new CatalogEntry(offer, component);
                if (matches(entry, query)) filtered.add(entry);
            }
        }
        Collections.sort(filtered, new Comparator<CatalogEntry>() {
            @Override
            public int compare(CatalogEntry a, CatalogEntry b) {
                int ac = remainingPurchases(a.offer), bc = remainingPurchases(b.offer);
                if ((ac == 0) != (bc == 0)) return ac == 0 ? 1 : -1;
                ItemStack ai = a.component.getStack(), bi = b.component.getStack();
                int aid = Item.getIdFromItem(ai.getItem());
                int bid = Item.getIdFromItem(bi.getItem());
                if (aid != bid) return Integer.compare(aid, bid);
                if (ai.getItemDamage() != bi.getItemDamage())
                    return Integer.compare(ai.getItemDamage(), bi.getItemDamage());
                if (ai.stackSize != bi.stackSize)
                    return Integer.compare(ai.stackSize, bi.stackSize);
                return Long.compare(a.offer.getPriceCents(), b.offer.getPriceCents());
            }
        });
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    private boolean matches(CatalogEntry entry, String query) {
        if (query.isEmpty()) return true;
        if (money(entry.offer.getPrice()).toLowerCase(Locale.ROOT).contains(query)) return true;
        ItemStack item = entry.component.getStack();
        if (clean(item.getDisplayName()).toLowerCase(Locale.ROOT).contains(query)) return true;
        if (clean(item.getUnlocalizedName()).toLowerCase(Locale.ROOT).contains(query)) return true;
        if (clean(item.getItem().getItemStackDisplayName(item))
            .toLowerCase(Locale.ROOT).contains(query)) return true;
        if (item.hasTagCompound() && item.getTagCompound().hasKey("display", 10)) {
            net.minecraft.nbt.NBTTagCompound display =
                item.getTagCompound().getCompoundTag("display");
            if (display.hasKey("Lore", 9)) {
                net.minecraft.nbt.NBTTagList lore = display.getTagList("Lore", 8);
                for (int i = 0; i < lore.tagCount(); i++)
                    if (clean(lore.getStringTagAt(i)).toLowerCase(Locale.ROOT)
                        .contains(query)) return true;
            }
        }
        return false;
    }

    private List<CatalogEntry> expandedCart() {
        cartSlots.clear();
        for (ShoppingCart.Line line : cart.lines())
            for (int i = 0; i < line.quantity; i++)
                for (ShopComponent component : line.offer.getComponents())
                    cartSlots.add(new CatalogEntry(line.offer, component));
        return cartSlots;
    }

    private int cartItemCount() {
        int count = 0;
        for (ShoppingCart.Line line : cart.lines())
            count += line.quantity * line.offer.getComponents().size();
        return count;
    }

    private static final class CatalogEntry {
        final ShopOffer offer;
        final ShopComponent component;
        CatalogEntry(ShopOffer offer, ShopComponent component) {
            this.offer = offer;
            this.component = component;
        }
    }

    /**
     * Remaining purchases calculated across shared inventory refs, including
     * any already-reserved purchases in this GUI.
     */
    private int remainingPurchases(ShopOffer offer) {
        IdentityHashMap<StockRef, Long> reserved = new IdentityHashMap<StockRef, Long>();
        for (ShoppingCart.Line line : cart.lines()) {
            for (ShopComponent component : line.offer.getComponents()) {
                StockRef stock = component.getStock();
                if (stock.get() == Integer.MAX_VALUE) continue;
                Long old = reserved.get(stock);
                reserved.put(stock, (old == null ? 0L : old)
                    + (long) component.amountPerPurchase() * line.quantity);
            }
        }
        IdentityHashMap<StockRef, Long> required = new IdentityHashMap<StockRef, Long>();
        for (ShopComponent component : offer.getComponents()) {
            StockRef stock = component.getStock();
            int amount = component.amountPerPurchase();
            if (amount <= 0 || stock.get() == Integer.MAX_VALUE) continue;
            Long old = required.get(stock);
            required.put(stock, (old == null ? 0L : old) + amount);
        }
        int available = Integer.MAX_VALUE;
        for (StockRef stock : required.keySet()) {
            long need = required.get(stock);
            long used = reserved.containsKey(stock) ? reserved.get(stock) : 0L;
            long remaining = Math.max(0L, (long) stock.get() - used);
            available = Math.min(available, (int) Math.min(Integer.MAX_VALUE, remaining / need));
        }
        return available;
    }

    private int slotRow(int mouseY) {
        int dy = mouseY - (top + 18);
        return dy < 0 || dy >= ROWS * SLOT ? -1 : dy / SLOT;
    }

    private int slotColumn(int mouseX) {
        int dx = mouseX - (left + 8);
        return dx < 0 || dx >= 9 * SLOT ? -1 : dx / SLOT;
    }

    private int slotX(int col) { return left + 8 + col * SLOT; }
    private int slotY(int row) { return top + 18 + row * SLOT; }
    private int maxScroll() {
        return Math.max(0, (filtered.size() + SHOP_COLUMNS - 1) / SHOP_COLUMNS - ROWS);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        if (search != null) search.mouseClicked(mouseX, mouseY, button);
        if (mouseX >= left + SCROLL_X && mouseX <= left + SCROLL_X + 12
            && mouseY >= top + SCROLL_Y && mouseY <= top + SCROLL_Y + SCROLL_H) {
            draggingScroll = true;
            setScrollFromMouse(mouseY);
            return;
        }
        int row = slotRow(mouseY), col = slotColumn(mouseX);
        if (row < 0 || col < 0) return;

        if (col == 0) {
            List<CatalogEntry> entries = expandedCart();
            if (row >= entries.size()) return;
            ShopOffer offer = entries.get(row).offer;
            cart.setQuantity(offer, cart.getQuantity(offer) - 1);
            applyFilter();
            return;
        }
        if (col == 1 && row == 3) {
            checkout();
            return;
        }
        if (col < 2) return;
        int index = (scrollRow + row) * SHOP_COLUMNS + (col - 2);
        if (index < 0 || index >= filtered.size()) return;
        ShopOffer offer = filtered.get(index).offer;
        if (cartItemCount() + offer.getComponents().size() > ROWS) {
            message("§cEinkaufsliste voll (maximal 7 Artikel).");
            return;
        }
        if (!cart.add(offer, 1)) message("§cNicht genügend Lagerbestand.");
        applyFilter();
    }

    private void checkout() {
        if (cart.isEmpty()) return;
        if (core.getPaymentQueue().isBusy()) {
            message("§cEine Zahlung läuft bereits.");
            return;
        }
        if (!core.getPaymentQueue().start(bot.getName(), cart)) {
            message("§cZahlung konnte nicht gestartet werden.");
            return;
        }
        cart.consumeLocally();
        cart.clear();
        mc.displayGuiScreen(null);
    }

    private void message(String value) {
        feedback = value;
        feedbackUntil = System.currentTimeMillis() + 3500L;
    }

    private void setScrollFromMouse(int mouseY) {
        int max = maxScroll();
        if (max <= 0) return;
        int y = top + SCROLL_Y;
        scrollRow = Math.max(0, Math.min(max,
            (mouseY - y) * max / Math.max(1, SCROLL_H - 1)));
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (draggingScroll && button == 0) setScrollFromMouse(mouseY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingScroll = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0)
            scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow + (wheel > 0 ? -1 : 1)));
    }

    @Override
    protected void keyTyped(char character, int code) throws java.io.IOException {
        if (code == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        String before = search.getText();
        if (search.textboxKeyTyped(character, code)) {
            if (!before.equals(search.getText())) {
                scrollRow = 0;
                applyFilter();
            }
            return;
        }
        super.keyTyped(character, code);
    }

    private static String clean(String text) {
        return text == null ? "" : text.replaceAll("§.", "");
    }

    private static String money(BigDecimal value) {
        synchronized (PRICE) {
            return PRICE.format(value) + "$";
        }
    }
}
