package me.aris.arischathover;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundManager {
    private final ArisChatHover plugin;

    public SoundManager(ArisChatHover plugin) {
        this.plugin = plugin;
    }

    public void playSound(Player player, String configPath) {
        String soundName = plugin.getConfig().getString("sounds." + configPath);
        if (soundName == null || soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException ignored) {}
    }
}
