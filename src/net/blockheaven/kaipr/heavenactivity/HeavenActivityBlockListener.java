package net.blockheaven.kaipr.heavenactivity;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class HeavenActivityBlockListener implements Listener {
    
    protected HeavenActivity plugin;
    
    protected Map<String, Long> lastAction = new HashMap<String, Long>();
    
    /**
     * Construct the listener.
     * 
     * @param plugin
     */
    public HeavenActivityBlockListener(HeavenActivity plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        
        final long time = System.currentTimeMillis();
        final String playerName = event.getPlayer().getName();
        
        if (!lastAction.containsKey(playerName) || (time > lastAction.get(playerName) + plugin.config.blockDelay)) {            
            plugin.data.addActivity(playerName, ActivitySource.BLOCK_PLACE);
            
            lastAction.put(playerName, time);
        }
        
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        
        if (event.isCancelled())
            return;
        
        final long time = System.currentTimeMillis();
        final String playerName = event.getPlayer().getName();
        
        if (!lastAction.containsKey(playerName) || (time > lastAction.get(playerName) + plugin.config.blockDelay)) {
            plugin.data.addActivity(playerName, ActivitySource.BLOCK_BREAK);
            
            lastAction.put(playerName, time);
        }
        
    }
}
