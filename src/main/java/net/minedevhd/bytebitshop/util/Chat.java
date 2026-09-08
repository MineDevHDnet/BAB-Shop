package net.minedevhd.bytebitshop.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

public final class Chat {
    private Chat() {}

    public static void info(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null)
            mc.thePlayer.addChatMessage(new ChatComponentText("§8[§bByteBitShop§8] §7" + text));
    }

    public static void error(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null)
            mc.thePlayer.addChatMessage(new ChatComponentText("§8[§bByteBitShop§8] §c" + text));
    }
}
