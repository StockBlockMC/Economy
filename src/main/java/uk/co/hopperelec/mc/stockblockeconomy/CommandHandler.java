package uk.co.hopperelec.mc.stockblockeconomy;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public final class CommandHandler {
    final SBEcoPlugin plugin;
    private final String defaultEconomy = "bank";
    private final double exchangeCharge = 1.3;
    private final String border = "§0================================";

    boolean isEconomy(String name) {
        return plugin.mysqlHandler.economies.containsKey(name);
    }

    public CommandHandler(SBEcoPlugin plugin) {
        this.plugin = plugin;
    }

    public void exchangerate(CommandSender sender, String[] args) {
        if (args.length == 0) sender.sendMessage(new TextComponent("§6Please enter the name of one or two economies to get the exchange rate between them (second defaults to "+defaultEconomy+")"));
        else {
            final String economy1 = args[0].toLowerCase();
            final String economy2;
            if (args.length > 2) economy2 = args[1].toLowerCase();
            else economy2 = defaultEconomy;

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
    }

    public void listEconomies(CommandSender sender) {
        sender.sendMessage(new TextComponent("§e"+String.join(", ",plugin.mysqlHandler.economies.keySet())));
    }

    public void balance(CommandSender sender, String[] args) {
        String playername = sender.getName();
        String economy = defaultEconomy;
        if (args.length == 1)
            if (isEconomy(args[0].toLowerCase())) economy = args[0].toLowerCase();
            else playername = args[0];
        else if (args.length > 2)
            if (isEconomy(args[0].toLowerCase())) {
                economy = args[0].toLowerCase();
                playername = args[1];
            } else if (isEconomy(args[1].toLowerCase())) {
                playername = args[0];
                economy = args[1].toLowerCase();
            } else {
                sender.sendMessage(new TextComponent("§6Could not find an economy in either of the specified arguments. You should specify a valid economy and a playername!"));
                return;
            }
        final String message = "§e"+playername+"'s "+economy+" balance is $";
        plugin.mysqlHandler.getBalance(sender, playername, results -> sender.sendMessage(new TextComponent(message+results.getInt("balance"))), economy);
    }

    public void balances(CommandSender sender, String[] args) {
        final String playername;
        if (args.length == 0) playername = sender.getName();
        else playername = args[0];
        plugin.mysqlHandler.getBalance(sender, playername, results -> {
            final StringBuilder listOfResults = new StringBuilder(results.getString("economy")+": $"+results.getInt("balance"));
            while (results.next()) listOfResults.append("\n").append(results.getString("economy")).append(": $").append(results.getInt("balance"));
            sender.sendMessage(new TextComponent(border+"\n§e"+playername +"'s balances:§f\n"+listOfResults+"\n"+border));
        }, null);
    }

    public void balanceleaderboard(CommandSender sender, String[] args) {
        final String economy;
        if (args.length == 0) economy = defaultEconomy;
        else economy = args[0];
        plugin.mysqlHandler.runSQL("SELECT players.lastname AS player,balances.value AS balance FROM balances INNER JOIN economies ON balances.economy_id = economies.id INNER JOIN players ON balances.player = players.uuid WHERE economies.name = ? ORDER BY balances.value DESC LIMIT 10",stmt -> stmt.setString(1,economy),results -> {
            final StringBuilder listOfResults = new StringBuilder("\n");
            while (results.next()) listOfResults.append(results.getString("player")).append(": $").append(results.getInt("balance")).append("\n");
            sender.sendMessage(new TextComponent(border+"\n§eTop balances in "+economy+":§f"+listOfResults+border));
        },true);
    }

    public void pay(CommandSender sender, String[] args) {
        String playername;
        String economy = defaultEconomy;
        int value = -1;
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("all")) playername = args[1];
            else if (args[1].equalsIgnoreCase("all")) playername = args[0];
            else {
                try {
                    value = Integer.parseInt(args[0]);
                    playername = args[1];
                } catch (NumberFormatException e1) {
                    try {
                        value = Integer.parseInt(args[1]);
                        playername = args[0];
                    } catch (NumberFormatException e2) {
                        sender.sendMessage(new TextComponent("§6Neither of the arguments could be processed as a value. Make sure you atleast specify a player name and value. You can also optionally specify an economy which defaults to 'bank'."));
                        return;
                    }
                }
            }

        } else if (args.length > 3) {
            economy = "Change example";
            sender.sendMessage(new TextComponent("I haven't bothered to code /pay to work for anything other than bank yet cos my code is getting messy and I'm going to focus on trying to tidy it up first"));
            return;

        } else {
            sender.sendMessage(new TextComponent("§6You must atleast specify a player name and value. You can also optionally specify an economy which defaults to 'bank'."));
            return;
        }

        if (value < 1 && value != -1) sender.sendMessage(new TextComponent("§cValue must be a positive number!"));
        else {
            final String finalPlayername = playername;
            final String finalEconomy = economy;
            int finalValuePay = value;
            plugin.mysqlHandler.getBalance(sender,sender.getName(),results -> {
                results.next();
                int innerValuePay;
                if (finalValuePay == -1) innerValuePay = results.getInt(1);
                else if (finalValuePay < results.getInt(1)) innerValuePay = finalValuePay;
                else {
                    sender.sendMessage(new TextComponent("§cYou do not have $"+finalValuePay));
                    return;
                }

                ProxiedPlayer recipient = plugin.getProxy().getPlayer(finalPlayername);
                if (recipient == null) sender.sendMessage(new TextComponent("No player by the name '"+finalPlayername+"' is currently online. Check capitalisation and spelling."));
                else {
                    plugin.mysqlHandler.makeTransaction((ProxiedPlayer)sender,finalEconomy,"-",innerValuePay,"Payment to "+finalPlayername,false);
                    plugin.mysqlHandler.makeTransaction(recipient,finalEconomy,"+",innerValuePay,"Payment from "+finalPlayername,false);
                }
            },finalEconomy);
        }
    }
}
