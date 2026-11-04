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

/** A small, single-panel shop. All views use the same in-game window; no popup dialogs. */
public final class ShopGui extends GuiScreen {
    private static final DecimalFormat PRICE = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMAN));
    private static final int MAX_WIDTH = 650;
    private static final int MAX_HEIGHT = 455;
    private static final int PAD = 14;
    private static final int BACKGROUND = 0xFF0D1118;
    private static final int SURFACE = 0xFF171E28;
    private static final int CARD = 0xFF202A36;
    private static final int HOVER = 0xFF2A3645;
    private static final int BORDER = 0xFF344151;
    private static final int WHITE = 0xFFF5F7FA;
    private static final int MUTED = 0xFFA0ABB9;
    private static final int ACCENT = 0xFFF05D73;
    private static final int ACCENT_HOVER = 0xFFFF7488;
    private static final int GREEN = 0xFF76D7A0;

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
        compact = panelHeight < 310 || panelWidth < 470;
        headerHeight = compact ? 44 : 58;
        footerHeight = compact ? 43 : 56;

        search = new GuiTextField(1, fontRendererObj, left + PAD + 5, top + headerHeight + 12,
                Math.max(1, panelWidth - PAD * 2 - 10), 20);
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
        drawGradientRect(0, 0, width, height, 0xFF080B10, 0xFF151D28);
        drawRect(left - 3, top + 4, right + 3, bottom + 6, 0x67000000);
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
        if (notice != null && System.currentTimeMillis() < noticeUntil) {
            int w = Math.min(panelWidth - 20, fontRendererObj.getStringWidth(notice) + 26);
            int x = left + (panelWidth - w) / 2;
            int y = bottom - footerHeight - 29;
            panel(x, y, w, 23, 0xFF283443, ACCENT);
            centered(trim(notice, w - 14), x, y + 8, WHITE, w);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawHeader(int mouseX, int mouseY) {
        drawRect(left + 1, top + 1, right - 1, top + headerHeight, SURFACE);
        drawRect(left, top + headerHeight - 1, right, top + headerHeight, BORDER);
        drawRect(left + PAD, top + 10, left + PAD + 3, top + headerHeight - 10, ACCENT);
        fontRendererObj.drawString("§f§lBAB SHOP", left + PAD + 12, top + 11, WHITE);
        fontRendererObj.drawString("§7" + trim(bot.getName(), Math.max(50, panelWidth - 235)), left + PAD + 12, top + 26, MUTED);

        if (view == View.SHOP) {
            button(right - PAD - 99, top + (headerHeight - 27) / 2, 99, 27,
                    "Korb (" + cart.transactionCount() + ")", mouseX, mouseY, true, new Runnable() {
                        @Override public void run() { view = View.CART; cartScroll = 0; }
                    });
        } else {
            button(right - PAD - 80, top + (headerHeight - 27) / 2, 80, 27,
                    "Angebote", mouseX, mouseY, false, new Runnable() {
                        @Override public void run() { view = View.SHOP; }
                    });
        }
    }

    private void drawShop(int mouseX, int mouseY) {
        int filterTop = top + headerHeight;
        int searchTop = filterTop + 7;
        panel(left + PAD, searchTop, panelWidth - PAD * 2, 29, SURFACE, BORDER);
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused())
            fontRendererObj.drawString("§8Item suchen ...", left + PAD + 5, searchTop + 10, MUTED);

        int categoryY = filterTop + (compact ? 41 : 45);
        button(left + PAD, categoryY, 23, 25, "<", mouseX, mouseY, false, new Runnable() {
            @Override public void run() { switchCategory(-1); }
        });
        button(left + PAD + 25, categoryY, compact ? 103 : 142, 25,
                category.label + " (" + countFor(category) + ")", mouseX, mouseY, false, new Runnable() {
                    @Override public void run() { switchCategory(1); }
                });
        button(left + PAD + (compact ? 130 : 169), categoryY, 23, 25, ">", mouseX, mouseY, false, new Runnable() {
            @Override public void run() { switchCategory(1); }
        });

        int refreshW = compact ? 42 : 85;
        button(right - PAD - refreshW, categoryY, refreshW, 25,
                compact ? "Neu" : "Aktualisieren", mouseX, mouseY, false, new Runnable() {
                    @Override public void run() { refresh(); }
                });

        int listTop = categoryY + 34;
        int listBottom = bottom - footerHeight - 8;
        int rowH = compact ? 55 : 65;
        int step = rowH + 7;
        int visible = Math.max(0, (listBottom - listTop + 7) / step);
        int maxScroll = Math.max(0, filtered.size() - visible);
        shopScroll = clamp(shopScroll, 0, maxScroll);

        if (filtered.isEmpty()) {
            centered("§7Keine passenden Angebote", left, listTop + 24, MUTED, panelWidth);
            centered("§8Suche oder Kategorie ändern", left, listTop + 39, MUTED, panelWidth);
            return;
        }

        ItemStack hoveredItem = null;
        for (int i = shopScroll; i < filtered.size() && i < shopScroll + visible; i++) {
            final ShopOffer offer = filtered.get(i);
            int x = left + PAD;
            int y = listTop + (i - shopScroll) * step;
            int w = panelWidth - PAD * 2;
            boolean hovered = inside(mouseX, mouseY, x, y, w, rowH);
            panel(x, y, w, rowH, hovered ? HOVER : SURFACE, hovered ? ACCENT : BORDER);

            ItemStack stack = offer.getDisplayStack();
            if (stack != null) {
                panel(x + 8, y + (rowH - 40) / 2, 40, 40, CARD, BORDER);
                renderItem(stack, x + 14, y + (rowH - 25) / 2, 1.65F);
                if (inside(mouseX, mouseY, x + 8, y, 44, rowH)) hoveredItem = stack;
            }

            int infoX = x + 57;
            int labelWidth = Math.max(30, w - 177);
            fontRendererObj.drawString("§f" + trim(clean(offer.getDisplayName()), labelWidth), infoX, y + 11, WHITE);
            fontRendererObj.drawString(stockText(offer), infoX, y + (compact ? 28 : 35), MUTED);
            String price = formatPrice(offer.getPrice());
            fontRendererObj.drawString("§f§l" + trim(price, 98), x + w - 106, y + 11, WHITE);

            hits.add(new HitBox(x, y, w, rowH, new Runnable() {
                @Override public void run() {
                    selected = offer;
                    selectedQuantity = Math.max(1, cart.getQuantity(offer));
                    componentScroll = 0;
                    view = View.DETAIL;
                }
            }));
            button(x + w - 79, y + rowH - 30, 71, 23,
                    offer.isAvailable() ? "+ 1" : "Leer", mouseX, mouseY, offer.isAvailable(), new Runnable() {
                        @Override public void run() {
                            if (!offer.isAvailable() || !cart.add(offer, 1)) toast("Nicht genug Lagerbestand");
                            else toast("Zum Warenkorb hinzugefügt");
                        }
                    });
        }
        if (hoveredItem != null && !Mouse.isButtonDown(0)) renderToolTip(hoveredItem, mouseX, mouseY);
        if (maxScroll > 0) scrollBar(right - 7, listTop, listBottom - listTop, visible, filtered.size(), shopScroll);
    }

    private void drawCart(int mouseX, int mouseY) {
        int titleY = top + headerHeight + 17;
        fontRendererObj.drawString("§f§lWarenkorb", left + PAD, titleY, WHITE);
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
            centered("§7Dein Warenkorb ist leer", left, listTop + 24, MUTED, panelWidth);
            centered("§8Wähle unter Angebote ein Item aus.", left, listTop + 42, MUTED, panelWidth);
        }

        for (int i = cartScroll; i < lines.size() && i < cartScroll + visible; i++) {
            final ShoppingCart.Line line = lines.get(i);
            int x = left + PAD;
            int y = listTop + (i - cartScroll) * step;
            int w = panelWidth - PAD * 2;
            panel(x, y, w, rowH, SURFACE, BORDER);
            ItemStack stack = line.offer.getDisplayStack();
            if (stack != null) renderItem(stack, x + 9, y + 15, 1.0F);
            int labelWidth = Math.max(30, w - 178);
            fontRendererObj.drawString("§f" + trim(clean(line.offer.getDisplayName()), labelWidth), x + 34, y + 9, WHITE);
            fontRendererObj.drawString("§7" + trim(formatPrice(line.offer.getPrice()) + " / Stück", labelWidth), x + 34, y + 26, MUTED);
            int minusX = x + w - 110;
            button(minusX, y + 10, 28, 27, "-", mouseX, mouseY, false, new Runnable() {
                @Override public void run() { cart.add(line.offer, -1); }
            });
            panel(minusX + 30, y + 10, 48, 27, CARD, BORDER);
            centered("§f" + line.quantity, minusX + 30, y + 19, WHITE, 48);
            button(minusX + 80, y + 10, 28, 27, "+", mouseX, mouseY, false, new Runnable() {
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
        panel(x, y, w, compact ? 64 : 79, SURFACE, BORDER);
        ItemStack stack = selected.getDisplayStack();
        if (stack != null) renderItem(stack, x + 13, y + (compact ? 14 : 18), compact ? 2.0F : 2.5F);
        int infoX = x + 65;
        fontRendererObj.drawString("§f§l" + trim(clean(selected.getDisplayName()), w - 78), infoX, y + (compact ? 8 : 13), WHITE);
        fontRendererObj.drawString("§f" + formatPrice(selected.getPrice()) + " §7pro Zahlung", infoX, y + (compact ? 25 : 33), WHITE);
        fontRendererObj.drawString(stockText(selected), infoX, y + (compact ? 43 : 52), MUTED);

        int contentTop = y + (compact ? 72 : 90);
        fontRendererObj.drawString("§7DU ERHÄLTST PRO ZAHLUNG", x + 4, contentTop, MUTED);
        contentTop += 17;
        int contentBottom = bottom - footerHeight - 8;
        int visible = Math.max(0, (contentBottom - contentTop) / 23);
        List<ShopComponent> components = selected.getComponents();
        componentScroll = clamp(componentScroll, 0, Math.max(0, components.size() - visible));
        for (int i = componentScroll; i < components.size() && i < componentScroll + visible; i++) {
            ShopComponent component = components.get(i);
            int rowY = contentTop + (i - componentScroll) * 23;
            panel(x, rowY, w, 21, SURFACE, BORDER);
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
        fontRendererObj.drawString("§f§lEinkauf bestätigen", x, y, WHITE);
        fontRendererObj.drawString("§7Prüfe die Zahlung, bevor sie ausgeführt wird.", x, y + 19, MUTED);
        int infoY = y + (compact ? 35 : 50);
        panel(x, infoY, w, compact ? 79 : 110, SURFACE, BORDER);
        summaryLine("Shop", bot.getName(), x + 12, infoY + 14, w - 24);
        summaryLine("Zahlungen", String.valueOf(cart.transactionCount()), x + 12, infoY + 37, w - 24);
        summaryLine("Gesamt", formatPrice(cart.total()), x + 12, infoY + 60, w - 24);
        if (!compact) fontRendererObj.drawString("§7Jede Zahlung wird mit 2,5 s Abstand gesendet.", x + 12, infoY + 87, MUTED);
    }

    private void drawFooter(int mouseX, int mouseY) {
        int y = bottom - footerHeight;
        drawRect(left, y, right, y + 1, BORDER);
        drawRect(left + 1, y + 1, right - 1, bottom - 1, SURFACE);
        int buttonY = y + (footerHeight - 30) / 2;
        int actionW = compact ? 112 : 146;
        int actionX = right - PAD - actionW;

        if (view == View.SHOP) {
            String label = filtered.size() + " Angebote";
            fontRendererObj.drawString("§7" + label, left + PAD, y + (footerHeight - 8) / 2, MUTED);
            button(actionX, buttonY, actionW, 30, compact ? "Warenkorb" : "Warenkorb öffnen", mouseX, mouseY, true, new Runnable() {
                @Override public void run() { view = View.CART; cartScroll = 0; }
            });
        } else if (view == View.CART) {
            fontRendererObj.drawString("§7Gesamtpreis", left + PAD, y + 8, MUTED);
            fontRendererObj.drawString("§f§l" + trim(formatPrice(cart.total()), Math.max(30, actionX - left - PAD - 5)),
                    left + PAD, y + (compact ? 23 : 30), WHITE);
            boolean enabled = !cart.isEmpty() && cart.transactionCount() <= 128 && !core.getPaymentQueue().isBusy();
            button(actionX, buttonY, actionW, 30, enabled ? "Zur Kasse" : "Nicht verfügbar", mouseX, mouseY, enabled,
                    new Runnable() { @Override public void run() { view = View.CONFIRM; } });
        } else if (view == View.DETAIL) {
            button(left + PAD, buttonY, compact ? 49 : 78, 30, "Zurück", mouseX, mouseY, false,
                    new Runnable() { @Override public void run() { view = View.SHOP; selected = null; } });
            int qtyX = left + PAD + (compact ? 55 : 89);
            button(qtyX, buttonY + 2, 24, 26, "-", mouseX, mouseY, false,
                    new Runnable() { @Override public void run() { selectedQuantity = Math.max(1, selectedQuantity - 1); } });
            panel(qtyX + 26, buttonY + 2, 34, 26, CARD, BORDER);
            centered("§f" + selectedQuantity, qtyX + 26, buttonY + 11, WHITE, 34);
            button(qtyX + 62, buttonY + 2, 24, 26, "+", mouseX, mouseY, false,
                    new Runnable() { @Override public void run() { selectedQuantity = Math.min(64, selectedQuantity + 1); } });
            button(actionX, buttonY, actionW, 30, "Zum Warenkorb", mouseX, mouseY, true, new Runnable() {
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
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int direction = wheel > 0 ? -1 : 1;
        if (view == View.SHOP) shopScroll += direction;
        else if (view == View.CART) cartScroll += direction;
        else if (view == View.DETAIL) componentScroll += direction;
    }

    private void button(int x, int y, int w, int h, String label, int mouseX, int mouseY, boolean primary, Runnable action) {
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        int fill = primary ? (hovered ? ACCENT_HOVER : ACCENT) : (hovered ? HOVER : CARD);
        panel(x, y, w, h, fill, primary ? ACCENT : BORDER);
        centered("§f" + trim(label, w - 8), x, y + (h - 8) / 2, WHITE, w);
        hits.add(new HitBox(x, y, w, h, action));
    }

    private void panel(int x, int y, int w, int h, int fill, int border) {
        drawRect(x, y, x + w, y + h, border);
        if (w > 2 && h > 2) drawRect(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private void scrollBar(int x, int y, int h, int visible, int total, int offset) {
        if (h <= 0 || visible <= 0 || total <= visible) return;
        drawRect(x, y, x + 2, y + h, BORDER);
        int thumb = Math.max(16, h * visible / total);
        int maxOffset = total - visible;
        int thumbY = y + (h - thumb) * offset / maxOffset;
        drawRect(x, thumbY, x + 2, thumbY + thumb, ACCENT);
    }

    private void summaryLine(String label, String value, int x, int y, int w) {
        fontRendererObj.drawString("§7" + label, x, y, MUTED);
        String shown = trim(value, Math.max(20, w - 110));
        fontRendererObj.drawString("§f" + shown, x + w - fontRendererObj.getStringWidth(shown), y, WHITE);
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
