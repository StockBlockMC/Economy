package uk.co.hopperelec.mc.stockblockeconomy;

import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import uk.co.hopperelec.mc.stockblockeconomy.commands.*;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

public final class SBEcoPlugin extends Plugin implements Listener {
    public MySQLHandler mysqlHandler;
    private final List<String> subcommands = Arrays.asList("exchangerate", "exchange", "rate", "list", "economies", "all", "balance", "bal", "balances", "bals", "top", "baltop", "balancetop","leaderboard","balanceleaderboard");

    @Override
    public void onEnable() {
        Configuration databaseConfig;
        try {
            databaseConfig = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "db.yml"));
        } catch (IOException e) {
            getLogger().log(Level.SEVERE,"Please create a db.yml with the database login details");
            return;
        }

        MysqlDataSource mysql = new MysqlConnectionPoolDataSource();
        mysql.setDatabaseName(databaseConfig.getString("name"));
        mysql.setUser(databaseConfig.getString("user"));
        mysql.setPassword(databaseConfig.getString("password"));

        try (Connection conn = mysql.getConnection()) {
            if (conn.isValid(1000)) getLogger().log(Level.INFO,"Successfully connected to database");
            else throw new SQLException("Could not establish database connection.");
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE,"Could not establish database connection: ");
            e.printStackTrace();
            return;
        }

        mysqlHandler = new MySQLHandler(this, mysql);
        CommandHandler commandHandler = new CommandHandler(this);
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new EcoCommand(commandHandler));
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new BalCommand(commandHandler::balance));
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new BalsCommand(commandHandler::balances));
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new BaltopCommand(commandHandler::balanceleaderboard));
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new PayCommand(commandHandler::pay));
        ProxyServer.getInstance().getPluginManager().registerListener(this, this);
    }

    public interface SubCommandHandler {
        void op(CommandSender sender, String[] args);
    }

    @EventHandler
    public void onPlayerLogin(PostLoginEvent event) {
        MySQLHandler.ApplyValues applyPlayer = stmt -> {
            stmt.setString(1,event.getPlayer().getName());
            stmt.setString(2,event.getPlayer().getUniqueId().toString());
        };

        if (mysqlHandler.runSQL("INSERT INTO players (lastname,uuid) VALUES (?,?)", applyPlayer, null, false)) {
            final String uuid = event.getPlayer().getUniqueId().toString();
            MySQLHandler.ApplyValues applyValues = stmt -> {
                for (int i = 1; i < 4; i++) stmt.setString(i,uuid);
            };

            mysqlHandler.runSQL("INSERT INTO balances (player,economy_id,value) VALUES (?,1,0),(?,2,0),(?,3,60000)", applyValues, null, true);
            final String desc = "First login to network";
            mysqlHandler.runSQL("INSERT INTO transaction_log (player,economy_id,operand,value,description) VALUES (?,1,'=',0,'"+desc+"'),(?,2,'=',0,'"+desc+"'),(?,3,'=',60000,'"+desc+"')", applyValues, null, true);
        } else mysqlHandler.runSQL("UPDATE players SET lastname = ? WHERE uuid = ?", applyPlayer, null, true);
    }

    @EventHandler
    public void onPlayerJoinServer(ServerConnectedEvent event) {
        String servername = event.getServer().getInfo().getName();
        if (mysqlHandler.economies.containsKey(servername)) {
            final String uuid = event.getPlayer().getUniqueId().toString();
            MySQLHandler.ApplyValues applyValues = stmt -> {
                stmt.setString(1,uuid);
                stmt.setInt(2,mysqlHandler.economies.get(servername));
            };
            if (mysqlHandler.runSQL("INSERT INTO balances (player,economy_id) VALUES (?,?)", applyValues, null, false))
                mysqlHandler.runSQL("INSERT INTO transaction_log (player,economy_id,operand,value,description) VALUES (?,?,'=',0,'First login to relevant server')", applyValues, null, true);
        }
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        mysqlHandler.runSQL("UPDATE players SET lastonline = ? WHERE uuid = ?", stmt -> {
            stmt.setTimestamp(1,new Timestamp(System.currentTimeMillis()));
            stmt.setString(2,event.getPlayer().getUniqueId().toString());
        }, null, true);
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        List<String> args = Arrays.asList(event.getCursor().split(" "));
        String lastArg;
        if (event.getCursor().endsWith(" ")) lastArg = "";
        else lastArg = args.get(args.size()-1).toLowerCase();

        switch (args.get(0)) {
            case "eco":
            case "economy":
            case "stockblock":
                if (args.size() > 2 || (args.size() == 2 && lastArg.equals(""))) {
                    switch (args.get(1).toLowerCase()) {
                        case "exchangerate":
                        case "exchange":
                        case "rate":
                        case "top":
                        case "baltop":
                        case "balancetop":
                        case "leaderboard":
                        case "balanceleaderboard":
                            for (String economy : mysqlHandler.economies.keySet())
                                if (!economy.equals(lastArg) && economy.toLowerCase().startsWith(lastArg)) event.getSuggestions().add(economy);
                            break;

                        case "pay":
                        case "send":
                        case "give":
                            event.getSuggestions().add("all");

                        case "bal":
                        case "balance":
                            for (String economy : mysqlHandler.economies.keySet())
                                if (!economy.equals(lastArg) && economy.toLowerCase().startsWith(lastArg)) event.getSuggestions().add(economy);

                        case "bals":
                        case "balances":
                            for (ProxiedPlayer player : getProxy().getPlayers())
                                if (!player.getName().equals(lastArg) && player.getName().toLowerCase().startsWith(lastArg)) event.getSuggestions().add(player.getName());
                            break;
                    }
                } else if (args.size() == 2) {
                    for (String subcommand : subcommands) {
                        if (!subcommand.equals(lastArg) && subcommand.toLowerCase().startsWith(lastArg)) event.getSuggestions().add(subcommand);
                    }
                } else if (lastArg.equals("")) event.getSuggestions().addAll(subcommands);

            case "baltop":
            case "balancetop":
                for (String economy : mysqlHandler.economies.keySet())
                    if (!economy.equals(lastArg) && economy.toLowerCase().startsWith(lastArg)) event.getSuggestions().add(economy);
                break;

            case "pay":
                event.getSuggestions().add("all");

            case "bal":
            case "balance":
                for (String economy : mysqlHandler.economies.keySet())
                    if (!economy.equals(lastArg) && economy.toLowerCase().startsWith(lastArg)) event.getSuggestions().add(economy);

            case "bals":
            case "balances":
                for (ProxiedPlayer player : getProxy().getPlayers())
                    if (!player.getName().equals(lastArg) && player.getName().toLowerCase().startsWith(lastArg)) event.getSuggestions().add(player.getName());
        }
    }
}
