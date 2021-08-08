package uk.co.hopperelec.mc.stockblockeconomy;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.InvalidCommandArgument;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bungee.contexts.OnlinePlayer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

@CommandAlias("eco|economy|sb|stockblock")
@Description("Core command for StockBlock economy")
public final class SBEcoCommand extends BaseCommand {
    SBEcoPlugin plugin;
    private final String defaultEconomy = "bank";
    private final double exchangeCharge = 1.3;
    private final String border = "§0================================";

    public SBEcoCommand(SBEcoPlugin plugin) {
        this.plugin = plugin;
    }

    @Subcommand("exchangerate|rate")
    @Description("Returns the exchange rate between two economies")
    @Syntax("(economy1) [economy2]")
    @CommandCompletion("@economy @economy")
    public void exchangerate(CommandSender sender, @Values("@economy") String economy1, @Values("@economy") @Default(defaultEconomy) String economy2) {
        if (economy1.equals(economy2)) sender.sendMessage(new TextComponent("§cYou can't exchange from an economy to itself!"));
        else {
            String innerSelect = " (SELECT SUM(balances.value) AS total FROM balances INNER JOIN economies ON balances.economy_id = economies.id WHERE economies.name = ?) ";
            plugin.mysqlHandler.runSQL("SELECT a.total/b.total*"+exchangeCharge+" AS rate FROM"+innerSelect+"a,"+innerSelect+"b", stmt -> {
                stmt.setString(1,economy1);
                stmt.setString(2,economy2);
            }, results -> {
                results.next();
                sender.sendMessage(new TextComponent(String.format("§eThe exchange rate from $1 in "+economy1+" to $1 in "+economy2+" is $%.2f", results.getDouble("rate"))));
            }, true);
        }
    }

    @Subcommand("list|economies|all")
    @Description("Returns a list of all economies")
    public void listEconomies(CommandSender sender) {
        sender.sendMessage(new TextComponent("§e"+String.join(", ",plugin.mysqlHandler.economies.keySet())));
    }

    @Subcommand("balance|bal")
    @CommandAlias("balance|bal")
    @Description("Returns a player's balance in an economy")
    @Syntax("[economy] [playername]")
    @CommandCompletion("@economy @players")
    public void balance(CommandSender sender, @Values("@economy") @Default(defaultEconomy) String economy, @Optional String playername) {
        if (playername == null) {
            if (sender instanceof ProxiedPlayer) playername = sender.getName();
            else throw new InvalidCommandArgument("Error: playername is not optional for console");
        }
        final String finalPlayername = playername;
        plugin.mysqlHandler.getBalance(sender, playername, results -> sender.sendMessage(new TextComponent("§e"+finalPlayername+"'s "+economy+" balance is $"+results.getInt("balance"))), economy);
    }

    @Subcommand("balances|bals")
    @CommandAlias("balances|bals")
    @Description("Returns a player's balance in all economies they are active in")
    @Syntax("(playername)")
    @CommandCompletion("@players")
    public void balances(CommandSender sender, @Optional String playername) {
        final String finalPlayername;
        if (playername == null) {
            if (sender instanceof ProxiedPlayer) finalPlayername = sender.getName();
            else throw new InvalidCommandArgument("Error: playername is not optional for console");
        }
        else finalPlayername = playername;
        plugin.mysqlHandler.getBalance(sender, finalPlayername, results -> {
            final StringBuilder listOfResults = new StringBuilder(results.getString("economy")+": $"+results.getInt("balance"));
            while (results.next()) listOfResults.append("\n").append(results.getString("economy")).append(": $").append(results.getInt("balance"));
            sender.sendMessage(new TextComponent(border+"\n§e"+finalPlayername +"'s balances:§f\n"+listOfResults+"\n"+border));
        }, null);
    }

    @Subcommand("balanceleaderboard|leaderboard|lb|balancetop|baltop|top")
    @CommandAlias("balancetop|baltop")
    @Description("Returns a list of the top 10 balances in an economy")
    @Syntax("(economy)")
    @CommandCompletion("@economy")
    public void balanceleaderboard(CommandSender sender, @Values("@economy") @Default(defaultEconomy) String economy) {
        plugin.mysqlHandler.runSQL("SELECT players.lastname AS player,balances.value AS balance FROM balances INNER JOIN economies ON balances.economy_id = economies.id INNER JOIN players ON balances.player = players.uuid WHERE economies.name = ? ORDER BY balances.value DESC LIMIT 10",stmt -> stmt.setString(1,economy),results -> {
            final StringBuilder listOfResults = new StringBuilder("\n");
            while (results.next()) listOfResults.append(results.getString("player")).append(": $").append(results.getInt("balance")).append("\n");
            sender.sendMessage(new TextComponent(border+"\n§eTop balances in "+economy+":§f"+listOfResults+border));
        },true);
    }

    @Subcommand("pay|give|send")
    @CommandAlias("pay")
    @Description("Transfers money in an economy to another online player")
    @Syntax("(player) (value|all) [economy]")
    @CommandCompletion("@players @moneyvalues @economy")
    public void pay(ProxiedPlayer sender, OnlinePlayer player, Payment value, @Optional @Values("@economy") @Default(defaultEconomy) String economy) {
        if (!value.all & value.value < 1) sender.sendMessage(new TextComponent("§cValue must be a positive number!"));
        else {
            plugin.mysqlHandler.getBalance(sender,sender.getName(),results -> {
                results.next();
                if (value.all) value.value = results.getInt(1);
                else if (value.value > results.getInt(1)) {
                    sender.sendMessage(new TextComponent("§cYou do not have $"+value.value));
                    return;
                }
                plugin.mysqlHandler.makeTransaction(sender,economy,"-",value.value,"Payment to "+player.getPlayer().getName(),false);
                plugin.mysqlHandler.makeTransaction(player.getPlayer(),economy,"+",value.value,"Payment from "+sender.getName(),false);
            },economy);
        }
    }
}
