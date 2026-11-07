package net.minedevhd.bytebitshop.gui;

import java.io.IOException;
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

/** One consistent, responsive in-game window. No blocking overlay dialogs or texture packs. */
public final class ShopGui extends GuiScreen {
    private static final DecimalFormat PRICE = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMAN));
    private static final int MAX_WIDTH = 720;
    private static final int MAX_HEIGHT = 500;
    private static final int PAD = 16;
    private static final int BACKGROUND = 0xFF0D1824;
    private static final int SURFACE = 0xFF172938;
    private static final int CARD = 0xFF203547;
    private static final int HOVER = 0xFF2B4556;
    private static final int BORDER = 0xFF30485A;
    private static final int SOFT_BORDER = 0xFF253A4A;
    private static final int WHITE = 0xFFF5FAFC;
    private static final int MUTED = 0xFFACBECC;
    private static final int ACCENT = 0xFF54D6BD;
    private static final int ACCENT_HOVER = 0xFF79E7D3;
    private static final int ACCENT_TEXT = 0xFF09232D;

    private enum View { SHOP, CART, DETAIL, CONFIRM }

    private final ByteBitShopCore core;
    private final ShopBot bot;
    private final ShoppingCart cart = new ShoppingCart();
    private final List<ShopOffer> filtered = new ArrayList<ShopOffer>();
    private final List<HitBox> hits = new ArrayList<HitBox>();
    private View view = View.SHOP;
    private Category category = Category.ALL;
    private GuiTextField search;
    private ShopOffer selected;
    private int selectedQuantity = 1;
    private int shopScroll;
    private int cartScroll;
    private int componentScroll;
    private int left, top, right, bottom, panelWidth, panelHeight, headerHeight, footerHeight;
    private boolean compact;
    private boolean mini;
    private ItemStack hoveredStack;
    private long lastObservedSync;
    private String notice;
    private long noticeUntil;

    public ShopGui(ByteBitShopCore core, ShopBot bot) {
        this.core = core;
        this.bot = bot;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        String oldSearch = search == null ? "" : search.getText();
        panelWidth = Math.max(1, Math.min(MAX_WIDTH, width - 16));
        panelHeight = Math.max(1, Math.min(MAX_HEIGHT, height - 16));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        right = left + panelWidth;
        bottom = top + panelHeight;
        mini = panelHeight < 270;
        compact = panelHeight < 345 || panelWidth < 500;
        headerHeight = mini ? 43 : (compact ? 54 : 66);
        footerHeight = mini ? 43 : (compact ? 51 : 62);

        search = new GuiTextField(1, fontRendererObj, left + PAD + 44, top + headerHeight + (mini ? 9 : 13),
                Math.max(1, panelWidth - PAD * 2 - 53), 20);
        search.setMaxStringLength(80);
        search.setEnableBackgroundDrawing(false);
        search.setTextColor(WHITE);
        search.setText(oldSearch);
        lastObservedSync = bot.getLastSync();
        rebuildFilter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        if (search != null) search.updateCursorCounter();
        if (lastObservedSync != bot.getLastSync()) reconcileLatestOffers();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hits.clear();
        hoveredStack = null;
        drawGradientRect(0, 0, width, height, 0xFF07101A, 0xFF192D3B);
        drawRect(left - 5, top + 5, right + 5, bottom + 7, 0x77000000);
        panel(left, top, panelWidth, panelHeight, BACKGROUND, BORDER);

        drawHeader(mouseX, mouseY);
        switch (view) {
            case SHOP: drawShop(mouseX, mouseY); break;
            case CART: drawCart(mouseX, mouseY); break;
            case DETAIL: drawDetail(mouseX, mouseY); break;
            case CONFIRM: drawConfirm(mouseX, mouseY); break;
            default: break;
        }
        drawFooter(mouseX, mouseY);
        if (hoveredStack != null && !Mouse.isButtonDown(0) && view == View.SHOP)
            renderToolTip(hoveredStack, mouseX, mouseY);
        if (notice != null && System.currentTimeMillis() < noticeUntil) {
            int w = Math.min(panelWidth - 20, fontRendererObj.getStringWidth(notice) + 26);
            int x = left + (panelWidth - w) / 2;
            int y = bottom - footerHeight - 29;
            panel(x, y, w, 23, 0xFF235344, ACCENT);
            centered(trim(notice, w - 14), x, y + 8, WHITE, w);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawHeader(int mouseX, int mouseY) {
        drawGradientRect(left + 1, top + 1, right - 1, top + headerHeight, 0xFF1B3345, 0xFF142A3A);
        drawRect(left, top + headerHeight - 1, right, top + headerHeight, BORDER);
        int markerY = top + (mini ? 7 : 12);
        drawRect(left + PAD, markerY, left + PAD + 4, markerY + (mini ? 27 : 35), ACCENT);
        fontRendererObj.drawString("§f§lBYTE & BIT", left + PAD + 14, markerY + 1, WHITE);
        fontRendererObj.drawString("§7SHOP  /  §b" + trim(bot.getName(), Math.max(50, panelWidth - 260)),
                left + PAD + 14, markerY + 19, MUTED);

        if (view == View.SHOP) {
            button(right - PAD - (compact ? 102 : 136), top + (headerHeight - 30) / 2,
                    compact ? 102 : 136, 30,
                    "Warenkorb (" + cart.transactionCount() + ")", mouseX, mouseY, true, new Runnable() {
                        @Override public void run() { view = View.CART; cartScroll = 0; }
                    });
        } else {
            button(right - PAD - (compact ? 92 : 116), top + (headerHeight - 30) / 2,
                    compact ? 92 : 116, 30,
                    "< Zum Shop", mouseX, mouseY, false, new Runnable() {
                        @Override public void run() { view = View.SHOP; }
                    });
        }
    }

    private void drawShop(int mouseX, int mouseY) {
        int filterTop = top + headerHeight;
        int searchTop = filterTop + (mini ? 4 : 8);
        int searchH = mini ? 27 : 32;
        panel(left + PAD, searchTop, panelWidth - PAD * 2, searchH, SURFACE, SOFT_BORDER);
        fontRendererObj.drawString("§7SUCHE", left + PAD + 9, searchTop + (searchH - 8) / 2, MUTED);
        drawRect(left + PAD + 42, searchTop + 6, left + PAD + 43, searchTop + searchH - 6, BORDER);
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused())
            fontRendererObj.drawString("§7" + trim("Item oder Preis suchen ...", panelWidth - PAD * 2 - 64),
                    left + PAD + 49, searchTop + (searchH - 8) / 2, MUTED);

        int categoryY = searchTop + searchH + (mini ? 5 : 10);
        int categoryH = mini ? 23 : 28;
        int refreshW = mini ? 56 : (compact ? 78 : 94);
        if (compact) {
            int labelW = Math.max(56, Math.min(170, panelWidth - PAD * 2 - refreshW - 66));
            button(left + PAD, categoryY, 23, categoryH, "<", mouseX, mouseY, false, new Runnable() {
                @Override public void run() { switchCategory(-1); }
            });
            button(left + PAD + 27, categoryY, labelW, categoryH,
                    category.label + " (" + countFor(category) + ")", mouseX, mouseY, false, new Runnable() {
                        @Override public void run() { switchCategory(1); }
                    });
            button(left + PAD + 31 + labelW, categoryY, 23, categoryH, ">", mouseX, mouseY, false, new Runnable() {
                @Override public void run() { switchCategory(1); }
            });
        } else {
            int count = Category.values().length;
            int gap = 5;
            int stripW = panelWidth - PAD * 2 - refreshW - 13;
            int chipW = (stripW - gap * (count - 1)) / count;
            for (int i = 0; i < count; i++) {
                final Category option = Category.values()[i];
                int cx = left + PAD + i * (chipW + gap);
                int cw = i == count - 1 ? left + PAD + stripW - cx : chipW;
                boolean active = option == category;
                boolean hover = inside(mouseX, mouseY, cx, categoryY, cw, categoryH);
                panel(cx, categoryY, cw, categoryH, active ? 0xFF1A4A48 : (hover ? HOVER : SURFACE),
                        active ? ACCENT : SOFT_BORDER);
                if (active) drawRect(cx + 1, categoryY + categoryH - 3, cx + cw - 1, categoryY + categoryH - 1, ACCENT);
                centered(trim(option.label, cw - 8), cx, categoryY + (categoryH - 8) / 2,
                        active ? WHITE : MUTED, cw);
                hits.add(new HitBox(cx, categoryY, cw, categoryH, new Runnable() {
                    @Override public void run() { category = option; shopScroll = 0; rebuildFilter(); }
                }));
            }
        }
        button(right - PAD - refreshW, categoryY, refreshW, categoryH,
                mini ? "Neu" : "Aktualisieren", mouseX, mouseY, false, new Runnable() {
            @Override public void run() { refresh(); }
        });

        int listTop = categoryY + categoryH + (mini ? 7 : 11);
        int listBottom = bottom - footerHeight - 8;
        int rowH = mini ? 58 : (compact ? 68 : 77);
        int gap = mini ? 5 : 9;
        int step = rowH + gap;
        int visible = Math.max(0, (listBottom - listTop + gap) / step);
        int maxScroll = Math.max(0, filtered.size() - visible);
        shopScroll = clamp(shopScroll, 0, maxScroll);

        if (filtered.isEmpty()) {
            int emptyHeight = Math.max(35, Math.min(82, listBottom - listTop));
            panel(left + PAD, listTop, panelWidth - PAD * 2, emptyHeight, SURFACE, SOFT_BORDER);
            centered("§fKeine passenden Angebote", left, listTop + 11, WHITE, panelWidth);
            if (emptyHeight >= 55) centered("§7Suche oder Kategorie ändern", left, listTop + 31, MUTED, panelWidth);
            return;
        }

        for (int i = shopScroll; i < filtered.size() && i < shopScroll + visible; i++) {
            final ShopOffer offer = filtered.get(i);
            int x = left + PAD;
            int y = listTop + (i - shopScroll) * step;
            int w = panelWidth - PAD * 2;
            boolean hovered = inside(mouseX, mouseY, x, y, w, rowH);
            panel(x, y, w, rowH, hovered ? HOVER : SURFACE, hovered ? ACCENT : SOFT_BORDER);
            if (hovered) drawRect(x + 2, y + rowH - 3, x + w - 2, y + rowH - 1, ACCENT);

            ItemStack stack = offer.getDisplayStack();
            if (stack != null) {
                int iconY = y + (rowH - 43) / 2;
                panel(x + 11, iconY, 43, 43, CARD, SOFT_BORDER);
                renderItem(stack, x + 18, iconY + 8, 1.7F);
                if (inside(mouseX, mouseY, x + 11, y, 43, rowH)) hoveredStack = stack;
            }

            int infoX = x + 66;
            int priceStart = x + w - (mini ? 99 : 130);
            int labelWidth = Math.max(25, priceStart - infoX - 13);
            fontRendererObj.drawString("§f§l" + trim(clean(offer.getDisplayName()), labelWidth), infoX, y + 12, WHITE);
            fontRendererObj.drawString(stockText(offer), infoX, y + (mini ? 35 : 41), MUTED);
            if (!mini) fontRendererObj.drawString("§7Details ansehen >", infoX, y + (compact ? 55 : 59), MUTED);
            String price = trim(formatPrice(offer.getPrice()), mini ? 89 : 120);
            fontRendererObj.drawString("§f§l" + price, priceStart, y + 12, offer.isAvailable() ? WHITE : MUTED);

            hits.add(new HitBox(x, y, w, rowH, new Runnable() {
                @Override public void run() {
                    selected = offer;
                    selectedQuantity = Math.max(1, cart.getQuantity(offer));
                    componentScroll = 0;
                    view = View.DETAIL;
                }
            }));
            int addW = mini ? 88 : 116;
            button(x + w - addW - 9, y + rowH - 31, addW, 24,
                    offer.isAvailable() ? "+ Hinzufügen" : "Ausverkauft", mouseX, mouseY, false, offer.isAvailable(), new Runnable() {
                        @Override public void run() {
                            if (!offer.isAvailable() || !cart.add(offer, 1)) toast("Nicht genug Lagerbestand");
                            else toast("Zum Warenkorb hinzugefügt");
                        }
                    });
        }
        if (maxScroll > 0) scrollBar(right - 7, listTop, listBottom - listTop, visible, filtered.size(), shopScroll);
    }

    private void drawCart(int mouseX, int mouseY) {
        int titleY = top + headerHeight + 17;
        fontRendererObj.drawString("§f§lDEIN WARENKORB", left + PAD, titleY, WHITE);
        String count = cart.transactionCount() + " Zahlungen";
        fontRendererObj.drawString("§7" + count, right - PAD - fontRendererObj.getStringWidth(count), titleY, MUTED);

        List<ShoppingCart.Line> lines = cart.lines();
        int listTop = titleY + 22;
        int listBottom = bottom - footerHeight - 10;
        int step = compact ? 54 : 61;
        int rowH = step - 7;
        int visible = Math.max(0, (listBottom - listTop + 7) / step);
        cartScroll = clamp(cartScroll, 0, Math.max(0, lines.size() - visible));
        if (lines.isEmpty()) {
            int emptyH = Math.max(40, Math.min(95, listBottom - listTop));
            panel(left + PAD, listTop, panelWidth - PAD * 2, emptyH, SURFACE, SOFT_BORDER);
            centered("§fHier ist noch Platz", left, listTop + 13, WHITE, panelWidth);
            if (emptyH > 53) centered("§7Wähle im Shop deine Produkte aus.", left, listTop + 34, MUTED, panelWidth);
        }

        for (int i = cartScroll; i < lines.size() && i < cartScroll + visible; i++) {
            final ShoppingCart.Line line = lines.get(i);
            int x = left + PAD;
            int y = listTop + (i - cartScroll) * step;
            int w = panelWidth - PAD * 2;
            panel(x, y, w, rowH, inside(mouseX, mouseY, x, y, w, rowH) ? HOVER : SURFACE, SOFT_BORDER);
            ItemStack stack = line.offer.getDisplayStack();
            if (stack != null) renderItem(stack, x + 10, y + 15, 1.0F);
            int labelWidth = Math.max(24, w - 162);
            fontRendererObj.drawString("§f§l" + trim(clean(line.offer.getDisplayName()), labelWidth), x + 37, y + 8, WHITE);
            fontRendererObj.drawString("§7" + trim(formatPrice(line.offer.getPrice()) + " / Kauf", labelWidth), x + 37, y + 26, MUTED);
            int minusX = x + w - 111;
            button(minusX, y + 10, 28, 27, "-", mouseX, mouseY, false, new Runnable() {
                @Override public void run() { cart.add(line.offer, -1); }
            });
            panel(minusX + 30, y + 10, 49, 27, 0xFF101F2C, SOFT_BORDER);
            centered("§f" + line.quantity, minusX + 30, y + 19, WHITE, 49);
            button(minusX + 81, y + 10, 28, 27, "+", mouseX, mouseY, false, new Runnable() {
                @Override public void run() { if (!cart.add(line.offer, 1)) toast("Nicht genug Lagerbestand"); }
            });
        }
        if (visible < lines.size()) scrollBar(right - 7, listTop, listBottom - listTop, visible, lines.size(), cartScroll);
    }

    private void drawDetail(int mouseX, int mouseY) {
        if (selected == null) { view = View.SHOP; return; }
        int x = left + PAD;
        int y = top + headerHeight + 12;
        int w = panelWidth - PAD * 2;
        panel(x, y, w, compact ? 64 : 84, SURFACE, SOFT_BORDER);
        drawRect(x + 1, y + 1, x + 4, y + (compact ? 63 : 83), ACCENT);
        ItemStack stack = selected.getDisplayStack();
        if (stack != null) renderItem(stack, x + 17, y + (compact ? 14 : 22), compact ? 2.0F : 2.5F);
        int infoX = x + 70;
        fontRendererObj.drawString("§f§l" + trim(clean(selected.getDisplayName()), w - 86), infoX, y + (compact ? 8 : 14), WHITE);
        fontRendererObj.drawString("§l" + trim(formatPrice(selected.getPrice()), w - 130),
                infoX, y + (compact ? 25 : 36), ACCENT);
        fontRendererObj.drawString(stockText(selected), infoX, y + (compact ? 44 : 59), MUTED);

        int contentTop = y + (compact ? 72 : 96);
        fontRendererObj.drawString("§7DU ERHÄLTST PRO ZAHLUNG", x + 4, contentTop, MUTED);
        contentTop += 17;
        int contentBottom = bottom - footerHeight - 8;
        int visible = Math.max(0, (contentBottom - contentTop) / 23);
        List<ShopComponent> components = selected.getComponents();
        componentScroll = clamp(componentScroll, 0, Math.max(0, components.size() - visible));
        for (int i = componentScroll; i < components.size() && i < componentScroll + visible; i++) {
            ShopComponent component = components.get(i);
            int rowY = contentTop + (i - componentScroll) * 23;
            panel(x, rowY, w, 21, SURFACE, SOFT_BORDER);
            ItemStack item = component.getStack();
            if (item != null) {
                renderItem(item, x + 3, rowY + 2, 1.0F);
                fontRendererObj.drawString("§f" + item.stackSize + "x §7" + trim(clean(item.getDisplayName()), w - 43),
                        x + 25, rowY + 7, WHITE);
            }
        }
        if (visible < components.size()) scrollBar(right - 7, contentTop, contentBottom - contentTop,
                visible, components.size(), componentScroll);
    }

    private void drawConfirm(int mouseX, int mouseY) {
        int x = left + PAD;
        int y = top + headerHeight + (compact ? 8 : 20);
        int w = panelWidth - PAD * 2;
        fontRendererObj.drawString("§f§lKAUF BESTÄTIGEN", x, y, WHITE);
        fontRendererObj.drawString("§7Prüfe die Zahlung, bevor sie gestartet wird.", x, y + 19, MUTED);
        int infoY = y + (compact ? 35 : 50);
        panel(x, infoY, w, compact ? 79 : 110, SURFACE, SOFT_BORDER);
        drawRect(x + 1, infoY + 1, x + 4, infoY + (compact ? 78 : 109), ACCENT);
        summaryLine("Shop", bot.getName(), x + 12, infoY + 14, w - 24);
        summaryLine("Zahlungen", String.valueOf(cart.transactionCount()), x + 12, infoY + 37, w - 24);
        summaryLine("Gesamt", formatPrice(cart.total()), x + 12, infoY + 60, w - 24);
        if (!compact) fontRendererObj.drawString("§7Die Zahlungen werden mit 2,5 s Abstand gesendet.", x + 12, infoY + 87, MUTED);
    }

    private void drawFooter(int mouseX, int mouseY) {
        int y = bottom - footerHeight;
        drawRect(left, y, right, y + 1, BORDER);
        drawRect(left + 1, y + 1, right - 1, bottom - 1, 0xFF152938);
        int buttonY = y + (footerHeight - 30) / 2;
        int actionW = compact ? Math.min(112, Math.max(76, panelWidth / 3)) : 154;
        int actionX = right - PAD - actionW;

        if (view == View.SHOP) {
            String label = filtered.size() + " Angebote";
            fontRendererObj.drawString("§f§l" + label, left + PAD, y + (footerHeight - 8) / 2 - (mini ? 0 : 7), WHITE);
            if (!mini) fontRendererObj.drawString("§7" + cart.transactionCount() + " im Warenkorb",
                    left + PAD, y + (footerHeight - 8) / 2 + 9, MUTED);
            button(actionX, buttonY, actionW, 30, compact ? "Warenkorb" : "Warenkorb ansehen", mouseX, mouseY, true, new Runnable() {
                @Override public void run() { view = View.CART; cartScroll = 0; }
            });
        } else if (view == View.CART) {
            fontRendererObj.drawString("§7GESAMTSUMME", left + PAD, y + (mini ? 3 : 8), MUTED);
            fontRendererObj.drawString("§l" + trim(formatPrice(cart.total()), Math.max(30, actionX - left - PAD - 5)),
                    left + PAD, y + (mini ? 20 : (compact ? 25 : 33)), ACCENT);
            boolean enabled = !cart.isEmpty() && cart.transactionCount() <= 128 && !core.getPaymentQueue().isBusy();
            button(actionX, buttonY, actionW, 30, enabled ? "Zur Kasse" : "Nicht verfügbar", mouseX, mouseY, true, enabled,
                    new Runnable() { @Override public void run() { view = View.CONFIRM; } });
        } else if (view == View.DETAIL) {
            boolean narrow = panelWidth < 320;
            button(left + PAD, buttonY, narrow ? 41 : (compact ? 49 : 78), 30, "Zurück", mouseX, mouseY, false,
                    new Runnable() { @Override public void run() { view = View.SHOP; selected = null; } });
            int qtyX = left + PAD + (narrow ? 45 : (compact ? 55 : 89));
            int toggleW = narrow ? 20 : 24;
            int countW = narrow ? 27 : 34;
            button(qtyX, buttonY + 2, toggleW, 26, "-", mouseX, mouseY, false,
                    new Runnable() { @Override public void run() { selectedQuantity = Math.max(1, selectedQuantity - 1); } });
            panel(qtyX + toggleW + 2, buttonY + 2, countW, 26, CARD, SOFT_BORDER);
            centered("§f" + selectedQuantity, qtyX + toggleW + 2, buttonY + 11, WHITE, countW);
            button(qtyX + toggleW + countW + 4, buttonY + 2, toggleW, 26, "+", mouseX, mouseY, false,
                    new Runnable() { @Override public void run() { selectedQuantity = Math.min(64, selectedQuantity + 1); } });
            button(actionX, buttonY, actionW, 30, "Hinzufügen", mouseX, mouseY, true, new Runnable() {
                @Override public void run() {
                    if (!cart.setQuantity(selected, selectedQuantity)) {
                        toast("Nicht genug Lagerbestand");
                        return;
                    }
                    selected = null;
                    view = View.SHOP;
                    toast("Menge übernommen");
                }
            });
        } else {
            button(left + PAD, buttonY, compact ? 95 : 120, 30, "Zurück", mouseX, mouseY, false,
                    new Runnable() { @Override public void run() { view = View.CART; } });
            button(actionX, buttonY, actionW, 30, "Jetzt kaufen", mouseX, mouseY, true,
                    new Runnable() { @Override public void run() { checkout(); } });
        }
    }

    private void refresh() {
        toast("Shop wird aktualisiert ...");
        core.getRegistry().ensureSynced(bot, true, new Runnable() {
            @Override public void run() {
                if (bot.getLastError() != null) {
                    toast("Fehler: " + bot.getLastError());
                    return;
                }
                reconcileLatestOffers();
                shopScroll = cartScroll = 0;
                toast("Shop aktualisiert");
            }
        });
    }

    private void reconcileLatestOffers() {
        lastObservedSync = bot.getLastSync();
        List<ShopOffer> fresh = bot.getOffers();
        boolean reduced = cart.reconcileOffers(fresh);
        if (selected != null) {
            ShopOffer replacement = null;
            for (ShopOffer offer : fresh) {
                if (offer.getPriceCents() == selected.getPriceCents()) {
                    replacement = offer;
                    break;
                }
            }
            selected = replacement;
            if (selected == null && view == View.DETAIL) view = View.SHOP;
            selectedQuantity = selected == null ? 1 : Math.max(1, Math.min(selectedQuantity, 64));
        }
        rebuildFilter();
        if (reduced) toast("Bestand geändert: Warenkorb angepasst");
    }

    private void checkout() {
        if (mc.thePlayer == null || mc.theWorld == null) { toast("Keine Serververbindung"); return; }
        if (core.getConfig().requireBotZone && !bot.contains(mc.thePlayer.getPositionVector())) {
            toast("Du bist nicht mehr in der Bot-Zone"); return;
        }
        if (cart.isEmpty() || cart.transactionCount() > 128) { toast("Ungültige Anzahl an Zahlungen"); return; }
        if (core.getPaymentQueue().isBusy() || !core.getPaymentQueue().start(bot.getName(), cart)) {
            toast("Checkout konnte nicht gestartet werden"); return;
        }
        int transactions = cart.transactionCount();
        String total = formatPrice(cart.total());
        cart.consumeLocally();
        cart.clear();
        Chat.info("Checkout gestartet: §f" + transactions + " §7Zahlung(en), Summe §c" + total
                + "§7. Abbruch mit §f/babshop cancel");
        mc.displayGuiScreen(null);
    }

    private void switchCategory(int direction) {
        Category[] values = Category.values();
        category = values[(category.ordinal() + direction + values.length) % values.length];
        shopScroll = 0;
        rebuildFilter();
    }

    private void rebuildFilter() {
        filtered.clear();
        String query = search == null ? "" : clean(search.getText()).toLowerCase(Locale.ROOT).trim();
        for (ShopOffer offer : bot.getOffers()) {
            if (category != Category.ALL && actualCategory(offer) != category) continue;
            if (!query.isEmpty() && !matchesSearch(offer, query)) continue;
            filtered.add(offer);
        }
    }

    private boolean matchesSearch(ShopOffer offer, String query) {
        if (clean(offer.getDisplayName()).toLowerCase(Locale.ROOT).contains(query)) return true;
        if (offer.getPrice().toPlainString().contains(query.replace(',', '.'))) return true;
        for (ShopComponent component : offer.getComponents()) {
            ItemStack stack = component.getStack();
            if (clean(stack.getDisplayName()).toLowerCase(Locale.ROOT).contains(query)) return true;
            if (stack.getUnlocalizedName().toLowerCase(Locale.ROOT).contains(query)) return true;
            if (stack.hasTagCompound() && stack.getTagCompound().hasKey("display", 10)) {
                for (String line : stack.getTooltip(mc.thePlayer, false)) {
                    if (clean(line).toLowerCase(Locale.ROOT).contains(query)) return true;
                }
            }
        }
        return false;
    }

    private int countFor(Category c) {
        if (c == Category.ALL) return bot.getOffers().size();
        int count = 0;
        for (ShopOffer offer : bot.getOffers()) if (actualCategory(offer) == c) count++;
        return count;
    }

    private static Category actualCategory(ShopOffer offer) {
        ItemStack stack = offer.getDisplayStack();
        if (stack == null) return Category.OTHER;
        String key = (stack.getUnlocalizedName() + " " + clean(stack.getDisplayName())).toLowerCase(Locale.ROOT);
        if (contains(key, "redstone", "repeater", "comparator", "hopper", "piston", "lever", "button", "pressure", "dispenser", "dropper")) return Category.REDSTONE;
        if (contains(key, "sword", "pickaxe", "axe", "shovel", "hoe", "helmet", "chestplate", "leggings", "boots", "shears", "bow", "fishing")) return Category.TOOLS;
        if (contains(key, "apple", "bread", "potato", "carrot", "beef", "pork", "chicken", "fish", "melon", "cookie", "cake")) return Category.FOOD;
        if (contains(key, "flower", "sapling", "leaves", "wool", "glass", "carpet", "banner", "skull", "painting", "frame", "torch", "fence")) return Category.DECO;
        if (key.startsWith("tile.") || contains(key, "stone", "dirt", "sand", "brick", "planks", "log", "ore", "block")) return Category.BLOCKS;
        return Category.OTHER;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (view == View.CONFIRM) view = View.CART;
            else if (view == View.DETAIL) { selected = null; view = View.SHOP; }
            else mc.displayGuiScreen(null);
            return;
        }
        if (view == View.SHOP && search != null && search.textboxKeyTyped(typedChar, keyCode)) {
            shopScroll = 0;
            rebuildFilter();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        if (view == View.SHOP && search != null) search.mouseClicked(mouseX, mouseY, button);
        for (int i = hits.size() - 1; i >= 0; i--) {
            HitBox hit = hits.get(i);
            if (hit.contains(mouseX, mouseY)) { hit.action.run(); return; }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int direction = wheel > 0 ? -1 : 1;
        if (view == View.SHOP) shopScroll += direction;
        else if (view == View.CART) cartScroll += direction;
        else if (view == View.DETAIL) componentScroll += direction;
    }

    private void button(int x, int y, int w, int h, String label, int mouseX, int mouseY, boolean primary, Runnable action) {
        button(x, y, w, h, label, mouseX, mouseY, primary, true, action);
    }

    private void button(int x, int y, int w, int h, String label, int mouseX, int mouseY,
                        boolean primary, boolean enabled, Runnable action) {
        boolean hovered = enabled && inside(mouseX, mouseY, x, y, w, h);
        int fill = !enabled ? 0xFF263440 : (primary ? (hovered ? ACCENT_HOVER : ACCENT) : (hovered ? HOVER : CARD));
        panel(x, y, w, h, fill, primary && enabled ? ACCENT : (enabled ? BORDER : SOFT_BORDER));
        centered(trim(label, w - 8), x, y + (h - 8) / 2,
                enabled ? (primary ? ACCENT_TEXT : WHITE) : MUTED, w);
        if (enabled) hits.add(new HitBox(x, y, w, h, action));
    }

    private void panel(int x, int y, int w, int h, int fill, int border) {
        drawRect(x, y, x + w, y + h, border);
        if (w > 2 && h > 2) drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private void scrollBar(int x, int y, int h, int visible, int total, int offset) {
        if (h <= 0 || visible <= 0 || total <= visible) return;
        drawRect(x, y, x + 2, y + h, BORDER);
        int thumb = Math.min(h, Math.max(16, h * visible / total));
        int maxOffset = total - visible;
        int thumbY = y + (h - thumb) * offset / maxOffset;
        drawRect(x, thumbY, x + 2, thumbY + thumb, ACCENT);
    }

    private void summaryLine(String label, String value, int x, int y, int w) {
        fontRendererObj.drawString("§7" + label, x, y, MUTED);
        String shown = trim(value, Math.max(20, w - 110));
        fontRendererObj.drawString(shown, x + w - fontRendererObj.getStringWidth(shown), y,
                "Gesamt".equals(label) ? ACCENT : WHITE);
    }

    private void centered(String value, int x, int y, int color, int w) {
        drawCenteredString(fontRendererObj, value, x + w / 2, y, color);
    }

    private void renderItem(ItemStack stack, int x, int y, float scale) {
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
        notice = message;
        noticeUntil = System.currentTimeMillis() + 2500L;
    }

    private static String formatPrice(BigDecimal value) { return PRICE.format(value) + "$"; }

    private static String stockText(ShopOffer offer) {
        int stock = offer.getMaximumPurchases();
        if (stock == Integer.MAX_VALUE) return "§aUnbegrenzt verfügbar";
        if (stock < 1) return "§cAusverkauft";
        return (stock <= 7 ? "§eNur " : "§a") + stock + " verfügbar";
    }

    private String trim(String text, int maxWidth) {
        String cleanText = clean(text);
        if (fontRendererObj.getStringWidth(cleanText) <= maxWidth) return cleanText;
        return fontRendererObj.trimStringToWidth(cleanText, Math.max(0, maxWidth - 8)) + "...";
    }

    private static String clean(String text) {
        if (text == null) return "";
        String plain = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        return plain == null ? text : plain;
    }

    private static boolean contains(String value, String... names) {
        for (String name : names) if (value.contains(name)) return true;
        return false;
    }

    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private enum Category {
        ALL("Alle"), BLOCKS("Blöcke"), DECO("Deko"), REDSTONE("Redstone"),
        TOOLS("Werkzeuge"), FOOD("Essen"), OTHER("Sonstiges");
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
