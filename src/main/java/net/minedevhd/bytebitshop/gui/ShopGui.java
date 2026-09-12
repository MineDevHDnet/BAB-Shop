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

    private static final int TOPBAR_H = 62;
    private static final int CATEGORY_H = 40;
    private static final int CARD_H = 84;
    private static final int GUI_MAX_W = 900;
    private static final int GUI_MAX_H = 500;
    private static final int GUI_MARGIN = 10;
    private static final int PAD = 14;

    private static final int BG_TOP = 0xFF090B0E;
    private static final int BG_BOTTOM = 0xFF12151A;
    private static final int SURFACE = 0xFF14171C;
    private static final int SURFACE_2 = 0xFF1A1E24;
    private static final int SURFACE_3 = 0xFF20252C;
    private static final int BORDER = 0xFF2C323A;
    private static final int BORDER_SOFT = 0xFF23282F;
    private static final int TEXT = 0xFFF3F5F7;
    private static final int MUTED = 0xFF8D959F;
    private static final int ACCENT = 0xFFD9475B;
    private static final int ACCENT_HOVER = 0xFFE65A6D;
    private static final int ACCENT_DARK = 0xFF5A202A;
    private static final int SUCCESS = 0xFF58B67A;
    private static final int WARNING = 0xFFE7B85C;
    private static final int DANGER = 0xFFE06666;

    private final ByteBitShopCore core;
    private final ShopBot bot;
    private final ShoppingCart cart = new ShoppingCart();
    private final List<ShopOffer> filtered = new ArrayList<ShopOffer>();
    private final List<HitBox> hits = new ArrayList<HitBox>();

    private GuiTextField search;
    private int searchX;
    private int searchY;
    private int searchWidth;
    private int guiLeft;
    private int guiTop;
    private int guiRight;
    private int guiBottom;
    private int guiWidth;
    private int guiHeight;
    private int cartWidth;

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
        cartWidth = guiWidth < 720 ? 198 : 232;

        int storeRight = guiRight - cartWidth;
        int brandWidth = Math.max(148, Math.min(210, guiWidth / 4));
        int refreshWidth = guiWidth < 720 ? 78 : 94;

        searchX = guiLeft + brandWidth;
        searchY = guiTop + 21;
        searchWidth = Math.max(108, storeRight - PAD - refreshWidth - 10 - searchX);
        search = new GuiTextField(1, fontRendererObj, searchX, searchY, searchWidth, 20);
        search.setMaxStringLength(80);
        search.setEnableBackgroundDrawing(true);
        search.setTextColor(0xFFF2F5F7);

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

        drawGradientRect(0, 0, width, height, BG_TOP, BG_BOTTOM);
        drawRect(guiLeft - 4, guiTop + 3, guiRight + 4, guiBottom + 5, 0x50000000);
        drawRect(guiLeft - 1, guiTop - 1, guiRight + 1, guiBottom + 1, BORDER);
        drawRect(guiLeft, guiTop, guiRight, guiBottom, SURFACE);

        int cartLeft = guiRight - cartWidth;
        drawRect(guiLeft, guiTop, guiRight, guiTop + TOPBAR_H, 0xFF111419);
        drawRect(guiLeft, guiTop + TOPBAR_H - 1, guiRight, guiTop + TOPBAR_H, BORDER);
        drawRect(cartLeft, guiTop + TOPBAR_H, guiRight, guiBottom, 0xFF111419);
        drawRect(cartLeft - 1, guiTop + TOPBAR_H, cartLeft, guiBottom, BORDER);

        drawHeader(mouseX, mouseY);
        drawCategoryBar(mouseX, mouseY);
        drawProducts(mouseX, mouseY);
        drawCart(mouseX, mouseY);

        if (selected != null) {
            drawProductModal(mouseX, mouseY);
        } else if (confirmCheckout) {
            drawCheckoutModal(mouseX, mouseY);
        }

        if (toast != null && System.currentTimeMillis() < toastUntil) {
            int toastW = Math.min(guiWidth - 40, fontRendererObj.getStringWidth(toast) + 34);
            int toastX = guiLeft + (guiWidth - toastW) / 2;
            int toastY = guiBottom - 36;
            panel(toastX, toastY, toastW, 24, 0xF022262C, ACCENT_DARK);
            drawCenteredString(fontRendererObj, "§f" + trimToWidth(toast, toastW - 18), toastX + toastW / 2, toastY + 8, TEXT);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawHeader(int mouseX, int mouseY) {
        int cartLeft = guiRight - cartWidth;

        drawRect(guiLeft + 14, guiTop + 13, guiLeft + 18, guiTop + 47, ACCENT);
        fontRendererObj.drawString("§f§lBYTE & BIT", guiLeft + 27, guiTop + 15, TEXT);
        fontRendererObj.drawString("§7MARKETPLACE", guiLeft + 27, guiTop + 29, MUTED);
        fontRendererObj.drawString("§8" + trim(bot.getName(), Math.max(70, searchX - guiLeft - 42)), guiLeft + 27, guiTop + 42, MUTED);

        drawRect(searchX - 2, searchY - 2, searchX + searchWidth + 2, searchY + 22, BORDER_SOFT);
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) {
            fontRendererObj.drawString("§8Suche nach Item, Preis oder Inhalt ...", searchX + 5, searchY + 6, MUTED);
        }

        final int refreshW = guiWidth < 720 ? 78 : 94;
        int refreshX = cartLeft - PAD - refreshW;
        secondaryButton(refreshX, guiTop + 19, refreshW, 26, guiWidth < 720 ? "Refresh" : "Aktualisieren", mouseX, mouseY, new Runnable() {
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

        int cartBadgeX = cartLeft + PAD;
        int badgeY = guiTop + 16;
        int badgeW = cartWidth - PAD * 2;
        drawRect(cartBadgeX, badgeY, cartBadgeX + badgeW, badgeY + 30, SURFACE_2);
        drawRect(cartBadgeX, badgeY, cartBadgeX + 3, badgeY + 30, ACCENT);
        fontRendererObj.drawString("§f§lWARENKORB", cartBadgeX + 11, badgeY + 6, TEXT);
        String itemText = cart.transactionCount() + " Pos.";
        fontRendererObj.drawString("§8" + itemText, cartBadgeX + badgeW - 8 - fontRendererObj.getStringWidth(itemText), badgeY + 7, MUTED);
        fontRendererObj.drawString("§7" + trimToWidth(formatPrice(cart.total()), badgeW - 20), cartBadgeX + 11, badgeY + 18, MUTED);
    }

    private void drawCategoryBar(int mouseX, int mouseY) {
        int left = guiLeft + PAD;
        int right = guiRight - cartWidth - PAD;
        int top = guiTop + TOPBAR_H + 10;
        int available = Math.max(1, right - left);
        int gap = 5;
        int count = Category.values().length;
        int tabW = Math.max(42, (available - gap * (count - 1)) / count);

        for (int i = 0; i < count; i++) {
            final Category c = Category.values()[i];
            int x = left + i * (tabW + gap);
            int w = i == count - 1 ? Math.max(42, right - x) : tabW;
            boolean active = category == c;
            boolean hover = inside(mouseX, mouseY, x, top, w, 28);

            int fill = active ? ACCENT_DARK : (hover ? SURFACE_3 : SURFACE_2);
            int border = active ? ACCENT : BORDER_SOFT;
            panel(x, top, w, 28, fill, border);

            String label = c.label;
            int categoryCount = countFor(c);
            String countText = Integer.toString(categoryCount);
            int countW = fontRendererObj.getStringWidth(countText);
            int labelSpace = w - 18 - countW;
            String shown = trimToWidth(label, Math.max(18, labelSpace));
            fontRendererObj.drawString(active ? "§f" + shown : "§7" + shown, x + 8, top + 10, active ? TEXT : MUTED);
            fontRendererObj.drawString(active ? "§f" + countText : "§8" + countText, x + w - 8 - countW, top + 10, active ? TEXT : MUTED);

            final int fx = x;
            final int fw = w;
            hits.add(new HitBox(fx, top, fw, 28, new Runnable() {
                @Override public void run() {
                    category = c;
                    productScroll = 0;
                    rebuildFilter();
                }
            }));
        }
    }

    private void drawProducts(int mouseX, int mouseY) {
        int left = guiLeft + PAD;
        int right = guiRight - cartWidth - PAD;
        int top = guiTop + TOPBAR_H + CATEGORY_H + 8;
        int bottom = guiBottom - PAD;

        fontRendererObj.drawString("§f§lAngebote", left, top, TEXT);
        String meta = filtered.size() + " von " + bot.getOffers().size();
        fontRendererObj.drawString("§8" + meta, right - fontRendererObj.getStringWidth(meta), top, MUTED);
        top += 19;

        int availableWidth = Math.max(120, right - left);
        int columns = availableWidth >= 430 ? 2 : 1;
        int gap = 8;
        int cardW = (availableWidth - gap * (columns - 1)) / columns;
        int rowsVisible = Math.max(1, (bottom - top) / (CARD_H + gap));
        int totalRows = (filtered.size() + columns - 1) / columns;
        int maxScroll = Math.max(0, totalRows - rowsVisible);
        productScroll = clamp(productScroll, 0, maxScroll);

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
            int fill = hover ? 0xFF22272E : SURFACE_2;
            int border = hover ? 0xFF3C444E : BORDER_SOFT;

            panel(x, y, cardW, CARD_H, fill, border);
            drawRect(x, y, x + 4, y + CARD_H, available ? ACCENT : 0xFF555B63);

            ItemStack stack = offer.getDisplayStack();
            if (stack != null) {
                drawRect(x + 13, y + 15, x + 49, y + 51, 0xFF111419);
                renderItem(stack, x + 15, y + 17, 1.95F);
                if (hover) hoverStack = stack;
            }

            int textX = x + 61;
            int contentRight = x + cardW - 10;
            String name = clean(offer.getDisplayName());
            fontRendererObj.drawString("§f§l" + trimToWidth(name, Math.max(40, contentRight - textX)), textX, y + 13, TEXT);

            String stock = stockText(offer);
            fontRendererObj.drawString(stock, textX, y + 31, TEXT);

            String price = formatPrice(offer.getPrice());
            fontRendererObj.drawString("§f§l" + price, textX, y + 54, TEXT);

            String details = "Details >";
            fontRendererObj.drawString(hover ? "§f" + details : "§8" + details,
                    contentRight - fontRendererObj.getStringWidth(details), y + 55, hover ? TEXT : MUTED);

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
            int emptyY = top + 38;
            drawRect(left, emptyY, right, emptyY + 72, SURFACE_2);
            drawCenteredString(fontRendererObj, "§fKeine passenden Angebote", (left + right) / 2, emptyY + 22, TEXT);
            drawCenteredString(fontRendererObj, "§8Versuche eine andere Suche oder Kategorie.", (left + right) / 2, emptyY + 40, MUTED);
        }

        if (maxScroll > 0) {
            int barX = right - 3;
            int barTop = top;
            int barH = Math.max(44, bottom - top);
            drawRect(barX, barTop, barX + 2, barTop + barH, BORDER_SOFT);
            int thumbH = Math.max(22, barH * rowsVisible / Math.max(rowsVisible, totalRows));
            int thumbY = barTop + (barH - thumbH) * productScroll / maxScroll;
            drawRect(barX, thumbY, barX + 2, thumbY + thumbH, ACCENT);
        }

        if (hoverStack != null && selected == null && !confirmCheckout && !Mouse.isButtonDown(0)) {
            renderToolTip(hoverStack, mouseX, mouseY);
        }
    }

    private void drawCart(int mouseX, int mouseY) {
        int x = guiRight - cartWidth;
        int innerX = x + PAD;
        int innerRight = guiRight - PAD;
        int y = guiTop + TOPBAR_H + 13;

        fontRendererObj.drawString("§f§lDeine Auswahl", innerX, y, TEXT);
        String purchases = cart.transactionCount() + " Kauf" + (cart.transactionCount() == 1 ? "" : "vorgänge");
        fontRendererObj.drawString("§8" + purchases, innerRight - fontRendererObj.getStringWidth(purchases), y, MUTED);
        y += 20;

        List<ShoppingCart.Line> lines = cart.lines();
        int footerH = 112;
        int listBottom = guiBottom - footerH - 6;
        int rowH = 54;
        int visibleRows = Math.max(1, (listBottom - y) / rowH);
        int maxCartScroll = Math.max(0, lines.size() - visibleRows);
        cartScroll = clamp(cartScroll, 0, maxCartScroll);

        if (lines.isEmpty()) {
            int emptyH = 76;
            panel(innerX, y, innerRight - innerX, emptyH, SURFACE_2, BORDER_SOFT);
            drawCenteredString(fontRendererObj, "§7Warenkorb ist leer", x + cartWidth / 2, y + 22, MUTED);
            drawCenteredString(fontRendererObj, "§8Wähle links ein Angebot aus.", x + cartWidth / 2, y + 41, MUTED);
        }

        for (int i = cartScroll; i < lines.size() && i < cartScroll + visibleRows; i++) {
            final ShoppingCart.Line line = lines.get(i);
            int rowY = y + (i - cartScroll) * rowH;

            panel(innerX, rowY, innerRight - innerX, 47, SURFACE_2, BORDER_SOFT);
            ItemStack stack = line.offer.getDisplayStack();
            if (stack != null) renderItem(stack, innerX + 7, rowY + 15, 1.0F);

            final int minusX = innerRight - 47;
            final int plusX = innerRight - 23;
            int textX = innerX + 29;
            int textWidth = Math.max(34, minusX - textX - 6);

            String name = clean(line.offer.getDisplayName());
            fontRendererObj.drawString("§f" + trimToWidth(name, textWidth), textX, rowY + 8, TEXT);
            String linePrice = line.quantity + " x " + formatPrice(line.offer.getPrice());
            fontRendererObj.drawString("§8" + trimToWidth(linePrice, textWidth), textX, rowY + 25, MUTED);

            smallButton(minusX, rowY + 12, "-", mouseX, mouseY, new Runnable() {
                @Override public void run() { cart.add(line.offer, -1); }
            });
            smallButton(plusX, rowY + 12, "+", mouseX, mouseY, new Runnable() {
                @Override public void run() {
                    if (!cart.add(line.offer, 1)) toast("Nicht genug Lagerbestand");
                }
            });
        }

        int footerY = guiBottom - footerH;
        drawRect(x, footerY, guiRight, footerY + 1, BORDER);
        fontRendererObj.drawString("§8GESAMT", innerX, footerY + 14, MUTED);

        String total = formatPrice(cart.total());
        fontRendererObj.drawString("§f§l" + total, innerRight - fontRendererObj.getStringWidth(total), footerY + 13, TEXT);
        fontRendererObj.drawString("§8" + cart.transactionCount() + " Zahlung(en) · 2,5 s Abstand", innerX, footerY + 32, MUTED);

        boolean canCheckout = !cart.isEmpty() && cart.transactionCount() <= 128 && !core.getPaymentQueue().isBusy();
        final int buttonY = footerY + 54;

        if (canCheckout) {
            primaryButton(innerX, buttonY, innerRight - innerX, 32, "Zur Kasse", mouseX, mouseY, new Runnable() {
                @Override public void run() { confirmCheckout = true; }
            });
        } else {
            drawRect(innerX, buttonY, innerRight, buttonY + 32, 0xFF1C2026);
            drawRect(innerX, buttonY, innerX + 3, buttonY + 32, 0xFF4A5058);
            String text = core.getPaymentQueue().isBusy()
                    ? "Checkout läuft ..."
                    : (cart.transactionCount() > 128 ? "Max. 128 Zahlungen" : "Noch nichts ausgewählt");
            drawCenteredString(fontRendererObj, "§8" + text, x + cartWidth / 2, buttonY + 12, MUTED);
        }

        String hint = "ESC schließen · /babshop cancel";
        drawCenteredString(fontRendererObj, "§8" + trimToWidth(hint, cartWidth - PAD * 2), x + cartWidth / 2, guiBottom - 17, MUTED);
    }

    private void drawProductModal(int mouseX, int mouseY) {
        hits.add(new HitBox(0, 0, width, height, new Runnable() { @Override public void run() {} }));
        drawRect(0, 0, width, height, 0xB5000000);

        int w = Math.min(430, guiWidth - 34);
        int h = Math.min(270, guiHeight - 34);
        int x = guiLeft + (guiWidth - w) / 2;
        int y = guiTop + (guiHeight - h) / 2;

        panel(x, y, w, h, SURFACE, BORDER);
        drawRect(x, y, x + w, y + 4, ACCENT);

        ItemStack stack = selected.getDisplayStack();
        int previewX = x + 22;
        int previewY = y + 24;
        panel(previewX, previewY, 74, 74, 0xFF101318, BORDER_SOFT);
        if (stack != null) renderItem(stack, previewX + 20, previewY + 20, 2.15F);

        int infoX = x + 114;
        int infoRight = x + w - 22;
        fontRendererObj.drawString("§f§l" + trimToWidth(clean(selected.getDisplayName()), infoRight - infoX), infoX, y + 27, TEXT);
        fontRendererObj.drawString("§8Angebot von §f" + trimToWidth(bot.getName(), infoRight - infoX - 68), infoX, y + 45, MUTED);

        String price = formatPrice(selected.getPrice());
        fontRendererObj.drawString("§f§l" + price, infoX, y + 68, TEXT);
        fontRendererObj.drawString("§8pro Zahlung", infoX + fontRendererObj.getStringWidth(price) + 7, y + 69, MUTED);
        fontRendererObj.drawString(stockText(selected), infoX, y + 86, TEXT);

        int componentTop = y + 115;
        int componentH = Math.max(58, h - 190);
        panel(x + 22, componentTop, w - 44, componentH, SURFACE_2, BORDER_SOFT);
        fontRendererObj.drawString("§8DU ERHÄLTST PRO ZAHLUNG", x + 34, componentTop + 11, MUTED);

        int componentY = componentTop + 29;
        int shown = 0;
        for (ShopComponent component : selected.getComponents()) {
            if (shown >= 4) break;
            ItemStack componentStack = component.getStack();
            String line = "§f" + componentStack.stackSize + "x §7" + clean(componentStack.getDisplayName());
            fontRendererObj.drawString(trimToWidth(line, w - 70), x + 34, componentY, TEXT);
            componentY += 13;
            shown++;
        }

        if (selected.getComponents().size() > shown) {
            fontRendererObj.drawString("§8+ " + (selected.getComponents().size() - shown) + " weitere Komponente(n)", x + 34, componentY, MUTED);
        }

        int controlsY = y + h - 51;
        fontRendererObj.drawString("§8MENGE", x + 22, controlsY + 10, MUTED);

        smallButton(x + 74, controlsY + 1, "-", mouseX, mouseY, new Runnable() {
            @Override public void run() { selectedQty = Math.max(1, selectedQty - 1); }
        });

        panel(x + 99, controlsY + 1, 44, 24, 0xFF101318, BORDER_SOFT);
        drawCenteredString(fontRendererObj, "§f" + selectedQty, x + 121, controlsY + 9, TEXT);

        smallButton(x + 148, controlsY + 1, "+", mouseX, mouseY, new Runnable() {
            @Override public void run() { selectedQty = Math.min(64, selectedQty + 1); }
        });

        int cancelW = 78;
        int addW = 108;
        secondaryButton(x + w - 22 - addW - 8 - cancelW, controlsY - 1, cancelW, 28, "Zurück", mouseX, mouseY, new Runnable() {
            @Override public void run() { selected = null; }
        });

        primaryButton(x + w - 22 - addW, controlsY - 1, addW, 28, "Hinzufügen", mouseX, mouseY, new Runnable() {
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
        drawRect(0, 0, width, height, 0xB5000000);

        int w = Math.min(390, guiWidth - 34);
        int h = Math.min(218, guiHeight - 30);
        int x = guiLeft + (guiWidth - w) / 2;
        int y = guiTop + (guiHeight - h) / 2;

        panel(x, y, w, h, SURFACE, BORDER);
        drawRect(x, y, x + w, y + 4, ACCENT);

        drawCenteredString(fontRendererObj, "§f§lCheckout bestätigen", width / 2, y + 24, TEXT);
        drawCenteredString(fontRendererObj, "§8Prüfe deinen Einkauf vor dem Start.", width / 2, y + 42, MUTED);

        int summaryX = x + 28;
        int summaryY = y + 67;
        int summaryW = w - 56;
        panel(summaryX, summaryY, summaryW, 72, SURFACE_2, BORDER_SOFT);

        fontRendererObj.drawString("§8SHOP", summaryX + 12, summaryY + 11, MUTED);
        String botName = trimToWidth(bot.getName(), summaryW - 96);
        fontRendererObj.drawString("§f" + botName, summaryX + summaryW - 12 - fontRendererObj.getStringWidth(botName), summaryY + 11, TEXT);

        fontRendererObj.drawString("§8ZAHLUNGEN", summaryX + 12, summaryY + 29, MUTED);
        String transactions = Integer.toString(cart.transactionCount());
        fontRendererObj.drawString("§f" + transactions, summaryX + summaryW - 12 - fontRendererObj.getStringWidth(transactions), summaryY + 29, TEXT);

        fontRendererObj.drawString("§8SUMME", summaryX + 12, summaryY + 47, MUTED);
        String total = formatPrice(cart.total());
        fontRendererObj.drawString("§f§l" + total, summaryX + summaryW - 12 - fontRendererObj.getStringWidth(total), summaryY + 47, TEXT);

        drawCenteredString(fontRendererObj, "§8Jede /pay-Zahlung wird mit 2,5 Sekunden Abstand gesendet.", width / 2, y + 151, MUTED);

        int modalButtonY = y + h - 40;
        secondaryButton(x + 28, modalButtonY, 112, 28, "Abbrechen", mouseX, mouseY, new Runnable() {
            @Override public void run() { confirmCheckout = false; }
        });

        primaryButton(x + w - 140, modalButtonY, 112, 28, "Jetzt kaufen", mouseX, mouseY, new Runnable() {
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
                for (String line : lore) {
                    if (clean(line).toLowerCase(Locale.ROOT).contains(query)) return true;
                }
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
        if (containsAny(key, "redstone", "repeater", "comparator", "hopper", "piston", "lever", "button", "pressure", "observer", "dispenser", "dropper")) {
            actual = Category.REDSTONE;
        } else if (containsAny(key, "sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots", "shears", "bow", "fishing")) {
            actual = Category.TOOLS;
        } else if (containsAny(key, "apple", "bread", "potato", "carrot", "beef", "pork", "chicken", "fish", "melon", "cookie", "cake", "food")) {
            actual = Category.FOOD;
        } else if (containsAny(key, "flower", "sapling", "leaves", "wool", "glass", "carpet", "banner", "skull", "painting", "frame", "torch", "fence")) {
            actual = Category.DECO;
        } else if (key.startsWith("tile.") || containsAny(key, "stone", "dirt", "sand", "brick", "planks", "log", "ore", "block")) {
            actual = Category.BLOCKS;
        } else {
            actual = Category.OTHER;
        }

        return actual == wanted;
    }

    private int countFor(Category c) {
        int count = 0;
        for (ShopOffer offer : bot.getOffers()) {
            if (matchesCategory(offer, c)) count++;
        }
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

            if (selected == null && !confirmCheckout && search != null) {
                search.mouseClicked(mouseX, mouseY, mouseButton);
            }

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
            int cartLeft = guiRight - cartWidth;

            if (mouseX >= cartLeft && mouseX < guiRight && mouseY >= guiTop && mouseY < guiBottom) {
                cartScroll += direction;
            } else if (mouseX > guiLeft && mouseX < cartLeft && mouseY >= guiTop && mouseY < guiBottom) {
                productScroll += direction;
            }
        } catch (Exception ignored) {}
    }

    private void primaryButton(int x, int y, int w, int h, String label, int mouseX, int mouseY, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int fill = hover ? ACCENT_HOVER : ACCENT;
        drawRect(x, y, x + w, y + h, fill);
        drawRect(x, y, x + 3, y + h, hover ? 0xFFFF8795 : 0xFFB92F43);
        drawCenteredString(fontRendererObj, "§f§l" + label, x + w / 2, y + (h - 8) / 2, TEXT);
        hits.add(new HitBox(x, y, w, h, action));
    }

    private void secondaryButton(int x, int y, int w, int h, String label, int mouseX, int mouseY, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        panel(x, y, w, h, hover ? SURFACE_3 : SURFACE_2, hover ? 0xFF444C56 : BORDER);
        drawCenteredString(fontRendererObj, hover ? "§f" + label : "§7" + label, x + w / 2, y + (h - 8) / 2, hover ? TEXT : MUTED);
        hits.add(new HitBox(x, y, w, h, action));
    }

    private void smallButton(int x, int y, String label, int mouseX, int mouseY, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, 20, 24);
        panel(x, y, 20, 24, hover ? ACCENT_DARK : SURFACE_3, hover ? ACCENT : BORDER);
        drawCenteredString(fontRendererObj, "§f" + label, x + 10, y + 8, TEXT);
        hits.add(new HitBox(x, y, 20, 24, action));
    }

    private void panel(int x, int y, int w, int h, int fill, int border) {
        drawRect(x, y, x + w, y + h, border);
        if (w > 2 && h > 2) {
            drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        }
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
        if (max == Integer.MAX_VALUE) return "§aUnbegrenzt verfügbar";
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
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
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
        TOOLS("Tools"),
        FOOD("Essen"),
        OTHER("Sonstiges");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private static final class HitBox {
        final int x;
        final int y;
        final int w;
        final int h;
        final Runnable action;

        HitBox(int x, int y, int w, int h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        boolean contains(int mx, int my) {
            return inside(mx, my, x, y, w, h);
        }
    }
}
