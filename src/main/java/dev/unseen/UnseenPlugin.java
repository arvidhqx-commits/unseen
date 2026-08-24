package dev.unseen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UnseenPlugin extends JavaPlugin implements Listener {

    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    private File stateFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        stateFile = new File(getDataFolder(), "state.yml");
        loadState();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::actionBarTick, 40L, 40L);
        // Re-apply hiding for anyone already online (e.g. /reload or plugin update).
        for (Player p : getServer().getOnlinePlayers()) {
            if (vanished.contains(p.getUniqueId())) hide(p);
        }
        getLogger().info("Unseen " + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        saveState();
    }

    private void loadState() {
        vanished.clear();
        if (!stateFile.exists()) return;
        for (String s : YamlConfiguration.loadConfiguration(stateFile).getStringList("vanished")) {
            try {
                vanished.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveState() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("vanished", vanished.stream().map(UUID::toString).toList());
        try {
            yml.save(stateFile);
        } catch (IOException e) {
            getLogger().warning("Could not save state.yml: " + e.getMessage());
        }
    }

    private void actionBarTick() {
        String bar = getConfig().getString("actionbar", "");
        if (bar.isEmpty()) return;
        Component c = Fmt.parse(bar);
        for (Player p : getServer().getOnlinePlayers()) {
            if (vanished.contains(p.getUniqueId())) p.sendActionBar(c);
        }
    }

    private void hide(Player target) {
        for (Player viewer : getServer().getOnlinePlayers()) {
            if (!viewer.equals(target) && !viewer.hasPermission("unseen.see")) {
                viewer.hidePlayer(this, target);
            }
        }
        if (getConfig().getBoolean("no-pickup", true)) target.setCanPickupItems(false);
        if (getConfig().getBoolean("invulnerable", true)) target.setInvulnerable(true);
        target.setSleepingIgnored(true);
    }

    private void show(Player target) {
        for (Player viewer : getServer().getOnlinePlayers()) {
            viewer.showPlayer(this, target);
        }
        target.setCanPickupItems(true);
        target.setInvulnerable(false);
        target.setSleepingIgnored(false);
    }

    public boolean isVanished(UUID id) {
        return vanished.contains(id);
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("[Unseen] Player-only command.", NamedTextColor.RED));
            return true;
        }
        if (vanished.remove(player.getUniqueId())) {
            show(player);
            player.sendMessage(Fmt.parse(getConfig().getString("messages.visible", "Visible again.")));
            if (getConfig().getBoolean("fake-quit-join-messages", true)) {
                broadcastFake(player, true);
            }
        } else {
            vanished.add(player.getUniqueId());
            hide(player);
            player.sendMessage(Fmt.parse(getConfig().getString("messages.vanished", "Vanished.")));
            if (getConfig().getBoolean("fake-quit-join-messages", true)) {
                broadcastFake(player, false);
            }
        }
        saveState();
        return true;
    }

    /** Sends a vanilla-style join/leave line to players who cannot see the vanished player. */
    private void broadcastFake(Player about, boolean join) {
        Component line = Component.translatable(
                join ? "multiplayer.player.joined" : "multiplayer.player.left",
                NamedTextColor.YELLOW, Component.text(about.getName()));
        for (Player viewer : getServer().getOnlinePlayers()) {
            if (!viewer.equals(about) && !viewer.hasPermission("unseen.see")) {
                viewer.sendMessage(line);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        // Hide already-vanished players from the newcomer.
        for (Player p : getServer().getOnlinePlayers()) {
            if (vanished.contains(p.getUniqueId()) && !joining.hasPermission("unseen.see")
                    && !joining.equals(p)) {
                joining.hidePlayer(this, p);
            }
        }
        if (vanished.contains(joining.getUniqueId())) {
            hide(joining);
            if (getConfig().getBoolean("silent-join-quit", true)) event.joinMessage(null);
            joining.sendMessage(Fmt.parse(getConfig().getString("messages.vanished", "Vanished.")));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (vanished.contains(event.getPlayer().getUniqueId())
                && getConfig().getBoolean("silent-join-quit", true)) {
            event.quitMessage(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (!getConfig().getBoolean("no-mob-target", true)) return;
        if (event.getTarget() instanceof Player p && vanished.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!getConfig().getBoolean("no-pickup", true)) return;
        if (event.getEntity() instanceof Player p && vanished.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!getConfig().getBoolean("invulnerable", true)) return;
        if (event.getEntity() instanceof Player p && vanished.contains(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
