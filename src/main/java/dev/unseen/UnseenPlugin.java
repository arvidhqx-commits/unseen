package dev.unseen;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UnseenPlugin extends JavaPlugin implements Listener {

    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();
    /** What Unseen itself switched on — only these get switched off again. */
    private final Set<UUID> ownedInvulnerable = ConcurrentHashMap.newKeySet();
    private final Set<UUID> ownedNoPickup = ConcurrentHashMap.newKeySet();
    private final Set<UUID> ownedSleepingIgnored = ConcurrentHashMap.newKeySet();
    private File stateFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        stateFile = new File(getDataFolder(), "state.yml");
        loadState();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, (Runnable) () -> actionBarTick(getServer().getOnlinePlayers()), 40L, 40L);
        // Re-apply hiding for anyone already online (e.g. /reload or plugin update).
        for (Player p : getServer().getOnlinePlayers()) {
            if (vanished.contains(p.getUniqueId())) hide(p);
        }
        getLogger().info("Unseen " + getDescription().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        saveState();
        restoreAll(getServer().getOnlinePlayers());
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

    /** Seam for the prober: the viewer list is a parameter, so no real players need to be online. */
    void actionBarTick(Collection<? extends Player> online) {
        String bar = getConfig().getString("actionbar", "");
        if (bar == null || bar.isEmpty()) return;
        Component c = Fmt.parse(bar);
        for (Player p : online) {
            if (vanished.contains(p.getUniqueId())) p.sendActionBar(c);
        }
    }

    private void hide(Player target) {
        hideFrom(target, getServer().getOnlinePlayers());
        applyVanishState(target);
    }

    private void show(Player target) {
        showTo(target, getServer().getOnlinePlayers());
        restoreVanishState(target);
    }

    /** Hides {@code target} from every viewer who is not allowed to see vanished players. */
    void hideFrom(Player target, Collection<? extends Player> viewers) {
        for (Player viewer : viewers) {
            if (!viewer.equals(target) && !viewer.hasPermission("unseen.see")) {
                viewer.hidePlayer(this, target);
            }
        }
    }

    /** Reveals {@code target} to every viewer again. */
    void showTo(Player target, Collection<? extends Player> viewers) {
        for (Player viewer : viewers) {
            viewer.showPlayer(this, target);
        }
    }

    /**
     * Applies the server-side side effects of being vanished — and remembers which of them
     * Unseen actually switched on. Without that memory, unvanishing would clear god mode a
     * creative plugin had set, or an AFK plugin's sleeping-ignored flag (found 07.09.2026).
     */
    void applyVanishState(Player target) {
        UUID id = target.getUniqueId();
        if (getConfig().getBoolean("no-pickup", true) && target.getCanPickupItems()) {
            target.setCanPickupItems(false);
            ownedNoPickup.add(id);
        }
        if (getConfig().getBoolean("invulnerable", true) && !target.isInvulnerable()) {
            target.setInvulnerable(true);
            ownedInvulnerable.add(id);
        }
        if (!target.isSleepingIgnored()) {
            target.setSleepingIgnored(true);
            ownedSleepingIgnored.add(id);
        }
    }

    /** Takes back exactly those side effects Unseen switched on itself — nothing else. */
    void restoreVanishState(Player target) {
        UUID id = target.getUniqueId();
        if (ownedNoPickup.remove(id)) target.setCanPickupItems(true);
        if (ownedInvulnerable.remove(id)) target.setInvulnerable(false);
        if (ownedSleepingIgnored.remove(id)) target.setSleepingIgnored(false);
    }

    /**
     * Clears the target of every mob in {@code around} that is currently hunting {@code id}.
     * Cancelling {@link EntityTargetEvent} only stops NEW targeting; a mob that already locked
     * on keeps chasing an invisible player forever (found 07.09.2026).
     */
    int clearMobTargets(UUID id, Collection<? extends Entity> around) {
        int cleared = 0;
        for (Entity e : around) {
            if (e instanceof Mob mob) {
                LivingEntity t = mob.getTarget();
                if (t != null && t.getUniqueId().equals(id)) {
                    mob.setTarget(null);
                    cleared++;
                }
            }
        }
        return cleared;
    }

    /**
     * Undoes everything Unseen did to players still online — called when the plugin shuts down.
     * Hidden players and the invulnerability flag are NOT cleaned up by Bukkit: without this,
     * removing the plugin left a staff member invisible and immortal (found 07.09.2026).
     * The vanish list itself is kept, so a restart puts them back into vanish.
     */
    void restoreAll(Collection<? extends Player> online) {
        for (Player p : online) {
            if (vanished.contains(p.getUniqueId())) {
                showTo(p, online);
                restoreVanishState(p);
            }
        }
    }

    public boolean isVanished(UUID id) {
        return vanished.contains(id);
    }

    boolean shouldCancelTarget(UUID id) {
        return getConfig().getBoolean("no-mob-target", true) && vanished.contains(id);
    }

    boolean shouldCancelPickup(UUID id) {
        return getConfig().getBoolean("no-pickup", true) && vanished.contains(id);
    }

    boolean shouldCancelDamage(UUID id) {
        return getConfig().getBoolean("invulnerable", true) && vanished.contains(id);
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command cmd,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("[Unseen] Player-only command.", NamedTextColor.RED));
            return true;
        }
        toggle(player, getServer().getOnlinePlayers());
        return true;
    }

    /** Flips the vanish state of one player. Returns true if the player is vanished afterwards. */
    boolean toggle(Player player, Collection<? extends Player> viewers) {
        boolean nowVanished;
        if (vanished.remove(player.getUniqueId())) {
            showTo(player, viewers);
            restoreVanishState(player);
            player.sendMessage(Fmt.parse(getConfig().getString("messages.visible", "Visible again.")));
            if (getConfig().getBoolean("fake-quit-join-messages", true)) {
                broadcastFake(player, true, viewers);
            }
            nowVanished = false;
        } else {
            vanished.add(player.getUniqueId());
            hideFrom(player, viewers);
            applyVanishState(player);
            forgetMobTargets(player);
            player.sendMessage(Fmt.parse(getConfig().getString("messages.vanished", "Vanished.")));
            if (getConfig().getBoolean("fake-quit-join-messages", true)) {
                broadcastFake(player, false, viewers);
            }
            nowVanished = true;
        }
        saveState();
        return nowVanished;
    }

    /** Makes mobs that already hunt this player drop him the moment he vanishes. */
    private void forgetMobTargets(Player player) {
        if (!getConfig().getBoolean("no-mob-target", true)) return;
        org.bukkit.Location loc = player.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        clearMobTargets(player.getUniqueId(), loc.getWorld().getNearbyEntities(loc, 48, 48, 48));
    }

    /** Sends a vanilla-style join/leave line to players who cannot see the vanished player. */
    void broadcastFake(Player about, boolean join, Collection<? extends Player> viewers) {
        Component line = Component.translatable(
                join ? "multiplayer.player.joined" : "multiplayer.player.left",
                NamedTextColor.YELLOW, Component.text(about.getName()));
        for (Player viewer : viewers) {
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
        if (event.getTarget() instanceof Player p && shouldCancelTarget(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && shouldCancelPickup(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && shouldCancelDamage(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
