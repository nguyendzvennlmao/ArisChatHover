package me.aris.arischathover;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArisChatHover extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTime = new ConcurrentHashMap<>();
    private final Map<UUID, List<Long>> messageLogs = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    
    public final Set<UUID> msgToggled = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public boolean chatEnabled = true;

    public YamlConfiguration msgConfig;
    public SoundManager soundManager;
    public static final LegacyComponentSerializer HEX_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&').hexCharacter('#').hexColors().useUnusualXRepeatedCharacterHexFormat().build();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMessagesConfig();
        this.soundManager = new SoundManager(this);
        
        getCommand("arischat").setExecutor(this);
        getCommand("chattoggle").setExecutor(this);
        
        MsgCommand msgCmd = new MsgCommand(this);
        getCommand("msg").setExecutor(msgCmd);
        getCommand("msgtoggle").setExecutor(msgCmd);
        
        getServer().getPluginManager().registerEvents(this, this);
    }

    public void loadMessagesConfig() {
        reloadConfig();
        File msgFile = new File(getDataFolder(), "messages.yml");
        if (!msgFile.exists()) saveResource("messages.yml", false);
        msgConfig = YamlConfiguration.loadConfiguration(msgFile);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("arischat") && args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("arischat.admin")) {
                sender.sendMessage(HEX_SERIALIZER.deserialize(msgConfig.getString("no-permission")));
                return true;
            }
            loadMessagesConfig();
            sender.sendMessage(HEX_SERIALIZER.deserialize(msgConfig.getString("reload-success")));
            return true;
        }

        if (label.equalsIgnoreCase("chattoggle")) {
            chatEnabled = !chatEnabled;
            String status = chatEnabled ? msgConfig.getString("chat-on") : msgConfig.getString("chat-off");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (getConfig().getBoolean("chat-toggle.chat")) p.sendMessage(HEX_SERIALIZER.deserialize(status));
                if (getConfig().getBoolean("chat-toggle.actionbar")) p.sendActionBar(HEX_SERIALIZER.deserialize(status));
            }
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (!chatEnabled && !p.hasPermission("arischat.admin")) {
            event.setCancelled(true);
            p.sendMessage(HEX_SERIALIZER.deserialize(msgConfig.getString("chat-is-muted")));
            return;
        }

        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (cooldowns.containsKey(uuid) && cooldowns.get(uuid) > now) {
            event.setCancelled(true);
            soundManager.playSound(p, "ratelimit-error");
            sendCooldown(p, msgConfig.getString("rate-limit-message").replace("{time}", String.valueOf((cooldowns.get(uuid) - now) / 1000 + 1)));
            return;
        }

        if (getConfig().getBoolean("ratelimit.enabled", true)) {
            List<Long> logs = messageLogs.computeIfAbsent(uuid, k -> new ArrayList<>());
            logs.removeIf(t -> (now - t) > (getConfig().getLong("ratelimit.time-window-seconds") * 1000));
            logs.add(now);
            if (logs.size() > getConfig().getInt("ratelimit.max-messages")) {
                event.setCancelled(true);
                long cd = getConfig().getLong("ratelimit.cooldown-seconds") * 1000;
                cooldowns.put(uuid, now + cd);
                soundManager.playSound(p, "ratelimit-error");
                sendCooldown(p, msgConfig.getString("rate-limit-message").replace("{time}", String.valueOf(cd / 1000)));
                return;
            }
        }

        String msgRaw = LegacyComponentSerializer.legacyAmpersand().serialize(event.message());
        
        msgRaw = handleMentions(msgRaw);

        if (getConfig().getBoolean("small-caps", true)) msgRaw = toSmallCaps(msgRaw);

        if (getConfig().getBoolean("antispam", true) && msgRaw.equalsIgnoreCase(lastMessage.get(uuid))) {
            long left = (lastTime.getOrDefault(uuid, 0L) + 15000) - now;
            if (left > 0) {
                event.setCancelled(true);
                soundManager.playSound(p, "spam-error");
                sendCooldown(p, msgConfig.getString("anti-spam-message").replace("{time}", String.valueOf(left / 1000 + 1)));
                return;
            }
        }
        
        lastMessage.put(uuid, msgRaw);
        lastTime.put(uuid, now);
        event.setCancelled(true);

        String statsRaw = PlaceholderAPI.setPlaceholders(p, String.join("\n", getConfig().getStringList("stats")).replace("{player}", p.getName()));
        Component hover = HEX_SERIALIZER.deserialize(statsRaw);
        String nameRank = PlaceholderAPI.setPlaceholders(p, "%luckperms_prefix%" + p.getName() + "%luckperms_suffix%");
        
        Component finalComp = HEX_SERIALIZER.deserialize(PlaceholderAPI.setPlaceholders(p, getConfig().getString("chat-format")))
                .replaceText(TextReplacementConfig.builder().match("\\{name\\}").replacement(HEX_SERIALIZER.deserialize(nameRank)).build())
                .replaceText(TextReplacementConfig.builder().match("\\{message\\}").replacement(HEX_SERIALIZER.deserialize(msgRaw)).build())
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.suggestCommand("/msg " + p.getName() + " "));

        Bukkit.broadcast(finalComp);
    }

    private String handleMentions(String message) {
        String color = getConfig().getString("mention-color", "&#facc15");
        for (Player online : Bukkit.getOnlinePlayers()) {
            String name = online.getName();
            if (message.contains(name)) {
                message = message.replace(name, color + name + "&r");
            }
        }
        return message;
    }

    public String toSmallCaps(String input) {
        String n = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String s = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀsᴛᴜᴠᴡxʏᴢ0123456789";
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            int i = n.indexOf(c);
            sb.append(i != -1 ? s.charAt(i) : c);
        }
        return sb.toString();
    }

    private void sendCooldown(Player p, String r) {
        Component c = HEX_SERIALIZER.deserialize(r);
        if (getConfig().getBoolean("anti-spam-chat")) p.sendMessage(c);
        if (getConfig().getBoolean("anti-spam-actionbar")) p.sendActionBar(c);
    }
  }
