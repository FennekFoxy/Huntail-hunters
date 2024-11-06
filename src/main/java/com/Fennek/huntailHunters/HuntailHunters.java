package com.Fennek.huntailHunters;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HuntailHunters extends JavaPlugin implements Listener {

    // Stores the arenas with their names and locations
    private HashMap<String, Arena> arenas = new HashMap<>();
    // Player statistics
    private HashMap<UUID, PlayerStats> playerStats = new HashMap<>();
    // Stats file
    private File statsFile;
    private FileConfiguration statsConfig;
    // Config file
    private FileConfiguration config;
    // Set of players who have joined the game
    private Set<UUID> joinedPlayers = new HashSet<>();
    // Static instance for accessing this plugin
    private static HuntailHunters instance;
    // Sets if there is an active game
    private boolean activeGame = false;

    @Override
    public void onEnable() {

        //Set instance to this plugin
        instance = this;

        // Save default config if it does not exist
        saveDefaultConfig();
        config = getConfig();

        // Create and load Stats.yml
        statsFile = new File(getDataFolder(), "Stats.yml");
        if (!statsFile.exists()) {
            try {
                if (statsFile.createNewFile()) {
                    getLogger().info("Stats.yml created successfully");
                }
            } catch (IOException e) {
                getLogger().severe("Could not create Stats.yml");
                e.printStackTrace();
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);

        // Register event listener to handle player-related events
        Bukkit.getPluginManager().registerEvents(this, this);

        // Register commands for managing the arenas and game logic
        getCommand("huntailhunters").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                    sender.sendMessage("§6HuntailHunters Commands:");
                    sender.sendMessage("§e/huntailhunters join §7- Join the current game.");
                    sender.sendMessage("§e/huntailhunters create <x1> <y1> <z1> <x2> <y2> <z2> <name> §7- Create an arena with specified coordinates and name.");
                    sender.sendMessage("§e/huntailhunters spawn <arena> <x> <y> <z> §7- Set the spawn point for the specified arena.");
                    sender.sendMessage("§e/huntailhunters start <arena> <true/false> §7- Start a game in the specified arena with or without power-ups.");
                    sender.sendMessage("§e/huntailhunters stop <arena> §7- Stop the current game in the specified arena.");
                    sender.sendMessage("§e/huntailhunters delete <arena> §7- Delete the specified arena.");
                    sender.sendMessage("§e/huntailhunters stats <IGN> §7- Show statistics for the specified player, or yourself if not specified.");
                    sender.sendMessage("§e/huntailhunters round <arena> <round_number> start <true/false> §7- Start a round in the specified arena with a given round number and power-ups.");
                    sender.sendMessage("§e/huntailhunters powerup <arena> <x> <y> <z> <powerup_name> §7- Add a power-up location to the specified arena.");

                    return true;
                }

                // Handle join command
                if (args[0].equalsIgnoreCase("join")) {
                    if (sender instanceof Player) {
                        Player player = (Player) sender;
                        if (player.hasPermission("huntailhunters.join")) {
                            addPlayerToJoinList(player);
                            return true;
                        } else {
                            player.sendMessage("You do not have permission to join the game.");
                            return true;
                        }
                    } else {
                        sender.sendMessage("Only players can join the game.");
                        return true;
                    }
                }

                // Handle create arena command
                if (args[0].equalsIgnoreCase("create")) {
                    if (args.length < 8) {
                        sender.sendMessage("Usage: /huntailhunters create <x1> <y1> <z1> <x2> <y2> <z2> <name>");
                        return true;
                    }
                    try {
                        String name = args[7];
                        Location loc1 = new Location(Bukkit.getWorld("world"),+ Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                        Location loc2 = new Location(Bukkit.getWorld("world"), Integer.parseInt(args[4]), Integer.parseInt(args[5]), Integer.parseInt(args[6]));
                        createArena(name, loc1, loc2);
                        sender.sendMessage("Arena " + name + " created successfully.");
                        return true;
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Coordinates must be a number.");
                        return true;
                    }
                }

                // Handle spawn point command
                if (args[0].equalsIgnoreCase("spawn")) {
                    if (args.length < 5) {
                        sender.sendMessage("Usage: /huntailhunters spawn <arena> <x> <y> <z>");
                        return true;
                    }
                    String arenaName = args[1];
                    if (!arenas.containsKey(arenaName)) {
                        sender.sendMessage("Arena " + arenaName + " does not exist.");
                        return true;
                    }
                    try {
                        double x = Double.parseDouble(args[2]);
                        double y = Double.parseDouble(args[3]);
                        double z = Double.parseDouble(args[4]);
                        Location newSpawn = new Location(Bukkit.getWorld("world"), x, y, z);
                        arenas.get(arenaName).setSpawnPoint(newSpawn);
                        saveArenas(); // Save updated arena spawn point to config
                        sender.sendMessage("Spawn point for arena " + arenaName + " set to (" + x + ", " + y + ", " + z + ").");
                        return true;
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Coordinates must be numbers.");
                        return true;
                    }
                }

                // Handle power-up command
                if (args[0].equalsIgnoreCase("powerup")) {
                    if (args.length < 6) {
                        sender.sendMessage("Usage: /huntailhunters powerup <arena> <x> <y> <z> <powerup_name>");
                        return true;
                    }
                    String arenaName = args[1];
                    if (!arenas.containsKey(arenaName)) {
                        sender.sendMessage("Arena " + arenaName + " does not exist.");
                        return true;
                    }
                    try {
                        double x = Double.parseDouble(args[2]);
                        double y = Double.parseDouble(args[3]);
                        double z = Double.parseDouble(args[4]);
                        String powerup_name = args[5];
                        Location powerUpLocation = new Location(Bukkit.getWorld("world"), x, y, z);
                        addPowerUpLocation(arenaName, powerUpLocation, powerup_name);
                        sender.sendMessage("Power-up location of " + powerup_name + " added to arena " + arenaName + ".");
                        return true;
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Coordinates must be numbers.");
                        return true;
                    }
                }

                // Handle start game command
                if (args[0].equalsIgnoreCase("start")) {
                    if (args.length < 2) {
                        sender.sendMessage("Usage: /huntailhunters start <arena>");
                        return true;
                    }
                    String arenaName = args[1];
                    // Check if the arena exists
                    if (!arenas.containsKey(arenaName)) {
                        sender.sendMessage("Arena " + arenaName + " does not exist.");
                        return true;
                    }
                    announceMessage(config.getString("messages.game_starting", "The game has started! Good luck to all players."));
                    if (arenas.containsKey(arenaName)) {
                        Arena arena = arenas.get(arenaName); // Get the arena object
                        // Teleport all joined players to the arena and give them a bow and special arrow
                        for (UUID playerUUID : joinedPlayers) {
                            Player player = Bukkit.getPlayer(playerUUID);
                            if (player != null) {
                                player.teleport(arena.getSpawnPoint()); // Teleport the player to the arena
                            }
                        }
                    }
                    return true;
                }

                if (args[0].equalsIgnoreCase("round")) {
                    if (args.length < 3 ) {
                        sender.sendMessage("Usage: /huntailhunters round <arena> <round_number> <true/false>");
                        return true;
                    }
                    String arenaName = args[1];
                    if (!arenas.containsKey(arenaName)) {
                        sender.sendMessage("Arena " + arenaName + " does not exist.");
                        return true;
                    }
                    int roundNumber;
                    try {
                        roundNumber = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Round number must be an integer.");
                        return true;
                    }
                    boolean powerUps = Boolean.parseBoolean(args[3].trim());
                    startGame(arenaName, powerUps);
                    announceMessage(config.getString("messages.round_start", "Round {round} has begun! Good luck to all players.").replace("{round}", String.valueOf(roundNumber)));
                    if (!powerUps){
                        announceMessage(config.getString("messages.power_ups_disabled", "Power-ups are disabled for this round."));
                    }else if (powerUps){
                        announceMessage(config.getString("messages.power_ups_enabled", "Power-ups are enabled for this round."));
                    }
                    for (UUID playerUUID : joinedPlayers) {
                        Player player = Bukkit.getPlayer(playerUUID);
                        if (player != null) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 200, 9));
                        }
                    }
                    return true;
                }

                // Handle stop game command
                if (args[0].equalsIgnoreCase("stop")) {
                    if (args.length < 2) {
                        sender.sendMessage("Usage: /huntailhunters stop <arena>");
                        return true;
                    }
                    String arenaName = args[1];
                    stopGame(arenaName);
                    return true;
                }

                // Handle delete arena command
                if (args[0].equalsIgnoreCase("delete")) {
                    if (args.length < 2) {
                        sender.sendMessage("Usage: /huntailhunters delete <arena>");
                        return true;
                    }
                    String arenaName = args[1];
                    // Send confirmation message with clickable options
                    TextComponent confirmMessage = new TextComponent("Are you sure you want to delete the arena " + arenaName + "? ");
                    TextComponent confirmButton = new TextComponent("[Confirm]");
                    confirmButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                    confirmButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/huntailhunters confirmdelete " + arenaName));
                    TextComponent declineButton = new TextComponent(" [Decline]");
                    declineButton.setColor(net.md_5.bungee.api.ChatColor.RED);
                    declineButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/huntailhunters declinedelete"));
                    confirmMessage.addExtra(confirmButton);
                    confirmMessage.addExtra(declineButton);
                    sender.spigot().sendMessage(confirmMessage);
                    return true;

                }
                // Handle confirm delete command
                if (args[0].equalsIgnoreCase("confirmdelete")) {
                    if (args.length < 2) {
                        sender.sendMessage("Usage: /huntailhunters confirmdelete <arena>");
                        return true;
                    }
                    String arenaName = args[1];
                    if (arenas.containsKey(arenaName)) {
                        deleteArena(arenaName);
                        announceMessage(config.getString("messages.delete_confirmed", "Arena {arena} has been deleted successfully.").replace("{arena}", arenaName));
                    } else {
                        sender.sendMessage("Arena " + arenaName + " does not exist.");
                    }
                    return true;
                }

                // Handle decline delete command
                if (args[0].equalsIgnoreCase("declinedelete")) {
                    sender.sendMessage(config.getString("messages.delete_cancelled", "Arena deletion has been cancelled."));
                    return true;
                }


                // Handle stats command
                if (args[0].equalsIgnoreCase("stats")) {
                    if (args.length < 2) {
                        if (sender instanceof Player) {
                            Player player = (Player) sender;
                            showPlayerStats(sender, player.getName());
                            return true;
                        } else {
                            sender.sendMessage("Usage: /huntailhunters stats <IGN>");
                            return true;
                        }
                    }
                    String playerName = args[1];
                    showPlayerStats(sender, playerName);
                    return true;
                }
                return false;
            }
        });

        // Load any saved arenas or player stats from config
        loadArenas();
        loadPlayerStats();

        // Log loaded successfully message
        getLogger().info("HuntailHunters (version " + getDescription().getVersion() + ") loaded successfully");
    }

    @Override
    public void onDisable() {
        // Save arenas and player stats to config
        saveArenas();
        savePlayerStats();
    }
    // Utility method to create and give an Event Arrow to a player
    private void giveEventArrow(Player player) {
        ItemStack arrow = new ItemStack(Material.ARROW, 1);
        ItemMeta arrowMeta = arrow.getItemMeta();
        if (arrowMeta != null) {
            arrowMeta.setDisplayName("§5Event Arrow");
            arrow.setItemMeta(arrowMeta);
        }
        player.getInventory().addItem(arrow);
    }
    // Method to get the instance of this plugin
    public static HuntailHunters getInstance() {
        return instance;
    }

    private void addPowerUpLocation(String arenaName, Location loc, String powerType) {
        String path = "arenas." + arenaName + ".powerups";
        ConfigurationSection powerUpSection = config.getConfigurationSection(path);

        if (powerUpSection == null) {
            powerUpSection = config.createSection(path);
        }

        String locationKey = powerType; // Unique key for each power-up
        powerUpSection.set(locationKey + ".location", serializeLocation(loc));

        saveConfig();
    }



    public void startPowerUpSpawnTask(String arenaName) {
        if (!arenas.containsKey(arenaName)) return;

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (activeGame) {
                String path = "arenas." + arenaName + ".powerups";
                ConfigurationSection powerUpSection = config.getConfigurationSection(path);

                if (powerUpSection != null) {
                    Set<String> keys = powerUpSection.getKeys(false);
                    if (!keys.isEmpty()) {
                        String randomKey = new ArrayList<>(keys).get(new Random().nextInt(keys.size()));
                        String locationString = powerUpSection.getString(randomKey + ".location");
                        String powerType = powerUpSection.getString(randomKey + ".type");

                        Location location = deserializeLocation(locationString);


                        ItemStack powerUpItem = new ItemStack(Material.POTION);
                        ItemMeta meta = powerUpItem.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName("§6Power-Up");
                            List<String> lore = new ArrayList<>();
                            lore.add("§dThis power-up grants a random bonus.");
                            meta.setLore(lore);
                            powerUpItem.setItemMeta(meta);
                        }
                        location.getWorld().dropItem(location, powerUpItem);

                        // Announce the power-up
                        announceMessage(config.getString("messages.power_up_spawned", "A Power-up has spawned nearby!"));
                    }
                }
            }
        }, 0L, 300L); // 300 ticks = 15 seconds
    }

    // Event listener for when a player consumes a power-up potion
    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        ItemStack consumedItem = event.getItem();
        ItemMeta meta = consumedItem.getItemMeta();
        if(activeGame){
            if (meta != null && meta.hasDisplayName() && meta.getDisplayName().equals("§6Power-Up")) {
                Player player = event.getPlayer();
                Random random = new Random();
                int number = random.nextInt(3);

                if (number == 0){
                    int duration = config.getInt("power_ups.speed.Duration", 600);
                    int amplifier = config.getInt("power_ups.speed.Amplifier", 1);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier));
                    player.sendMessage("§6You have been given a §l§bSPEED BOOST§f§6!§f");
                }else if (number == 1 ){
                    int duration = config.getInt("power_ups.speed.Duration", 600);
                    int amplifier = config.getInt("power_ups.speed.Amplifier", 1);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, duration, amplifier));
                    player.sendMessage("§6You have been given a §l§aJUMP BOOST§f§6!§f");
                }else if (number == 2){
                    giveEventArrow(player);
                    player.sendMessage("§6You have been given an §l§dEXTRA ARROW§f§6!§f");
                }
            }
         }
    }

    // Handling player death event to give arrows
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killed = event.getEntity(); // The player who was killed
        Player killer = killed.getKiller(); // The player who killed the other player

        // Suppress death messages for players killed in the game
        if (activeGame && joinedPlayers.contains(event.getEntity().getUniqueId())) {
            event.setDeathMessage(null);
        }
        // Check if the killer is not null (i.e., another player killed them) and if the game is active in that world
        if (killer != null && activeGame && joinedPlayers.size() != 1) {
            // Give the killer a new event arrow
            ItemStack arrow = new ItemStack(Material.ARROW, 1);
            ItemMeta arrowMeta = arrow.getItemMeta();
            ItemStack sword = new ItemStack(Material.IRON_SWORD, 1);
            ItemMeta swordMeta = sword.getItemMeta();
            if (arrowMeta != null) {
                arrowMeta.setDisplayName("§5Event Arrow");
                arrow.setItemMeta(arrowMeta);
            } else if (swordMeta != null) {
                swordMeta.setDisplayName("§5Event Sword");
                sword.setItemMeta(swordMeta);

            }
            killer.getInventory().addItem(arrow); // Give the killer a special arrow
        }

        // Remove the killed player from the joined players set
        joinedPlayers.remove(killed.getUniqueId());

        // Check if there is only one player left in the game
        if (joinedPlayers.size() == 1) {
            UUID winnerUUID = joinedPlayers.iterator().next();
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                announceMessage(config.getString("messages.winner_announcement", "{winner} is the winner!").replace("{winner}", winner.getName()));
                activeGame = false;
                // Launch a small firework effect at the winner's location
                Location winnerLocation = winner.getLocation();
                winnerLocation.getWorld().spawnEntity(winnerLocation, org.bukkit.entity.EntityType.FIREWORK);
                // Update the player's win stats
                playerStats.putIfAbsent(winnerUUID, new PlayerStats(0));
                playerStats.get(winnerUUID).addWin();
                savePlayerStats();
            }
        }
    }

    // Method for creating an arena
    public void createArena(String name, Location loc1, Location loc2) {
        // Create a new arena with the given name and locations
        arenas.put(name, new Arena(name, loc1, loc2));
        saveArenas(); // Save arenas to config
    }

    // Method for starting a game
    public void startGame(String arenaName, boolean powerUps) {
        // Check if the specified arena exists
        if (arenas.containsKey(arenaName)) {
            Arena arena = arenas.get(arenaName); // Get the arena object
            activeGame = true;
            if (powerUps){
                startPowerUpSpawnTask(arenaName);
            }

            // Teleport all joined players to the arena and give them a bow and special arrow
            for (UUID playerUUID : joinedPlayers) {
                Player player = Bukkit.getPlayer(playerUUID);
                if (player != null) {
                     // Check if player already has the Event Sword
                    boolean hasSword = player.getInventory().contains(Material.IRON_SWORD, 1);
                    if (!hasSword) {
                        ItemStack sword = new ItemStack(Material.IRON_SWORD, 1);
                        ItemMeta swordMeta = sword.getItemMeta();
                        if (swordMeta != null) {
                            swordMeta.setDisplayName("§5Event Sword");
                            swordMeta.setUnbreakable(true);
                            sword.setItemMeta(swordMeta);
                        } // Use display name to identify event bow
                        player.getInventory().addItem(sword);
                    }// Give the player a bow

                    // Check if player already has the Event Bow
                    boolean hasBow = player.getInventory().contains(Material.BOW, 1);
                    if (!hasBow) {
                        ItemStack bow = new ItemStack(Material.BOW, 1);
                        ItemMeta bowMeta = bow.getItemMeta();
                        if (bowMeta != null) {
                            bowMeta.setDisplayName("§5Event Bow");
                            bowMeta.setUnbreakable(true);
                            bow.setItemMeta(bowMeta);
                            bow.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 25);
                        } // Use display name to identify event bow
                        player.getInventory().addItem(bow);
                    }// Give the player a bow

                    // Check if player already has an Event Arrow
                    boolean hasArrow = player.getInventory().contains(Material.ARROW, 1) || player.getInventory().getItemInOffHand().getType() == Material.ARROW;
                    if (!hasArrow) {
                        giveEventArrow(player);
                    }// Give the player a special arrow
                }
            }
        }
    }

    // Event listener to ensure bows can only shoot special event arrows
    @EventHandler
    public void onPlayerShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            ItemStack arrowItem = event.getConsumable();
            if (activeGame) {
                if (arrowItem != null && arrowItem.getItemMeta() != null) {
                    String displayName = arrowItem.getItemMeta().getDisplayName();
                    if (!"§5Event Arrow".equals(displayName)) {
                        event.setCancelled(true);
                        player.sendMessage("You can only use the special event arrows!");
                    } else {
                        if (event.getProjectile() instanceof Arrow) {
                            ((Arrow) event.getProjectile()).setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
                        }
                    }
                }
            }
        }
    }

    // Method to stop a game
    public void stopGame(String arenaName) {
        if (activeGame) {
            announceMessage(config.getString("messages.game_forced_stop"));
            activeGame = false;
        }
    }

    // Method to delete an arena
    public void deleteArena(String name) {
        if (arenas.containsKey(name)) {
            arenas.remove(name);
            saveArenas(); // Save arenas to config after deletion
            getLogger().info("Arena " + name + " has been deleted.");
        } else {
            getLogger().warning("Arena " + name + " does not exist.");
        }
    }

    // Method to load player stats from Stats.yml
    private void loadPlayerStats() {
        for (String key : statsConfig.getKeys(false)) {
            UUID playerUUID = UUID.fromString(key);
            int wins = statsConfig.getInt(key + ".wins");
            playerStats.put(playerUUID, new PlayerStats(wins));
        }
    }

    // Method to save player stats to Stats.yml
    private void savePlayerStats() {
        for (UUID playerUUID : playerStats.keySet()) {
            PlayerStats stats = playerStats.get(playerUUID);
            statsConfig.set(playerUUID.toString() + ".wins", stats.getWins());
        }
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            getLogger().severe("Could not save Stats.yml");
            e.printStackTrace();
        }
    }

    // Method to serialize a Location object to a string
    private String serializeLocation(Location loc) {
        if (loc == null) {
            return null;
        }
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
    }

    // Method to deserialize a Location object from a string
    private Location deserializeLocation(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        String[] parts = str.split(",");
        if (parts.length != 4) {
            return null;
        }
        return new Location(Bukkit.getWorld(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
    }

    // Method to load arenas from config.yml
    private void loadArenas() {
        if (config.contains("arenas")) {
            for (String key : config.getConfigurationSection("arenas").getKeys(false)) {
                String path = "arenas." + key;
                String name = config.getString(path + ".name");
                Location loc1 = deserializeLocation(config.getString(path + ".loc1"));
                Location loc2 = deserializeLocation(config.getString(path + ".loc2"));
                Location spawn = deserializeLocation(config.getString(path + ".spawn", serializeLocation(loc1)));
                Arena arena = new Arena(name, loc1, loc2);
                arena.setSpawnPoint(spawn);
                arenas.put(name, arena);
            }
        }
    }

    // Method to save arenas to config.yml
    private void saveArenas() {
        for (String key : arenas.keySet()) {
            Arena arena = arenas.get(key);
            String path = "arenas." + key;
            config.set(path + ".name", arena.getName());
            config.set(path + ".loc1", serializeLocation(arena.getLocation1()));
            config.set(path + ".loc2", serializeLocation(arena.getLocation2()));
            config.set(path + ".spawn", serializeLocation(arena.getSpawnPoint()));
        }
        saveConfig();
    }


    // Utility method to announce a message to all players
    private void announceMessage(String message) {
        Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', message));
    }

    // Method to add a player to the joined list
    public void addPlayerToJoinList(Player player) {
        joinedPlayers.add(player.getUniqueId());
        player.sendMessage("You have joined the queue for Huntail Hunters!");
        player.sendMessage(config.getString("messages.check_inventory"));
    }

    // Method to display player statistics
    public void showPlayerStats(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayer(playerName);
        if (player != null && playerStats.containsKey(player.getUniqueId())) {
            PlayerStats stats = playerStats.get(player.getUniqueId());
            sender.sendMessage(player.getName() + " has " + stats.getWins() + " win(s).");
        } else {
            sender.sendMessage("Player not found or no stats available.");
        }
    }
}

// Placeholder class for Arena
class Arena {
    private String name; // Name of the arena
    private Location loc1; // First corner of the arena
    private Location loc2; // Opposite corner of the arena
    private Location spawnPoint; // Spawn point for the arena

    public Arena(String name, Location loc1, Location loc2) {
        this.name = name;
        this.loc1 = loc1;
        this.loc2 = loc2;
        this.spawnPoint = loc1;
    }

    public String getName() {
        return name;
    }

    public Location getLocation1() {return loc1;}

    public Location getLocation2() {
        return loc2;
    }

    public Location getSpawnPoint(){ return spawnPoint;}

    public void setSpawnPoint(Location spawnPoint){ this.spawnPoint = spawnPoint;}
}


class PlayerStats {
    private int wins; // Number of wins the player has

    public PlayerStats(int wins) {
        this.wins = wins;
    }

    public int getWins() {
        return wins;
    }

    public void addWin() {
        this.wins++;
    }
}
