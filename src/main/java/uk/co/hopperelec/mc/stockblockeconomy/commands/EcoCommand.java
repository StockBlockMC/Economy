package uk.co.hopperelec.mc.stockblockeconomy.commands;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import uk.co.hopperelec.mc.stockblockeconomy.CommandHandler;

import java.util.Arrays;

public final class EcoCommand extends Command {
    final CommandHandler commandHandler;

    public EcoCommand(CommandHandler commandHandler) {
        super("eco",null,"economy","sb","stockblock");
        this.commandHandler = commandHandler;
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TextComponent("Ooh economy shit (help information will be here)"));

        } else {
            String[] argsToSend = Arrays.copyOfRange(args, 1, args.length);
            switch (args[0].toLowerCase()) {
                case "exchangerate":
                case "exchange":
                case "rate":
                    commandHandler.exchangerate(sender,argsToSend);
                    break;

                case "list":
                case "economies":
                case "all":
                    commandHandler.listEconomies(sender);
                    break;

                case "bal":
                case "balance":
                    commandHandler.balance(sender,argsToSend);
                    break;

                case "bals":
                case "balances":
                    commandHandler.balances(sender,argsToSend);
                    break;

                case "top":
                case "baltop":
                case "balancetop":
                case "leaderboard":
                case "balanceleaderboard":
                    commandHandler.balanceleaderboard(sender,argsToSend);
                    break;

                case "pay":
                case "send":
                case "give":
                    commandHandler.pay(sender,argsToSend);
                    break;

                default:
                    sender.sendMessage(new TextComponent("§cUnknown subcommand '"+args[0]+"'"));
            }
        }
    }
}
