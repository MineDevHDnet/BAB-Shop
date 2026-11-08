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

/** A Minecraft-style inventory shop, entirely client-side and without server containers. */
public final class ShopGui extends GuiScreen {
    private static final DecimalFormat PRICE = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMAN));
    private static final int MAX_WIDTH = 780;
    private static final int MAX_HEIGHT = 500;
    private static final int PAD = 16;
    private static final int BACKGROUND = 0xFF171D23;
    private static final int SURFACE = 0xFF252E35;
    private static final int CARD = 0xFF303B43;
    private static final int HOVER = 0xFF45545C;
    private static final int BORDER = 0xFF69757A;
    private static final int SOFT_BORDER = 0xFF3C484F;
    private static final int WHITE = 0xFFF4F2E9;
    private static final int MUTED = 0xFFC0C5C1;
    private static final int ACCENT = 0xFF92D17B;
    private static final int ACCENT_HOVER = 0xFFB2E69A;
    private static final int ACCENT_TEXT = 0xFF1D3121;

    private enum View { SHOP, CART, DETAIL, CONFIRM }

    private final ByteBitShopCore core;
    private final ShopBot bot;
    private final ShoppingCart cart = new ShoppingCart();
    private final List<ShopOffer> filtered = new ArrayList<ShopOffer>();
    private final List<HitBox> hits = new ArrayList<HitBox>();
    private View view = View.SHOP;
    private Category category = Category.ALL;
    private GuiTextField search;
    private int searchX, searchY, searchW;
    private ShopOffer selected;
    private int selectedQuantity = 1;
    private int shopScroll;
    private int cartScroll;
    private int componentScroll;
    private int left, top, right, bottom, panelWidth, panelHeight, headerHeight, footerHeight;
    private boolean compact;
    private boolean mini;
    private ItemStack hoveredStack;
    private ShopOffer hoveredOffer;
    private int pageCount = 1;
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
        mini = panelHeight < 280;
        compact = panelHeight < 350 || panelWidth < 620;
        headerHeight = mini ? 45 : (compact ? 57 : 70);
        footerHeight = mini ? 40 : (compact ? 50 : 60);

        searchX = left + (panelWidth < 400 ? 120 : (compact ? 156 : 242));
        searchY = top + (headerHeight - 20) / 2;
        searchW = Math.max(40, right - PAD - searchX - 13);
        search = new GuiTextField(1, fontRendererObj, searchX + 6, searchY, searchW, 20);
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
        hoveredOffer = null;
        drawGradientRect(0, 0, width, height, 0xFF0D1114, 0xFF293038);
        drawRect(left - 5, top + 5, right + 5, bottom + 7, 0x88000000);
        panel(left, top, panelWidth, panelHeight, BACKGROUND, BORDER);

        drawHeader(mouseX, mouseY);
        switch (view) {
            case SHOP: drawShopInventory(mouseX, mouseY); break;
            case CART: drawCartInventory(mouseX, mouseY); break;
            case DETAIL: drawDetailInventory(mouseX, mouseY); break;
            case CONFIRM: drawConfirmInventory(mouseX, mouseY); break;
            default: break;
        }
        drawFooter(mouseX, mouseY);
        if (hoveredOffer != null && view == View.SHOP && !Mouse.isButtonDown(0)) {
            List<String> tips = new ArrayList<String>();
            tips.add("§f§l" + clean(hoveredOffer.getDisplayName()));
            tips.add("§aPreis: §f" + formatPrice(hoveredOffer.getPrice()));
            tips.add(stockText(hoveredOffer));
            tips.add("§7Linksklick: auswählen  ·  Shift: +1");
            drawHoveringText(tips, mouseX, mouseY);
        } else if (hoveredStack != null && !Mouse.isButtonDown(0)) {
            renderToolTip(hoveredStack, mouseX, mouseY);
        }
        if (notice != null && System.currentTimeMillis() < noticeUntil) {
            int w = Math.min(panelWidth - 20, fontRendererObj.getStringWidth(notice) + 26);
            int x = left + (panelWidth - w) / 2;
            int y = bottom - footerHeight - 29;
            panel(x, y, w, 23, 0xFF334B39, ACCENT);
            centered(trim(notice, w - 14), x, y + 8, WHITE, w);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawHeader(int mouseX, int mouseY) {
        drawGradientRect(left + 1, top + 1, right - 1, top + headerHeight, 0xFF353D42, 0xFF242C32);
        drawRect(left, top + headerHeight - 1, right, top + headerHeight, BORDER);
        int markerY = top + (mini ? 8 : 15);
        drawRect(left + PAD, markerY, left + PAD + 4, markerY + (mini ? 29 : 38), ACCENT);
        fontRendererObj.drawString("§f§lBAB SHOP", left + PAD + 15, markerY + 2, WHITE);
        fontRendererObj.drawString("§7" + trim(bot.getName(), Math.max(35, searchX - left - PAD - 23)),
                left + PAD + 15, markerY + 20, MUTED);

        if (view == View.SHOP) {
            panel(searchX, searchY - 5, searchW + 12, 29, SURFACE, SOFT_BORDER);
            search.drawTextBox();
            if (search.getText().isEmpty() && !search.isFocused())
                fontRendererObj.drawString("§7" + trim("Item oder Preis suchen ...", searchW - 8),
                        searchX + 8, searchY + 6, MUTED);
        } else {
            String title = view == View.CART ? "WARENKORB" : (view == View.DETAIL ? "PRODUKTDETAILS" : "KAUF ABSCHLIESSEN");
            fontRendererObj.drawString("§a§l" + title, right - PAD - fontRendererObj.getStringWidth(title),
                    top + (headerHeight - 8) / 2, ACCENT);
        }
    }

    private InventoryLayout inventoryLayout() {
        InventoryLayout layout = new InventoryLayout();
        layout.toolbarY = top + headerHeight + 6;
        layout.toolbarH = mini ? 24 : 30;
        layout.gridY = layout.toolbarY + layout.toolbarH + (mini ? 7 : 12);
        layout.bottom = bottom - footerHeight - 8;
        layout.previewW = panelWidth >= 435 ? (compact ? 124 : 214) : 0;
        int separation = layout.previewW > 0 ? 12 : 0;
        layout.gridW = panelWidth - PAD * 2 - layout.previewW - separation;
        layout.columns = compact && layout.previewW > 0 ? 7 : 9;
        while (layout.columns > 4 && layout.gridW < layout.columns * 28) layout.columns--;
        layout.slot = Math.min(compact ? 40 : 47,
                (layout.gridW - (layout.columns - 1) * 4) / layout.columns);
        layout.slot = Math.max(20, layout.slot);
        layout.pitch = layout.slot + 4;
        layout.rows = Math.min(6, Math.max(1, (layout.bottom - layout.gridY + 4) / layout.pitch));
        layout.capacity = layout.columns * layout.rows;
        layout.gridX = left + PAD + Math.max(0,
                (layout.gridW - layout.columns * layout.slot - (layout.columns - 1) * 4) / 2);
        layout.previewX = left + PAD + layout.gridW + separation;
        return layout;
    }

    private void drawShopInventory(int mouseX, int mouseY) {
        InventoryLayout l = inventoryLayout();
        drawInventoryCategories(l, mouseX, mouseY);

        pageCount = Math.max(1, (filtered.size() + l.capacity - 1) / l.capacity);
        shopScroll = clamp(shopScroll, 0, pageCount - 1);
        int first = shopScroll * l.capacity;
        int end = Math.min(filtered.size(), first + l.capacity);

        if (!filtered.isEmpty() && !filtered.contains(selected)) {
            selected = filtered.get(0);
            selectedQuantity = Math.max(1, cart.getQuantity(selected));
        }
        if (filtered.isEmpty()) selected = null;

        for (int i = first; i < end; i++) {
            final ShopOffer offer = filtered.get(i);
            int offset = i - first;
            int x = l.gridX + (offset % l.columns) * l.pitch;
            int y = l.gridY + (offset / l.columns) * l.pitch;
            boolean hover = inside(mouseX, mouseY, x, y, l.slot, l.slot);
            drawInventorySlot(x, y, l.slot, hover, offer == selected);
            ItemStack stack = offer.getDisplayStack();
            if (stack != null) drawSlotItem(stack, x, y, l.slot);
            int dotColor = offer.isAvailable() ? ACCENT : 0xFFE27E7E;
            drawRect(x + l.slot - 7, y + 4, x + l.slot - 3, y + 8, dotColor);

            if (hover) hoveredOffer = offer;
            hits.add(new HitBox(x, y, l.slot, l.slot, new Runnable() {
                @Override public void run() {
                    if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
                        if (!offer.isAvailable() || !cart.add(offer, 1)) toast("Nicht genug Lagerbestand");
                        else toast("+1 zum Warenkorb hinzugefügt");
                        return;
                    }
                    if (l.previewW == 0) {
                        selected = offer;
                        selectedQuantity = Math.max(1, cart.getQuantity(offer));
                        componentScroll = 0;
                        view = View.DETAIL;
                    } else {
                        selected = offer;
                        selectedQuantity = Math.max(1, cart.getQuantity(offer));
                    }
                }
            }));
        }

        if (filtered.isEmpty()) {
            panel(l.gridX, l.gridY, l.gridW, Math.min(78, l.bottom - l.gridY), SURFACE, SOFT_BORDER);
            centered("§fKeine passenden Produkte", l.gridX, l.gridY + 16, WHITE, l.gridW);
            centered("§7Suche oder Kategorie ändern", l.gridX, l.gridY + 35, MUTED, l.gridW);
        } else if (end - first < l.capacity) {
            // Empty slots keep the familiar Minecraft-inventory structure visible.
            for (int i = end - first; i < l.capacity; i++) {
                int x = l.gridX + (i % l.columns) * l.pitch;
                int y = l.gridY + (i / l.columns) * l.pitch;
                drawInventorySlot(x, y, l.slot, false, false);
            }
        }

        if (l.previewW > 0) drawSelectedOffer(l, mouseX, mouseY);
    }

    private void drawInventoryCategories(InventoryLayout l, int mouseX, int mouseY) {
        int y = l.toolbarY;
        if (compact) {
            int refreshW = mini ? 52 : 70;
            int categoryW = Math.min(180, panelWidth - PAD * 2 - refreshW - 64);
            button(left + PAD, y, 24, l.toolbarH, "<", mouseX, mouseY, false, new Runnable() {
                @Override public void run() { switchCategory(-1); }
            });
            button(left + PAD + 28, y, categoryW, l.toolbarH,
                    category.label + " (" + countFor(category) + ")", mouseX, mouseY, false, new Runnable() {
                @Override public void run() { switchCategory(1); }
            });
            button(left + PAD + 32 + categoryW, y, 24, l.toolbarH, ">", mouseX, mouseY, false, new Runnable() {
                @Override public void run() { switchCategory(1); }
            });
            button(right - PAD - refreshW, y, refreshW, l.toolbarH, "Neu laden", mouseX, mouseY, false, new Runnable() {
                @Override public void run() { refresh(); }
            });
            return;
        }

        int refreshW = 86;
        int count = Category.values().length;
        int gap = 5;
        int areaW = panelWidth - PAD * 2 - refreshW - 12;
        int tabW = (areaW - (count - 1) * gap) / count;
        for (int i = 0; i < count; i++) {
            final Category option = Category.values()[i];
            int x = left + PAD + i * (tabW + gap);
            int w = i == count - 1 ? left + PAD + areaW - x : tabW;
            boolean active = category == option;
            boolean hover = inside(mouseX, mouseY, x, y, w, l.toolbarH);
            panel(x, y, w, l.toolbarH, active ? 0xFF37543A : hover ? HOVER : SURFACE,
                    active ? ACCENT : SOFT_BORDER);
            if (active) drawRect(x + 2, y + l.toolbarH - 3, x + w - 2, y + l.toolbarH - 1, ACCENT);
            centered(trim(option.label, w - 8), x, y + (l.toolbarH - 8) / 2,
                    active ? WHITE : MUTED, w);
            hits.add(new HitBox(x, y, w, l.toolbarH, new Runnable() {
                @Override public void run() { category = option; shopScroll = 0; rebuildFilter(); }
            }));
        }
        button(right - PAD - refreshW, y, refreshW, l.toolbarH, "Neu laden", mouseX, mouseY, false,
                new Runnable() { @Override public void run() { refresh(); } });
    }

    private void drawInventorySlot(int x, int y, int size, boolean hover, boolean selectedSlot) {
        int outline = selectedSlot ? ACCENT : (hover ? WHITE : 0xFF70797B);
        drawRect(x, y, x + size, y + size, outline);
        drawRect(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF171D21);
        drawRect(x + 3, y + 3, x + size - 2, y + size - 2, hover ? HOVER : 0xFF303A41);
        drawRect(x + 2, y + 2, x + size - 2, y + 3, 0xFF11171B);
        if (selectedSlot) drawRect(x + 3, y + size - 4, x + size - 3, y + size - 2, ACCENT);
    }

    private void drawSlotItem(ItemStack stack, int x, int y, int size) {
        float scale = size >= 42 ? 2.0F : (size >= 34 ? 1.65F : 1.25F);
        int pixels = (int) (16F * scale);
        renderItem(stack, x + (size - pixels) / 2, y + (size - pixels) / 2, scale);
    }

    private void drawSelectedOffer(InventoryLayout l, int mouseX, int mouseY) {
        int x = l.previewX;
        int y = l.gridY;
        int w = l.previewW;
        int h = Math.max(60, l.bottom - y);
        panel(x, y, w, h, SURFACE, BORDER);
        drawRect(x + 1, y + 1, x + w - 1, y + 4, ACCENT);
        fontRendererObj.drawString("§f§lAUSWAHL", x + 10, y + 10, WHITE);
        if (selected == null) {
            centered("§7Wähle ein Item", x, y + 42, MUTED, w);
            return;
        }

        boolean shortPreview = h < 190;
        ItemStack stack = selected.getDisplayStack();
        int iconSize = shortPreview ? 31 : 55;
        int iconX = x + 10;
        int iconY = y + (shortPreview ? 27 : 34);
        drawInventorySlot(iconX, iconY, iconSize, false, false);
        if (stack != null) drawSlotItem(stack, iconX, iconY, iconSize);

        int infoX = x + (shortPreview ? 49 : 77);
        int infoWidth = w - (infoX - x) - 8;
        fontRendererObj.drawString("§f" + trim(clean(selected.getDisplayName()), infoWidth), infoX,
                y + (shortPreview ? 29 : 43), WHITE);
        fontRendererObj.drawString("§a" + trim(formatPrice(selected.getPrice()), infoWidth), infoX,
                y + (shortPreview ? 47 : 64), ACCENT);
        int stockY = y + (shortPreview ? 71 : 103);
        String stock = stockText(selected);
        fontRendererObj.drawString("§7" + trim(stock, w - 20), x + 10, stockY, MUTED);

        if (!shortPreview) {
            drawRect(x + 10, y + 121, x + w - 10, y + 122, SOFT_BORDER);
            int count = selected.getComponents().size();
            fontRendererObj.drawString("§7" + count + " Item-Komponenten pro Kauf", x + 10, y + 134, MUTED);
            secondaryAction(x + 10, y + h - 75, w - 20, 28, "Details ansehen", mouseX, mouseY,
                    new Runnable() { @Override public void run() { view = View.DETAIL; componentScroll = 0; } });
        } else {
            hits.add(new HitBox(x, y, w, h - 32, new Runnable() {
                @Override public void run() { view = View.DETAIL; componentScroll = 0; }
            }));
        }

        button(x + 9, y + h - (shortPreview ? 29 : 34), w - 18, shortPreview ? 24 : 27,
                selected.isAvailable() ? "+1 in den Korb" : "Ausverkauft",
                mouseX, mouseY, true, selected.isAvailable(), new Runnable() {
            @Override public void run() {
                if (!cart.add(selected, 1)) toast("Nicht genug Lagerbestand");
                else toast("+1 zum Warenkorb hinzugefügt");
            }
        });
    }

    private void secondaryAction(int x, int y, int w, int h, String label, int mouseX, int mouseY, Runnable action) {
        button(x, y, w, h, label, mouseX, mouseY, false, action);
    }

    private void drawCartInventory(int mouseX, int mouseY) {
        List<ShoppingCart.Line> lines = cart.lines();
        int listTop = top + headerHeight + (mini ? 8 : 17);
        int listBottom = bottom - footerHeight - 9;
        int rowH = mini ? 44 : (compact ? 56 : 68);
        int step = rowH + 6;
        int visible = Math.max(1, (listBottom - listTop + 6) / step);
        cartScroll = clamp(cartScroll, 0, Math.max(0, lines.size() - visible));

        if (lines.isEmpty()) {
            panel(left + PAD, listTop, panelWidth - PAD * 2,
                    Math.min(95, Math.max(35, listBottom - listTop)), SURFACE, SOFT_BORDER);
            centered("§fDein Warenkorb ist leer", left, listTop + 16, WHITE, panelWidth);
            if (listBottom - listTop > 53)
                centered("§7Wähle im Shop Produkte aus.", left, listTop + 36, MUTED, panelWidth);
        }

        for (int i = cartScroll; i < lines.size() && i < cartScroll + visible; i++) {
            final ShoppingCart.Line line = lines.get(i);
            int x = left + PAD;
            int y = listTop + (i - cartScroll) * step;
            int w = panelWidth - PAD * 2;
            panel(x, y, w, rowH, SURFACE, SOFT_BORDER);
            int slot = mini ? 34 : (compact ? 43 : 54);
            int itemX = x + 6;
            int itemY = y + (rowH - slot) / 2;
            drawInventorySlot(itemX, itemY, slot, false, false);
            ItemStack stack = line.offer.getDisplayStack();
            if (stack != null) drawSlotItem(stack, itemX, itemY, slot);

            int controlW = mini ? 22 : 28;
            int qtyW = mini ? 32 : 45;
            int controlsX = x + w - (controlW * 2 + qtyW + 8) - 9;
            int textX = itemX + slot + 12;
            int textW = Math.max(18, controlsX - textX - 12);
            fontRendererObj.drawString("§f§l" + trim(clean(line.offer.getDisplayName()), textW), textX, y + 8, WHITE);
            fontRendererObj.drawString("§a" + trim(formatPrice(line.offer.getPrice()), textW),
                    textX, y + (mini ? 25 : 30), ACCENT);

            int controlsY = y + (rowH - 27) / 2;
            button(controlsX, controlsY, controlW, 27, "-", mouseX, mouseY, false, new Runnable() {
                @Override public void run() { cart.add(line.offer, -1); }
            });
            panel(controlsX + controlW + 4, controlsY, qtyW, 27, CARD, BORDER);
            centered("§f" + line.quantity, controlsX + controlW + 4, controlsY + 9, WHITE, qtyW);
            button(controlsX + controlW + qtyW + 8, controlsY, controlW, 27, "+", mouseX, mouseY, false,
                    new Runnable() {
                @Override public void run() {
                    if (!cart.add(line.offer, 1)) toast("Nicht genug Lagerbestand");
                }
            });
        }

        if (lines.size() > visible)
            scrollBar(right - 7, listTop, listBottom - listTop, visible, lines.size(), cartScroll);
    }

    private void drawDetailInventory(int mouseX, int mouseY) {
        if (selected == null) { view = View.SHOP; return; }
        int x = left + PAD;
        int y = top + headerHeight + 9;
        int w = panelWidth - PAD * 2;
        int heroH = mini ? 75 : (compact ? 94 : 120);
        panel(x, y, w, heroH, SURFACE, BORDER);
        drawRect(x + 1, y + 1, x + 5, y + heroH - 1, ACCENT);

        int slot = mini ? 54 : (compact ? 70 : 86);
        int slotX = x + 13;
        int slotY = y + (heroH - slot) / 2;
        drawInventorySlot(slotX, slotY, slot, false, false);
        ItemStack stack = selected.getDisplayStack();
        if (stack != null) drawSlotItem(stack, slotX, slotY, slot);
        int textX = slotX + slot + 17;
        int infoW = Math.max(30, x + w - textX - 12);
        fontRendererObj.drawString("§f§l" + trim(clean(selected.getDisplayName()), infoW), textX, y + 13, WHITE);
        fontRendererObj.drawString("§l" + trim(formatPrice(selected.getPrice()), infoW),
                textX, y + (mini ? 32 : 39), ACCENT);
        fontRendererObj.drawString(stockText(selected), textX, y + (mini ? 52 : 65), MUTED);

        int labelY = y + heroH + 12;
        fontRendererObj.drawString("§f§lDU ERHÄLTST PRO ZAHLUNG", x, labelY, WHITE);
        List<ShopComponent> components = selected.getComponents();
        int gridY = labelY + 17;
        int gridBottom = bottom - footerHeight - 7;
        int pitch = mini ? 30 : 43;
        int size = pitch - 4;
        int cols = Math.min(compact ? 8 : 9, Math.max(4, w / pitch));
        int rows = Math.max(0, (gridBottom - gridY + 4) / pitch);
        int capacity = cols * rows;
        int pages = capacity == 0 ? 1 : Math.max(1, (components.size() + capacity - 1) / capacity);
        componentScroll = clamp(componentScroll, 0, pages - 1);
        int first = capacity * componentScroll;
        for (int i = first; i < components.size() && i < first + capacity; i++) {
            ShopComponent component = components.get(i);
            int index = i - first;
            int cx = x + (index % cols) * pitch;
            int cy = gridY + (index / cols) * pitch;
            boolean hover = inside(mouseX, mouseY, cx, cy, size, size);
            drawInventorySlot(cx, cy, size, hover, false);
            ItemStack item = component.getStack();
            if (item != null) {
                drawSlotItem(item, cx, cy, size);
                if (hover) hoveredStack = item;
            }
        }
        if (pages > 1)
            fontRendererObj.drawString("§7Seite " + (componentScroll + 1) + "/" + pages,
                    x + w - 77, labelY, MUTED);
    }

    private void drawConfirmInventory(int mouseX, int mouseY) {
        int x = left + PAD;
        int y = top + headerHeight + 16;
        int w = panelWidth - PAD * 2;
        int h = Math.min(188, bottom - footerHeight - y - 12);
        panel(x, y, w, h, SURFACE, BORDER);
        drawRect(x + 1, y + 1, x + 5, y + h - 1, ACCENT);
        fontRendererObj.drawString("§f§lBESTELLUNG PRÜFEN", x + 16, y + 13, WHITE);
        fontRendererObj.drawString("§7" + trim("Die /pay-Zahlungen werden nacheinander gesendet.", w - 32),
                x + 16, y + 33, MUTED);
        drawRect(x + 16, y + 54, x + w - 16, y + 55, SOFT_BORDER);
        summaryLine("Shop", bot.getName(), x + 16, y + 66, w - 32);
        summaryLine("Zahlungen", String.valueOf(cart.transactionCount()), x + 16, y + 88, w - 32);
        summaryLine("Gesamtpreis", formatPrice(cart.total()), x + 16, y + 110, w - 32);
        if (h >= 157)
            fontRendererObj.drawString("§7Abstand zwischen Zahlungen: 2,5 Sekunden", x + 16, y + 140, MUTED);
    }

    private void drawFooter(int mouseX, int mouseY) {
        int y = bottom - footerHeight;
        drawRect(left, y, right, y + 1, BORDER);
        drawRect(left + 1, y + 1, right - 1, bottom - 1, 0xFF292F34);
        int buttonY = y + (footerHeight - 30) / 2;
        int actionW = compact ? Math.min(112, Math.max(76, panelWidth / 3)) : 154;
        int actionX = right - PAD - actionW;

        if (view == View.SHOP) {
            button(left + PAD, buttonY + 2, 27, 26, "<", mouseX, mouseY, false, shopScroll > 0, new Runnable() {
                @Override public void run() { shopScroll--; }
            });
            int pagerX = left + PAD + 34;
            fontRendererObj.drawString("§f§l" + (shopScroll + 1) + " / " + pageCount,
                    pagerX, buttonY + 12, WHITE);
            button(left + PAD + 90, buttonY + 2, 27, 26, ">", mouseX, mouseY, false,
                    shopScroll < pageCount - 1, new Runnable() {
                @Override public void run() { shopScroll++; }
            });
            if (!compact) fontRendererObj.drawString("§7" + filtered.size() + " Angebote",
                    left + PAD + 135, buttonY + 11, MUTED);
            button(actionX, buttonY, actionW, 30, "Warenkorb (" + cart.transactionCount() + ")", mouseX, mouseY, true, new Runnable() {
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

    private static final class InventoryLayout {
        int toolbarY, toolbarH, gridX, gridY, gridW, bottom;
        int columns, rows, slot, pitch, capacity, previewX, previewW;
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
