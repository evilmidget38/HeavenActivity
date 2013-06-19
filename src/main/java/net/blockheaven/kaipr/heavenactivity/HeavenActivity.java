package net.blockheaven.kaipr.heavenactivity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class HeavenActivity extends JavaPlugin {
    /**
     * Logger for messages.
     */
    protected static final Logger logger = Logger.getLogger("Minecraft.HeavenActivity");

    /**
     * Configuration
     */
    public HeavenActivityConfig config;

    /**
     * Economy via Vault
     */
    public Economy econ;

    /**
     * Data
     */
    public HeavenActivityData data;

    /**
     * Sequence update timer
     */
    public static Timer updateTimer = null;

    /**
     * The current sequence
     */
    public int currentSequence = 1;

    /**
     * Called when plugin gets enabled, initialize all the stuff we need
     */
    public void onEnable() {

        logger.info(getDescription().getName() + " "
                + getDescription().getVersion() + " enabled.");

        getDataFolder().mkdirs();

        config = new HeavenActivityConfig(this);
        data = new HeavenActivityData(this);

        data.initNewSequence();
        startUpdateTimer();
        if (!setupEconomy()) {
            getLogger().severe("Unable to find Vault and/or economy plugin.  Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getPluginManager().registerEvents(new HeavenActivityPlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new HeavenActivityBlockListener(this), this);

    }

    private boolean setupEconomy()
    {
        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
        } catch (ClassNotFoundException e) {
            return false;
        }
        RegisteredServiceProvider<Economy> economyProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            econ = economyProvider.getProvider();
        }

        return (econ != null);
    }

    /**
     * Called when the plugin gets disabled, disable timers and save stats
     */
    public void onDisable() {
        stopUpdateTimer();
    }

    /**
     * Command handling
     */
    public boolean onCommand(CommandSender sender, Command cmd,
            String commandLabel, String[] args) {

        if (args.length == 0) {
            if (hasPermission(sender, "activity.view.own")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ChatColor.RED + "[Activity] Activity is only tracked for players!");
                    return false;
                }
                final int activity = data.getActivity((Player) sender);
                sendMessage(sender, "Your current activity is: " + activityColor(activity) + activity + "%");
            } else {
                sendMessage(sender, ChatColor.RED + "You have no permission to see your activity.");
            }
        } else if (args[0].compareToIgnoreCase("list") == 0 || args[0].compareToIgnoreCase("listall") == 0) {
            if (hasPermission(sender, "activity.view.list")) {
                StringBuilder res = new StringBuilder();
                for (Player player : getServer().getOnlinePlayers()) {
                    final int activity = data.getActivity(player);
                    res.append(activityColor(activity) + player.getName() + " " + activity + "%");
                    res.append(ChatColor.GRAY + ", ");
                }
                if (res.length() > 0) {
                    sendMessage(sender, res.substring(0, res.length() - 2));
                } else {
                    sendMessage(sender, "There are no players online.");
                }
            } else {
                sendMessage(sender, ChatColor.RED + "You have no permission to see a list of online players' activity.");
            }
        } else if (args[0].compareToIgnoreCase("admin") == 0 && hasPermission(sender, "activity.admin.*")) {
            if (args.length == 1) {
                sendMessage(sender, ChatColor.RED + "/activity admin <reload>");
            } else if (args[1].compareToIgnoreCase("reload") == 0) {
                config.load();
                stopUpdateTimer();
                startUpdateTimer();
                sendMessage(sender, ChatColor.GREEN + "Reloaded");
            } else if (args[1].compareToIgnoreCase("debug") == 0) {
                if (config.debug) {
                    config.debug = false;
                    sendMessage(sender, "Debug mode turned " + ChatColor.RED + "OFF");
                } else {
                    config.debug = true;
                    sendMessage(sender, "Debug mode turned " + ChatColor.GREEN +"ON");
                }
            } else if (args[1].compareToIgnoreCase("benchmark") == 0) {
                TimerTask getting = new TimerTask() {
                    public void run() {
                        long start = System.currentTimeMillis();
                        for (int i=100000; i > 0; i--) {
                            data.getActivity("_benchmark_");
                        }
                        HeavenActivity.logger.info("100.000 x getActivity: " + String.valueOf(System.currentTimeMillis() - start) + "ms");
                    }
                };

                TimerTask setting = new TimerTask() {
                    public void run() {
                        long start = System.currentTimeMillis();
                        for (int i=100000; i > 0; i--) {
                            data.addActivity("_benchmark_", ActivitySource.MOVE, 3);
                        }
                        HeavenActivity.logger.info("1.000.000 x addActivity: " + String.valueOf(System.currentTimeMillis() - start) + "ms");
                    }
                };

                getServer().getScheduler().runTaskAsynchronously(this, getting);
                getServer().getScheduler().runTaskAsynchronously(this, setting);
            }
        } else if (args.length == 1) {
            if (hasPermission(sender, "activity.view.other")) {
                String playerName = matchSinglePlayer(sender, args[0]).getName();
                int activity = data.getActivity(playerName);
                sendMessage(sender, "Current activity of " + playerName + ": " + activityColor(activity) + activity + "%");
            } else {
                sendMessage(sender, ChatColor.RED + "You have no permission to see other's activity.");
            }
        }

        return true;

    }

    /**
     * Checks permission for a CommandSender
     * 
     * @param player
     * @param node
     * @return
     */
    public boolean hasPermission(CommandSender sender, String node) {
        if (sender instanceof ConsoleCommandSender)
            return true;
        return hasPermission((Player)sender, node);
    }

    /**
     * Checks permission for a Player
     * 
     * @param player
     * @param node
     * @return
     */
    public boolean hasPermission(Player player, String node) {
        return player.hasPermission(node); // I'd rather not change this everywhere in the code.
    }

    /**
     * Returns a cumulated multiplier set for the given player
     * 
     * @param player
     * @return
     */
    public Map<ActivitySource, Double> getCumulatedMultiplierSet(Player player) {
        Map<ActivitySource, Double> res = new HashMap<ActivitySource, Double>(ActivitySource.values().length);

        Iterator<String> multiplierSetNameIterator = config.multiplierSets.keySet().iterator();
        for (int i1 = config.multiplierSets.size(); i1 > 0; i1--) {
            final String multiplierSetName = multiplierSetNameIterator.next();

            if (!hasPermission(player, "activity.multiplier." + multiplierSetName))
                continue;

            final Map<ActivitySource, Double> multiplierSet = config.multiplierSets.get(multiplierSetName);
            Iterator<ActivitySource> sourceIterator = multiplierSet.keySet().iterator();

            for (int i2 = multiplierSet.size(); i2 > 0; i2--) {
                final ActivitySource source = sourceIterator.next();

                if (res.containsKey(source)) {
                    res.put(source, res.get(source) * multiplierSet.get(source));
                } else {
                    res.put(source, multiplierSet.get(source));
                }
            }

        }

        return res;
    }


    //    public Set<String> getMultiplierSets(Player player) {
    //        Set<String> res = new HashSet<String>();
    //        
    //        Iterator<String> multiplierIterator = config.multiplierSets.keySet().iterator();
    //        while (multiplierIterator.hasNext()) {
    //            final String multiplier = multiplierIterator.next();
    //            if (hasPermission(player, "activity.multiplier." + multiplier)) {
    //                res.add(multiplier);
    //            }
    //        }
    //        
    //        return res;
    //    }

    /**
     * Sends a prefixed message to given CommandSender
     * 
     * @param sender
     * @param message
     */
    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.DARK_GRAY + "[Activity] " + ChatColor.GRAY + message);
    }

    /**
     * Sends a prefixed message to given Player
     * 
     * @param player
     * @param message
     */
    public void sendMessage(Player player, String message) {
        player.sendMessage(ChatColor.DARK_GRAY + "[Activity] " + ChatColor.GRAY + message);
    }

    /**
     * Match a single online player which name contains filter
     * 
     * @param sender
     * @param filter
     * @return
     */
    public Player matchSinglePlayer(CommandSender sender, String filter) {

        filter = filter.toLowerCase();
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getName().toLowerCase().contains(filter)) {
                return player;
            }
        }

        sender.sendMessage(ChatColor.RED + "No matching player found, matching yourself.");
        return (Player) sender;

    }

    /**
     * Returns current activity of given player
     * 
     * @param playerName
     * @return
     */
    @Deprecated
    public int getActivity(Player player) {
        return data.getActivity(player.getName());
    }

    /**
     * Returns current activity of given playerName
     * 
     * @param playerName
     * @return
     */
    @Deprecated
    public int getActivity(String playerName) {
        return data.getActivity(playerName);
    }

    /**
     * Logs a debug message
     * 
     * @param message
     */
    public void debugMsg(String message) {
        debugMsg(message, null);
    }

    /**
     * Logs a debug message including the time the action took
     * 
     * @param message
     * @param started
     */
    public void debugMsg(String message, Long started) {
        if (!config.debug) return;
        StringBuilder msg = new StringBuilder("[HeavenActivity Debug]");
        msg.append("[Seq#").append(currentSequence).append(" (").append(data.playersActivities.size()).append(")]");
        if (started != null) {
            msg.append("[Time:").append(System.currentTimeMillis() - started).append("ms]");
        }
        msg.append(" ").append(message);

        HeavenActivity.logger.info(msg.toString());
    }

    /**
     * Initializes and starts the update timer
     */
    protected void startUpdateTimer() {

        updateTimer = new Timer();
        final HeavenActivity thisPlugin = this;
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            final HeavenActivity plugin = thisPlugin;

            public void run() {

                // Give players info
                if (currentSequence % config.notificationSequence == 0) {
                    final long notificationStarted = System.currentTimeMillis();
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        public void run() {

                            for (Player player : getServer().getOnlinePlayers()) {
                                if (hasPermission(player, "activity.notify.activity")) {
                                    final int activity = data.getActivity(player.getName());
                                    sendMessage(player, "Your current activity is: " + activityColor(activity) + activity + "%");
                                }
                            }
                            debugMsg("Notifications sent", notificationStarted);
                        }
                    });
                }

                // Handle income
                if (currentSequence % config.incomeSequence == 0 && config.incomeEnabled) {
                    long handleIncomeStarted = System.currentTimeMillis();
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        public void run() {
                            plugin.handleOnlineIncome();
                        }
                    });
                    debugMsg("Online income handled", handleIncomeStarted);
                }

                ++currentSequence;
                data.initNewSequence();

            }

        }, (config.sequenceInterval * 1000L), (config.sequenceInterval * 1000L));

                    logger.info("[HeavenActivity] Update timer started");

    }

    /**
     * Stops the update timer
     */
    protected void stopUpdateTimer() {
        updateTimer.cancel();
        logger.info("[HeavenActivity] Update timer stopped");
    }

    /**
     * Gives income to online players
     */
    protected void handleOnlineIncome() {

        if (data.playersActivities.size() == 0)
            return;


        for (Player player : getServer().getOnlinePlayers()) {
            final int activity = data.getActivity(player);
            if ((int)activity >= config.incomeMinActivity) {

                config.incomeExpression.setVariable("player_activity", activity);
                config.incomeExpression.setVariable("player_balance", econ.getBalance(player.getName()));

                final Double amount = config.incomeExpression.getValue();

                if (amount > 0.0 || config.incomeAllowNegative) {
                    econ.depositPlayer(player.getName(), amount);

                    if (config.incomeSourceAccount != null) {
                        econ.withdrawPlayer(player.getName(), amount);
                    }

                    if (hasPermission(player, "activity.notify.income")) {
                        sendMessage(player, "You got " + activityColor(activity) + econ.format(amount) 
                                + ChatColor.GRAY + " income for being " 
                                + activityColor(activity) + activity + "% " + ChatColor.GRAY + "active.");
                        sendMessage(player, "Your Balance is now: " + ChatColor.WHITE 
                                + econ.format(econ.getBalance(player.getName())));
                    }

                    continue;
                }
            }

            sendMessage(player, ChatColor.RED + "You were too lazy, no income for you this time!");
        }

    }

    protected ChatColor activityColor(int activity) {

        if (activity > 75) {
            return ChatColor.GREEN;
        } else if (activity < 25) {
            return ChatColor.RED;
        } else {
            return ChatColor.YELLOW;
        }

    }

}
