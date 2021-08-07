package uk.co.hopperelec.mc.stockblockeconomy;

import com.mysql.cj.jdbc.MysqlDataSource;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class MySQLHandler {
    final Plugin plugin;
    private final MysqlDataSource mysql;
    private int loopsTillHour;
    public final Map<String,Integer> economies = new HashMap<>();

    public MySQLHandler(Plugin plugin, MysqlDataSource mysql) {
        this.plugin = plugin;
        this.mysql = mysql;

        final LocalDateTime now = LocalDateTime.now();
        loopsTillHour = (int) Duration.between(now,now.plusHours(1).truncatedTo(ChronoUnit.HOURS)).toMinutes();
        plugin.getProxy().getScheduler().schedule(plugin, (() ->
                plugin.getProxy().getScheduler().schedule(plugin, () -> {
                    loopsTillHour--;
                    if (loopsTillHour == 0) {
                        loopsTillHour = 59;
                        makeTransaction(null,3,"*",1.005,"Hourly interest",true);
                    }
                    for (ProxiedPlayer player : plugin.getProxy().getPlayers())
                        makeTransaction(player,3,"+",1000,"Minute of playtime",true);
                }, 0, 1, TimeUnit.MINUTES)), Duration.between(now,now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).getNano(), TimeUnit.NANOSECONDS);

        runSQL("SELECT id,name FROM economies", null, results -> {
            while (results.next()) economies.put(results.getString("name"),results.getInt("id"));
        }, true);
    }

    public void getBalance(CommandSender requester, String playername, UseValues useValues, String economy) {
        String prefix = "";
        String suffix = "";
        if (economy != null) {
            economy = economy.toLowerCase();
            if (!economies.containsKey(economy)) {
                requester.sendMessage(new TextComponent("§cUnknown economy '"+economy+"'"));
                return;
            }
            suffix = " AND economies.name = ?";
        } else prefix = "economies.name AS economy,";
        String finalEconomy = economy;
        runSQL("SELECT "+prefix+"balances.value AS balance FROM balances INNER JOIN players ON balances.player = players.uuid INNER JOIN economies ON balances.economy_id = economies.id WHERE players.lastname = ?"+suffix, stmt -> {
            stmt.setString(1,playername);
            if (finalEconomy != null) stmt.setString(2, finalEconomy);
        }, results -> {
            if (results.next()) useValues.op(results);
            else requester.sendMessage(new TextComponent("§cUnknown player '"+playername+"'. Check capitalisation and spelling. They may have changed their name recently or haven't logged into the server in a while."));
        }, true);
    }

    public interface ApplyValues {
        void op(PreparedStatement stmt) throws SQLException;
    }

    public interface UseValues {
        void op(ResultSet results) throws SQLException;
    }

    public boolean runSQL(String statement, ApplyValues before, UseValues after, boolean logFail) {
        try (Connection conn = mysql.getConnection(); PreparedStatement stmt = conn.prepareStatement(statement)) {
            if (before != null) before.op(stmt);
            if (after != null) after.op(stmt.executeQuery());
            else stmt.execute();
            return true;
        } catch (SQLException e) {
            if (logFail) plugin.getLogger().log(Level.SEVERE,"Failed executing statement `"+statement+"`: "+e.getMessage());
            return false;
        }
    }

    public void makeTransaction(ProxiedPlayer player, int economy_id, String operand, double value, String description, boolean commonValue) {
        final String valueStr;
        if (commonValue) valueStr = Double.toString(value);
        else valueStr = "?";

        final ApplyValues applyValues;
        if (player != null) {
            final String uuid = player.getUniqueId().toString();
            applyValues = stmt -> {
                stmt.setString(1,uuid);
                if (!commonValue) stmt.setDouble(2,value);
            };
            runSQL("INSERT INTO transaction_log (player,economy_id,operand,value,description) VALUES (?,"+economy_id+",'"+operand+"',"+valueStr+",'"+description+"')", applyValues, null, true);
            runSQL("UPDATE balances SET value = value"+operand+valueStr+" WHERE player = ? AND economy_id = "+economy_id, applyValues, null, true);
        } else {
            if (!commonValue) applyValues = stmt -> stmt.setDouble(1,value);
            else applyValues = null;
            runSQL("INSERT INTO transaction_log (player,economy_id,operand,value,description) (SELECT player,economy_id,'"+operand+"',"+valueStr+",'"+description+"' FROM balances WHERE economy_id = "+economy_id+")", applyValues, null, true);
            System.out.println("UPDATE balances SET value = value"+operand+valueStr+" WHERE economy_id = "+economy_id);
            runSQL("UPDATE balances SET value = value"+operand+valueStr+" WHERE economy_id = "+economy_id, applyValues, null, true);
        }
    }

    public void makeTransaction(ProxiedPlayer player, String economy, String operand, double value, String description, boolean commonValue) {
        runSQL("SELECT id FROM economies WHERE name = ?",stmt -> stmt.setString(1,economy),results -> {
            results.next();
            makeTransaction(player,results.getInt("id"),operand,value,description,commonValue);
        },true);
    }
}
