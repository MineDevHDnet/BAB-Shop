package net.minedevhd.bytebitshop.command;

import java.util.Arrays;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minedevhd.bytebitshop.ByteBitShopCore;
import net.minedevhd.bytebitshop.model.ShopBot;
import net.minedevhd.bytebitshop.util.Chat;

public final class ByteBitShopCommand extends CommandBase {
    private final ByteBitShopCore core;

    public ByteBitShopCommand(ByteBitShopCore core) {
        this.core = core;
    }

    @Override public String getCommandName() { return "babshop"; }
    @Override public String getCommandUsage(ICommandSender sender) { return "/babshop [refresh|bots|cancel|config]"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public List<String> getCommandAliases() { return Arrays.asList("bytebitshop", "bbshop"); }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            core.openNearbyShop();
            return;
        }

        String sub = args[0].toLowerCase();
        if ("refresh".equals(sub)) {
            core.refreshSources(true);
            return;
        }
        if ("cancel".equals(sub)) {
            core.getPaymentQueue().cancel();
            return;
        }
        if ("bots".equals(sub)) {
            List<ShopBot> bots = core.getRegistry().loadedBots();
            Chat.info("Bekannte Bot-UUIDs: §f" + core.getRegistry().knownBotCount() + "§7, aktuell gesehen: §f" + bots.size());
            for (ShopBot bot : bots) {
                String state = bot.isSyncing() ? "lädt" : (bot.getLastError() == null ? bot.getOffers().size() + " Angebote" : "Fehler");
                Chat.info("§f" + bot.getName() + " §8- §7" + state);
            }
            return;
        }
        if ("config".equals(sub)) {
            Chat.info("Hotkey: §fOptionen → Steuerung → ByteBitShop §8| §7Config: §f.minecraft/config/bytebitshop.json");
            return;
        }

        Chat.info("§f/babshop §8| §f/babshop refresh §8| §f/babshop bots §8| §f/babshop cancel §8| §f/babshop config");
    }
}
