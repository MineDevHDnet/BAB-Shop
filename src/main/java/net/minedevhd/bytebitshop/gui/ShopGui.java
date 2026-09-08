package net.minedevhd.bytebitshop.gui;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minedevhd.bytebitshop.ByteBitShopCore;
import net.minedevhd.bytebitshop.model.ShopBot;
import net.minedevhd.bytebitshop.model.ShopComponent;
import net.minedevhd.bytebitshop.model.ShopOffer;
import net.minedevhd.bytebitshop.model.ShoppingCart;
import net.minedevhd.bytebitshop.util.Chat;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class ShopGui extends GuiScreen {
    private static final DecimalFormat PRICE = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMAN));
    private static final int HEADER_H = 48;
    private static final int SIDEBAR_W = 110;
    private static final int CART_W = 216;
    private static final int CARD_H = 64;
    private static final int GUI_MAX_W = 760;
    private static final int GUI_MAX_H = 420;
    private static final int GUI_MARGIN = 12;

    private final ByteBitShopCore core;
    private final ShopBot bot;
    private final ShoppingCart cart = new ShoppingCart();
    private final List<ShopOffer> filtered = new ArrayList<ShopOffer>();
    private final List<HitBox> hits = new ArrayList<HitBox>();

    private GuiTextField search;
    private int searchX;
    private int searchY;
    private int guiLeft;
    private int guiTop;
    private int guiRight;
    private int guiBottom;
    private int guiWidth;
    private int guiHeight;
    private Category category = Category.ALL;
    private int productScroll;
    private int cartScroll;
    private ShopOffer selected;
    private int selectedQty = 1;
    private boolean confirmCheckout;
    private String toast;
    private long toastUntil;

    public ShopGui(ByteBitShopCore core, ShopBot bot) {
        this.core = core;
        this.bot = bot;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        guiWidth = Math.max(1, Math.min(GUI_MAX_W, width - GUI_MARGIN * 2));
        guiHeight = Math.max(1, Math.min(GUI_MAX_H, height - GUI_MARGIN * 2));
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;
        guiRight = guiLeft + guiWidth;
        guiBottom = guiTop + guiHeight;

        int mainLeft = guiLeft + SIDEBAR_W + 14;
        int mainRight = guiRight - CART_W - 14;
        int searchWidth = Math.max(100, Math.min(238, mainRight - mainLeft - 96));
        searchX = mainLeft;
        searchY = guiTop + 14;
        search = new GuiTextField(1, fontRendererObj, searchX, searchY, searchWidth, 20);
        search.setMaxStringLength(80);
        search.setEnableBackgroundDrawing(true);
        search.setTextColor(0xF2F5F7);
        rebuildFilter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        if (search != null) search.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hits.clear();
        drawGradientRect(0, 0, width, height, 0xFF080607, 0xFF12090C);
        drawRect(guiLeft - 1, guiTop - 1, guiRight + 1, guiBottom + 1, 0xFF4A1B24);
        drawRect(guiLeft, guiTop, guiRight, guiBottom, 0xFF120A0D);
        drawRect(guiLeft, guiTop, guiRight, guiTop + HEADER_H, 0xF0180B0F);
        drawRect(guiLeft, guiTop + HEADER_H, guiLeft + SIDEBAR_W, guiBottom, 0xE0140A0D);
        drawRect(guiRight - CART_W, guiTop + HEADER_H, guiRight, guiBottom, 0xF012090C);
        drawRect(guiLeft + SIDEBAR_W, guiTop + HEADER_H, guiLeft + SIDEBAR_W + 1, guiBottom, 0xFF4A2229);
        drawRect(guiRight - CART_W - 1, guiTop + HEADER_H, guiRight - CART_W, guiBottom, 0xFF4A2229);

        drawHeader(mouseX, mouseY);
        drawSidebar(mouseX, mouseY);
        drawProducts(mouseX, mouseY);
        drawCart(mouseX, mouseY);

        if (selected != null) drawProductModal(mouseX, mouseY);
        else if (confirmCheckout) drawCheckoutModal(mouseX, mouseY);

        if (toast != null && System.currentTimeMillis() < toastUntil) {
            int w = fontRendererObj.getStringWidth(toast) + 24;
            int x = (width - w) / 2;
            int toastY = Math.min(height - 34, guiBottom - 32);
            drawRect(x, toastY, x + w, toastY + 22, 0xEE321018);
            drawCenteredString(fontRendererObj, toast, width / 2, toastY + 7, 0xF5F7FA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawHeader(int mouseX, int mouseY) {
        fontRendererObj.drawString("§c§lBYTE & BIT", guiLeft + 12, guiTop + 10, 0xFFFFFF);
        fontRendererObj.drawString("§7Shop: §f" + trim(bot.getName(), SIDEBAR_W - 18), guiLeft + 12, guiTop + 27, 0xFFFFFF);

        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused())
            fontRendererObj.drawString("§8Items durchsuchen ...", searchX + 5, searchY + 6, 0xFFFFFF);

        int x = guiRight - CART_W + 10;
        button(x, guiTop + 13, 90, 22, "Aktualisieren", mouseX, mouseY, new Runnable() {
            @Override public void run() {
                toast("Shop wird aktualisiert ...");
                core.getRegistry().ensureSynced(bot, true, new Runnable() {
                    @Override public void run() {
                        rebuildFilter();
                        toast(bot.getLastError() == null ? "Shop aktualisiert" : "Fehler: " + bot.getLastError());
                    }
                });
            }
        });
    }

    private void drawSidebar(int mouseX, int mouseY) {
        fontRendererObj.drawString("§7KATEGORIEN", guiLeft + 10, guiTop + HEADER_H + 11, 0xFFFFFF);
        int y = guiTop + HEADER_H + 28;
        for (final Category c : Category.values()) {
            int count = countFor(c);
            boolean active = category == c;
            int itemX = guiLeft + 7;
            int itemW = SIDEBAR_W - 14;
            boolean hover = inside(mouseX, mouseY, itemX, y, itemW, 26);
            int color = active ? 0xFF501722 : (hover ? 0xFF2A151A : 0x00101010);
            drawRect(itemX, y, itemX + itemW, y + 26, color);
            if (active) drawRect(itemX, y, itemX + 3, y + 26, 0xFFE04458);
            String countText = Integer.toString(count);
            int countX = guiLeft + SIDEBAR_W - 13 - fontRendererObj.getStringWidth(countText);
            String label = trimToWidth(c.label, Math.max(20, countX - (guiLeft + 14) - 5));
            fontRendererObj.drawString(label, guiLeft + 14, y + 6, active ? 0xFFFFFF : 0xD7DCE2);
            fontRendererObj.drawString(countText, countX, y + 6, 0x9A7F86);
            final int fy = y;
            hits.add(new HitBox(itemX, fy, itemW, 26, new Runnable() {
                @Override public void run() {
                    category = c;
                    productScroll = 0;
                    rebuildFilter();
                }
            }));
            y += 29;
        }

        int infoY = guiBottom - 42;
        if (infoY > y + 4) {
            fontRendererObj.drawString("§8Hotkey / /babshop", guiLeft + 10, infoY, 0xFFFFFF);
            fontRendererObj.drawString("§8Standalone 1.0.2", guiLeft + 10, infoY + 13, 0xFFFFFF);
        }
    }

    private void drawProducts(int mouseX, int mouseY) {
        int left = guiLeft + SIDEBAR_W + 14;
        int right = guiRight - CART_W - 14;
        int top = guiTop + HEADER_H + 12;
        int bottom = guiBottom - 13;
        int availableWidth = Math.max(120, right - left);
        int columns = Math.max(1, availableWidth / 142);
        int gap = 7;
        int cardW = (availableWidth - gap * (columns - 1)) / columns;
        int rowsVisible = Math.max(1, (bottom - top) / (CARD_H + gap));
        int totalRows = (filtered.size() + columns - 1) / columns;
        int maxScroll = Math.max(0, totalRows - rowsVisible);
        productScroll = clamp(productScroll, 0, maxScroll);

        fontRendererObj.drawString("§f§lAngebote", left, top - 1, 0xFFFFFF);
        String meta = filtered.size() + " Treffer";
        fontRendererObj.drawString("§8" + meta, right - fontRendererObj.getStringWidth(meta), top - 1, 0xFFFFFF);
        top += 17;

        int first = productScroll * columns;
        int capacity = (rowsVisible + 1) * columns;
        int end = Math.min(filtered.size(), first + capacity);
        ItemStack hoverStack = null;

        for (int index = first; index < end; index++) {
            final ShopOffer offer = filtered.get(index);
            int relative = index - first;
            int row = relative / columns;
            int col = relative % columns;
            int x = left + col * (cardW + gap);
            int y = top + row * (CARD_H + gap);
            if (y + CARD_H > bottom) continue;

            boolean hover = inside(mouseX, mouseY, x, y, cardW, CARD_H);
            boolean available = offer.isAvailable();
            drawRect(x, y, x + cardW, y + CARD_H, hover ? 0xFF32171D : 0xFF211116);
            drawRect(x, y, x + cardW, y + 2, available ? 0xFFE04458 : 0xFF5B4A4E);

            ItemStack stack = offer.getDisplayStack();
            if (stack != null) {
                renderItem(stack, x + 10, y + 20, 1.25F);
                if (hover) hoverStack = stack;
            }

            int textX = x + 39;
            String name = clean(offer.getDisplayName());
            fontRendererObj.drawString("§f" + trimToWidth(name, cardW - 47), textX, y + 10, 0xFFFFFF);
            fontRendererObj.drawString("§c" + formatPrice(offer.getPrice()), textX, y + 26, 0xFFFFFF);
            String stock = stockText(offer);
            fontRendererObj.drawString(stock, textX, y + 42, 0xFFFFFF);

            final int fx = x;
            final int fy = y;
            hits.add(new HitBox(fx, fy, cardW, CARD_H, new Runnable() {
                @Override public void run() {
                    selected = offer;
                    selectedQty = Math.max(1, cart.getQuantity(offer));
                }
            }));
        }

        if (filtered.isEmpty()) {
            drawCenteredString(fontRendererObj, "§7Keine passenden Angebote gefunden.", (left + right) / 2, top + 50, 0xFFFFFF);
        }

        if (maxScroll > 0) {
            int barX = right + 5;
            int barTop = top;
            int barH = Math.max(40, bottom - top);
            drawRect(barX, barTop, barX + 3, barTop + barH, 0xFF28161A);
            int thumbH = Math.max(18, barH * rowsVisible / Math.max(rowsVisible, totalRows));
            int thumbY = barTop + (barH - thumbH) * productScroll / maxScroll;
            drawRect(barX, thumbY, barX + 3, thumbY + thumbH, 0xFFE04458);
        }

        if (hoverStack != null && selected == null && !confirmCheckout && !Mouse.isButtonDown(0))
            renderToolTip(hoverStack, mouseX, mouseY);
    }

    private void drawCart(int mouseX, int mouseY) {
        int x = guiRight - CART_W;
        int innerX = x + 13;
        int y = guiTop + HEADER_H + 12;
        fontRendererObj.drawString("§f§lWarenkorb", innerX, y, 0xFFFFFF);
        String count = cart.transactionCount() + " Kauf" + (cart.transactionCount() == 1 ? "" : "vorgänge");
        fontRendererObj.drawString("§8" + count, guiRight - 11 - fontRendererObj.getStringWidth(count), y, 0xFFFFFF);
        y += 19;

        List<ShoppingCart.Line> lines = cart.lines();
        int listBottom = guiBottom - 96;
        int rowH = 48;
        int visibleRows = Math.max(1, (listBottom - y) / rowH);
        int maxCartScroll = Math.max(0, lines.size() - visibleRows);
        cartScroll = clamp(cartScroll, 0, maxCartScroll);

        if (lines.isEmpty()) {
            drawCenteredString(fontRendererObj, "§8Noch nichts ausgewählt", x + CART_W / 2, y + 30, 0xFFFFFF);
        }

        for (int i = cartScroll; i < lines.size() && i < cartScroll + visibleRows; i++) {
            final ShoppingCart.Line line = lines.get(i);
            int rowY = y + (i - cartScroll) * rowH;
            drawRect(innerX, rowY, guiRight - 11, rowY + 42, 0xFF1D0F13);
            ItemStack stack = line.offer.getDisplayStack();
            if (stack != null) renderItem(stack, innerX + 7, rowY + 13, 0.95F);

            final int minusX = guiRight - 53;
            final int plusX = guiRight - 30;
            int textWidth = Math.max(38, minusX - (innerX + 30) - 6);
            String name = clean(line.offer.getDisplayName());
            fontRendererObj.drawString("§f" + trimToWidth(name, textWidth), innerX + 30, rowY + 7, 0xFFFFFF);
            fontRendererObj.drawString("§8" + line.quantity + " × §c" + trimToWidth(formatPrice(line.offer.getPrice()), textWidth), innerX + 30, rowY + 23, 0xFFFFFF);
            smallButton(minusX, rowY + 13, "−", mouseX, mouseY, new Runnable() {
                @Override public void run() { cart.add(line.offer, -1); }
            });
            smallButton(plusX, rowY + 13, "+", mouseX, mouseY, new Runnable() {
                @Override public void run() {
                    if (!cart.add(line.offer, 1)) toast("Nicht genug Lagerbestand");
                }
            });
        }

        int footerY = guiBottom - 86;
        drawRect(x, footerY, guiRight, footerY + 1, 0xFF4A2229);
        fontRendererObj.drawString("§7Summe", innerX, footerY + 13, 0xFFFFFF);
        String total = formatPrice(cart.total());
        fontRendererObj.drawString("§f§l" + total, guiRight - 11 - fontRendererObj.getStringWidth(total), footerY + 13, 0xFFFFFF);
        fontRendererObj.drawString("§8" + cart.transactionCount() + " einzelne /pay-Zahlung(en)", innerX, footerY + 31, 0xFFFFFF);

        boolean canCheckout = !cart.isEmpty() && cart.transactionCount() <= 128 && !core.getPaymentQueue().isBusy();
        final int buttonY = footerY + 51;
        if (canCheckout) {
            button(innerX, buttonY, CART_W - 26, 30, "Checkout", mouseX, mouseY, new Runnable() {
                @Override public void run() { confirmCheckout = true; }
            });
        } else {
            drawRect(innerX, buttonY, guiRight - 11, buttonY + 28, 0xFF29161A);
            String text = core.getPaymentQueue().isBusy() ? "Checkout läuft ..." : (cart.transactionCount() > 128 ? "Max. 128 Zahlungen" : "Warenkorb leer");
            drawCenteredString(fontRendererObj, "§8" + text, x + CART_W / 2, buttonY + 10, 0xFFFFFF);
        }
    }

    private void drawProductModal(int mouseX, int mouseY) {
        hits.add(new HitBox(0, 0, width, height, new Runnable() { @Override public void run() {} }));
        drawRect(0, 0, width, height, 0xA8000000);
        int w = Math.min(380, guiWidth - 34);
        int h = Math.min(232, guiHeight - 34);
        int x = guiLeft + (guiWidth - w) / 2;
        int y = guiTop + (guiHeight - h) / 2;
        drawRect(x, y, x + w, y + h, 0xFF1B0D11);
        drawRect(x, y, x + w, y + 3, 0xFFE04458);

        ItemStack stack = selected.getDisplayStack();
        if (stack != null) renderItem(stack, x + 24, y + 28, 2.0F);
        fontRendererObj.drawString("§f§l" + trimToWidth(clean(selected.getDisplayName()), w - 105), x + 78, y + 24, 0xFFFFFF);
        fontRendererObj.drawString("§c§l" + formatPrice(selected.getPrice()) + " §7pro Kauf", x + 78, y + 43, 0xFFFFFF);
        fontRendererObj.drawString(stockText(selected), x + 78, y + 60, 0xFFFFFF);

        int componentY = y + 88;
        fontRendererObj.drawString("§7Du erhältst pro Zahlung:", x + 22, componentY, 0xFFFFFF);
        componentY += 16;
        int shown = 0;
        for (ShopComponent component : selected.getComponents()) {
            if (shown >= 5) break;
            ItemStack componentStack = component.getStack();
            String line = "§f" + componentStack.stackSize + "× §7" + clean(componentStack.getDisplayName());
            fontRendererObj.drawString(trimToWidth(line, w - 44), x + 28, componentY, 0xFFFFFF);
            componentY += 13;
            shown++;
        }
        if (selected.getComponents().size() > shown)
            fontRendererObj.drawString("§8+ " + (selected.getComponents().size() - shown) + " weitere Komponente(n)", x + 28, componentY, 0xFFFFFF);

        int controlsY = y + h - 57;
        fontRendererObj.drawString("§7Menge", x + 22, controlsY + 8, 0xFFFFFF);
        smallButton(x + 72, controlsY, "−", mouseX, mouseY, new Runnable() {
            @Override public void run() { selectedQty = Math.max(1, selectedQty - 1); }
        });
        drawRect(x + 98, controlsY, x + 138, controlsY + 24, 0xFF12080B);
        drawCenteredString(fontRendererObj, "§f" + selectedQty, x + 118, controlsY + 8, 0xFFFFFF);
        smallButton(x + 142, controlsY, "+", mouseX, mouseY, new Runnable() {
            @Override public void run() { selectedQty = Math.min(64, selectedQty + 1); }
        });

        button(x + w - 190, controlsY - 2, 76, 28, "Zurück", mouseX, mouseY, new Runnable() {
            @Override public void run() { selected = null; }
        });
        button(x + w - 108, controlsY - 2, 86, 28, "Hinzufügen", mouseX, mouseY, new Runnable() {
            @Override public void run() {
                if (!selected.isAvailable()) {
                    toast("Dieses Angebot ist ausverkauft");
                    return;
                }
                if (!cart.setQuantity(selected, selectedQty)) {
                    toast("Nicht genug Lagerbestand für diese Menge");
                    return;
                }
                selected = null;
                toast("Zum Warenkorb hinzugefügt");
            }
        });
    }

    private void drawCheckoutModal(int mouseX, int mouseY) {
        hits.add(new HitBox(0, 0, width, height, new Runnable() { @Override public void run() {} }));
        drawRect(0, 0, width, height, 0xA8000000);
        int w = Math.min(350, guiWidth - 34);
        int h = Math.min(174, guiHeight - 28);
        int x = guiLeft + (guiWidth - w) / 2;
        int y = guiTop + (guiHeight - h) / 2;
        drawRect(x, y, x + w, y + h, 0xFF1B0D11);
        drawRect(x, y, x + w, y + 3, 0xFFE04458);

        drawCenteredString(fontRendererObj, "§f§lCheckout bestätigen", width / 2, y + 22, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "§7Bot: §f" + bot.getName(), width / 2, y + 46, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "§7Summe: §c§l" + formatPrice(cart.total()), width / 2, y + 62, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "§7Zahlungen: §f" + cart.transactionCount(), width / 2, y + 80, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "§8Jede /pay-Zahlung hat 2,5 Sekunden Abstand.", width / 2, y + 100, 0xFFFFFF);

        int modalButtonY = y + h - 40;
        button(x + 32, modalButtonY, 112, 28, "Abbrechen", mouseX, mouseY, new Runnable() {
            @Override public void run() { confirmCheckout = false; }
        });
        button(x + w - 144, modalButtonY, 112, 28, "Jetzt kaufen", mouseX, mouseY, new Runnable() {
            @Override public void run() { doCheckout(); }
        });
    }

    private void doCheckout() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            toast("Keine Serververbindung");
            return;
        }
        if (core.getConfig().requireBotZone && !bot.contains(mc.thePlayer.getPositionVector())) {
            toast("Du bist nicht mehr in der Bot-Zone");
            return;
        }
        if (cart.transactionCount() > 128) {
            toast("Maximal 128 Zahlungen pro Checkout");
            return;
        }
        if (core.getPaymentQueue().isBusy()) {
            toast("Es läuft bereits ein Checkout");
            return;
        }
        if (!core.getPaymentQueue().start(bot.getName(), cart)) {
            toast("Checkout konnte nicht gestartet werden");
            return;
        }

        cart.consumeLocally();
        int transactions = cart.transactionCount();
        String total = formatPrice(cart.total());
        cart.clear();
        confirmCheckout = false;
        Chat.info("Checkout gestartet: §f" + transactions + " §7Zahlung(en), Summe §c" + total + "§7, Delay §f2,5 s§7. Abbruch: §f/babshop cancel");
        mc.displayGuiScreen(null);
    }

    private void rebuildFilter() {
        filtered.clear();
        String query = search == null ? "" : clean(search.getText()).toLowerCase(Locale.ROOT).trim();
        for (ShopOffer offer : bot.getOffers()) {
            if (!matchesCategory(offer, category)) continue;
            if (!query.isEmpty() && !matchesSearch(offer, query)) continue;
            filtered.add(offer);
        }
    }

    private boolean matchesSearch(ShopOffer offer, String query) {
        String name = clean(offer.getDisplayName()).toLowerCase(Locale.ROOT);
        if (name.contains(query)) return true;
        if (offer.getPrice().toPlainString().contains(query.replace(',', '.'))) return true;
        for (ShopComponent component : offer.getComponents()) {
            ItemStack stack = component.getStack();
            String original = clean(stack.getDisplayName()).toLowerCase(Locale.ROOT);
            if (original.contains(query)) return true;
            try {
                if (stack.getUnlocalizedName().toLowerCase(Locale.ROOT).contains(query)) return true;
            } catch (Exception ignored) {}
            if (stack.hasTagCompound() && stack.getTagCompound().hasKey("display", 10)) {
                List<String> lore = stack.getTooltip(mc.thePlayer, false);
                for (String line : lore)
                    if (clean(line).toLowerCase(Locale.ROOT).contains(query)) return true;
            }
        }
        return false;
    }

    private boolean matchesCategory(ShopOffer offer, Category wanted) {
        if (wanted == Category.ALL) return true;
        ItemStack stack = offer.getDisplayStack();
        if (stack == null) return wanted == Category.OTHER;
        String key = (stack.getUnlocalizedName() + " " + clean(stack.getDisplayName())).toLowerCase(Locale.ROOT);

        Category actual;
        if (containsAny(key, "redstone", "repeater", "comparator", "hopper", "piston", "lever", "button", "pressure", "observer", "dispenser", "dropper"))
            actual = Category.REDSTONE;
        else if (containsAny(key, "sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots", "shears", "bow", "fishing"))
            actual = Category.TOOLS;
        else if (containsAny(key, "apple", "bread", "potato", "carrot", "beef", "pork", "chicken", "fish", "melon", "cookie", "cake", "food"))
            actual = Category.FOOD;
        else if (containsAny(key, "flower", "sapling", "leaves", "wool", "glass", "carpet", "banner", "skull", "painting", "frame", "torch", "fence"))
            actual = Category.DECO;
        else if (key.startsWith("tile.") || containsAny(key, "stone", "dirt", "sand", "brick", "planks", "log", "ore", "block"))
            actual = Category.BLOCKS;
        else
            actual = Category.OTHER;
        return actual == wanted;
    }

    private int countFor(Category c) {
        int count = 0;
        for (ShopOffer offer : bot.getOffers()) if (matchesCategory(offer, c)) count++;
        return count;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        try {
            if (selected != null || confirmCheckout) {
                if (keyCode == Keyboard.KEY_ESCAPE) {
                    selected = null;
                    confirmCheckout = false;
                }
                return;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                mc.displayGuiScreen(null);
                return;
            }
            if (search != null && search.textboxKeyTyped(typedChar, keyCode)) {
                productScroll = 0;
                rebuildFilter();
            }
        } catch (Exception ignored) {}
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        try {
            if (mouseButton != 0) return;
            if (selected == null && !confirmCheckout && search != null)
                search.mouseClicked(mouseX, mouseY, mouseButton);

            for (int i = hits.size() - 1; i >= 0; i--) {
                HitBox hit = hits.get(i);
                if (hit.contains(mouseX, mouseY)) {
                    hit.action.run();
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void handleMouseInput() {
        try {
            super.handleMouseInput();
            int wheel = Mouse.getEventDWheel();
            if (wheel == 0 || selected != null || confirmCheckout) return;
            int mouseX = Mouse.getEventX() * width / mc.displayWidth;
            int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
            int direction = wheel > 0 ? -1 : 1;
            if (mouseX >= guiRight - CART_W && mouseX < guiRight && mouseY >= guiTop && mouseY < guiBottom) cartScroll += direction;
            else if (mouseX > guiLeft + SIDEBAR_W && mouseX < guiRight - CART_W && mouseY >= guiTop && mouseY < guiBottom) productScroll += direction;
        } catch (Exception ignored) {}
    }

    private void button(int x, int y, int w, int h, String label, int mouseX, int mouseY, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        drawRect(x, y, x + w, y + h, hover ? 0xFFE04458 : 0xFFB72C3F);
        drawCenteredString(fontRendererObj, "§f" + label, x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
        hits.add(new HitBox(x, y, w, h, action));
    }

    private void smallButton(int x, int y, String label, int mouseX, int mouseY, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, 20, 24);
        drawRect(x, y, x + 20, y + 24, hover ? 0xFF5A2630 : 0xFF32171D);
        drawCenteredString(fontRendererObj, "§f" + label, x + 10, y + 8, 0xFFFFFF);
        hits.add(new HitBox(x, y, 20, 24, action));
    }

    private void renderItem(ItemStack stack, int x, int y, float scale) {
        if (stack == null) return;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableDepth();
            GlStateManager.enableRescaleNormal();
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.translate(x, y, 0);
            GlStateManager.scale(scale, scale, scale);
            mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
            mc.getRenderItem().renderItemOverlayIntoGUI(fontRendererObj, stack, 0, 0, null);
        } finally {
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix();
            GlStateManager.disableLighting();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
        }
    }

    private void toast(String message) {
        toast = message;
        toastUntil = System.currentTimeMillis() + 2500L;
    }

    private static String formatPrice(BigDecimal price) {
        return PRICE.format(price) + "$";
    }

    private static String stockText(ShopOffer offer) {
        int max = offer.getMaximumPurchases();
        if (max == Integer.MAX_VALUE) return "§a∞ verfügbar";
        if (max <= 0) return "§cAusverkauft";
        if (max <= 7) return "§eNur " + max + " verfügbar";
        return "§a" + max + " verfügbar";
    }

    private String trimToWidth(String text, int maxWidth) {
        if (fontRendererObj.getStringWidth(text) <= maxWidth) return text;
        return fontRendererObj.trimStringToWidth(text, Math.max(0, maxWidth - fontRendererObj.getStringWidth("..."))) + "...";
    }

    private String trim(String text, int maxWidth) {
        return trimToWidth(clean(text), maxWidth);
    }

    private static String clean(String text) {
        if (text == null) return "";
        String clean = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        return clean == null ? text : clean;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Category {
        ALL("Alle"),
        BLOCKS("Blöcke"),
        DECO("Deko"),
        REDSTONE("Redstone"),
        TOOLS("Tools & Rüstung"),
        FOOD("Essen"),
        OTHER("Sonstiges");

        final String label;
        Category(String label) { this.label = label; }
    }

    private static final class HitBox {
        final int x, y, w, h;
        final Runnable action;
        HitBox(int x, int y, int w, int h, Runnable action) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.action = action;
        }
        boolean contains(int mx, int my) { return inside(mx, my, x, y, w, h); }
    }
}
