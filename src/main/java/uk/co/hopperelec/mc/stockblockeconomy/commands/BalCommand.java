package uk.co.hopperelec.mc.stockblockeconomy.commands;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import uk.co.hopperelec.mc.stockblockeconomy.SBEcoPlugin;

public final class BalCommand extends Command {
    final SBEcoPlugin.SubCommandHandler subCommandHandler;

    public BalCommand(SBEcoPlugin.SubCommandHandler subCommandHandler) {
        super("eco",null,"bal","balance");
        this.subCommandHandler = subCommandHandler;
    }

    public void execute(CommandSender sender, String[] args) {
        subCommandHandler.op(sender,args);
    }
}
