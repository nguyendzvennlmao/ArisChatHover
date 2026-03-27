package me.aris.arischathover;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;

public class MsgCommand implements CommandExecutor {
    private final ArisChatHover plugin;

    public MsgCommand(ArisChatHover plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (label.equalsIgnoreCase("msgtoggle")) {
            boolean isToggled = plugin.msgToggled.contains(p.getUniqueId());
            if (isToggled) {
                plugin.msgToggled.remove(p.getUniqueId());
                sendToggleStatus(p, plugin.msgConfig.getString("msg-on"), "msg-toggle");
            } else {
                plugin.msgToggled.add(p.getUniqueId());
                sendToggleStatus(p, plugin.msgConfig.getString("msg-off"), "msg-toggle");
            }
            return true;
        }

        if (label.equalsIgnoreCase("msg")) {
            if (args.length < 2) return false;
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                p.sendMessage(ArisChatHover.HEX_SERIALIZER.deserialize(plugin.msgConfig.getString("player-not-found")));
                plugin.soundManager.playSound(p, "msg-error");
                return true;
            }
            if (plugin.msgToggled.contains(target.getUniqueId()) && !p.hasPermission("arischat.admin")) {
                p.sendMessage(ArisChatHover.HEX_SERIALIZER.deserialize(plugin.msgConfig.getString("msg-target-off")));
                plugin.soundManager.playSound(p, "msg-error");
                return true;
            }

            String msg = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            if (plugin.getConfig().getBoolean("small-caps", true)) msg = plugin.toSmallCaps(msg);

            String toF = plugin.msgConfig.getString("msg-to").replace("{player}", target.getName()).replace("{message}", msg);
            String fromF = plugin.msgConfig.getString("msg-from").replace("{player}", p.getName()).replace("{message}", msg);

            p.sendMessage(ArisChatHover.HEX_SERIALIZER.deserialize(toF));
            target.sendMessage(ArisChatHover.HEX_SERIALIZER.deserialize(fromF));
            
            plugin.soundManager.playSound(target, "msg-received");

            if (plugin.getConfig().getBoolean("msg-settings.actionbar")) {
                p.sendActionBar(ArisChatHover.HEX_SERIALIZER.deserialize(plugin.msgConfig.getString("status-sent").replace("{player}", target.getName())));
                target.sendActionBar(ArisChatHover.HEX_SERIALIZER.deserialize(plugin.msgConfig.getString("status-received").replace("{player}", p.getName())));
            }
        }
        return true;
    }

    private void sendToggleStatus(Player p, String raw, String configKey) {
        if (plugin.getConfig().getBoolean(configKey + ".chat")) {
            p.sendMessage(ArisChatHover.HEX_SERIALIZER.deserialize(raw));
        }
        if (plugin.getConfig().getBoolean(configKey + ".actionbar")) {
            p.sendActionBar(ArisChatHover.HEX_SERIALIZER.deserialize(raw));
        }
    }
        }
