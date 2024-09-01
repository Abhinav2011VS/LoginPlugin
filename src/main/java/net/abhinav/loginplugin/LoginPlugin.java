package net.abhinav.loginplugin;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LoginPlugin extends JavaPlugin implements Listener {

    private Location loginIslandSpawn;
    private final Map<UUID, String> playerSessions = new HashMap<>();
    private final Map<UUID, Integer> failedLoginAttempts = new HashMap<>();
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private MultiverseCore mvCore;

    @Override
    public void onEnable() {
        // Register events
        Bukkit.getPluginManager().registerEvents(this, this);

        // Load configuration
        saveDefaultConfig();
        double x = getConfig().getDouble("loginIsland.x");
        double y = getConfig().getDouble("loginIsland.y");
        double z = getConfig().getDouble("loginIsland.z");
        String worldName = getConfig().getString("loginIsland.world");

        // Initialize MultiverseCore
        mvCore = (MultiverseCore) Bukkit.getPluginManager().getPlugin("Multiverse-Core");

        if (mvCore != null) {
            MultiverseWorld mvWorld = mvCore.getMVWorldManager().getMVWorld(worldName);
            if (mvWorld != null) {
                loginIslandSpawn = new Location(mvWorld.getCBWorld(), x, y, z);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Teleport the player to the login island
        if (loginIslandSpawn != null) {
            player.teleport(loginIslandSpawn);
        }
        player.sendMessage(ChatColor.RED + "You need to log in to access the main server!");

        // Handle existing session
        if (playerSessions.containsKey(playerId)) {
            player.sendMessage(ChatColor.YELLOW + "You are logged in from another location.");
        }

        // Initialize failed login attempts
        failedLoginAttempts.putIfAbsent(playerId, 0);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clear failed login attempts on quit
        failedLoginAttempts.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // Handle login command
        if (command.startsWith("/login ")) {
            String[] args = command.split(" ");
            if (args.length == 2) {
                String password = args[1];
                handleLogin(player, password);
                event.setCancelled(true); // Prevent command from executing normally
            }
        } else if (command.startsWith("/reg ")) {
            String[] args = command.split(" ");
            if (args.length == 3) {
                String password1 = args[1];
                String password2 = args[2];
                handleRegistration(player, password1, password2);
                event.setCancelled(true);
            }
        } else if (command.startsWith("/resetpass ")) {
            String[] args = command.split(" ");
            if (args.length == 2) {
                String newPassword = args[1];
                handlePasswordReset(player, newPassword);
                event.setCancelled(true);
            }
        }
    }

    private void handleLogin(Player player, String password) {
        UUID playerId = player.getUniqueId();
        String username = player.getName().toLowerCase();

        Map<String, Object> userConfig = (Map<String, Object>) getConfig().get("users." + username);

        if (userConfig != null) {
            String storedPassword = (String) userConfig.get("password");

            if (storedPassword.equals(password)) {
                if (playerSessions.containsKey(playerId)) {
                    // Check if the player is logged in from another location
                    if (!playerSessions.get(playerId).equals(password)) {
                        player.sendMessage(ChatColor.YELLOW + "You are logged in from another location. Kicking you out.");
                        player.kickPlayer(ChatColor.RED + "You have been kicked due to a login from another location.");
                        return;
                    }
                }

                // Successful login
                playerSessions.put(playerId, password);
                moveToMainWorld(player);
                failedLoginAttempts.remove(playerId);
            } else {
                // Failed login
                int attempts = failedLoginAttempts.getOrDefault(playerId, 0) + 1;
                failedLoginAttempts.put(playerId, attempts);

                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    player.kickPlayer(ChatColor.RED + "Too many failed login attempts.");
                } else {
                    player.sendMessage(ChatColor.RED + "Incorrect password. Attempt " + attempts + "/" + MAX_FAILED_ATTEMPTS);
                }
            }
        } else {
            player.sendMessage(ChatColor.RED + "You are not registered. Please use /reg to register.");
        }
    }

    private void handleRegistration(Player player, String password1, String password2) {
        String username = player.getName().toLowerCase();

        if (!password1.equals(password2)) {
            player.sendMessage(ChatColor.RED + "Passwords do not match.");
            return;
        }

        if (getConfig().contains("users." + username)) {
            player.sendMessage(ChatColor.RED + "You are already registered.");
            return;
        }

        // Register the user and ask security questions
        getConfig().set("users." + username + ".password", password1);
        getConfig().set("users." + username + ".questions.q1", "What is your favorite color?");
        getConfig().set("users." + username + ".questions.q2", "What do you want to be?");
        getConfig().set("users." + username + ".questions.q1a", ""); // Prompt player to enter answer
        getConfig().set("users." + username + ".questions.q2a", ""); // Prompt player to enter answer
        saveConfig();

        player.sendMessage(ChatColor.GREEN + "You have been registered. Please answer the security questions.");
        player.sendMessage(ChatColor.GREEN + "1. What is your favorite color?");
        // You need to collect the answers from the player and update config
        // This can be done using additional events or a custom command handler
    }

    private void handlePasswordReset(Player player, String newPassword) {
        UUID playerId = player.getUniqueId();
        String username = player.getName().toLowerCase();

        Map<String, Object> userConfig = (Map<String, Object>) getConfig().get("users." + username);

        if (userConfig != null) {
            // Ask security questions for verification
            player.sendMessage(ChatColor.GREEN + "To reset your password, answer the following questions:");
            player.sendMessage(ChatColor.GREEN + "1. What is your favorite color?");
            // Verify answers
            player.sendMessage(ChatColor.GREEN + "2. What do you want to be?");
            // Verify answers

            // If answers are correct, update password
            getConfig().set("users." + username + ".password", newPassword);
            saveConfig();
            player.sendMessage(ChatColor.GREEN + "Your password has been reset.");
        } else {
            player.sendMessage(ChatColor.RED + "You are not registered.");
        }
    }

    private void moveToMainWorld(Player player) {
        String mainWorldName = getConfig().getString("mainWorld");
        MultiverseWorld mvWorld = mvCore.getMVWorldManager().getMVWorld(mainWorldName);
        if (mvWorld != null) {
            player.teleport(mvWorld.getCBWorld().getSpawnLocation());
        }
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location loc = player.getLocation();
        if (loc.getWorld().getName().equals(getConfig().getString("loginIsland.world"))) {
            if (loc.getY() < 0) { // Or any other threshold for the void
                player.teleport(loginIslandSpawn);
            }
        }
    }

}
