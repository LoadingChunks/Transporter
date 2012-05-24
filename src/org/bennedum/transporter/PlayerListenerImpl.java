/*
 * Copyright 2011 frdfsnlght <frdfsnlght@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bennedum.transporter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bennedum.transporter.api.GateException;
import org.bennedum.transporter.api.ReservationException;
import org.bennedum.transporter.api.TransporterException;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class PlayerListenerImpl implements Listener {

    // Logic map for player interaction
    private static final Map<Integer,String> ACTIONS = new HashMap<Integer,String>();
    
    // Masks are strings composed of zeros and ones. Each character position
    // corresponds to a bit position (bit 0 is first position).
    // 0 1  : Is the gate currently open?
    // 1 2  : Does the player have trp.gate.open permission?
    // 2 4  : Does the player have trp.gate.close permission?
    // 3 8  : Does the player have trp.gate.changeLink permission?
    // 4 16 : Does the gate have a valid destination?
    // 5 32 : Is the gate on its last link?
    // 6 64 : Is the gate block a trigger?
    // 7 128: Is the gate block a switch?
    
    // Values are a comma separated list of actions to perform:
    // OPEN: open the gate
    // CLOSE: close the gate
    // CHANGELINK: change the gate's link
    
    static {
        // gate is closed
        addAction("01xxxx1x", "OPEN");
        addAction("0xx1xx01", "CHANGELINK");
        addAction("00x1xxx1", "CHANGELINK");
        addAction("01x10x11", "CHANGELINK,OPEN");
                  
        // gate is open
        addAction("1x1xxx10", "CLOSE");
        addAction("1x10xx11", "CLOSE");
        addAction("1x11x111", "CLOSE,CHANGELINK");
        addAction("1x01xxx1", "CHANGELINK");
        addAction("1xx1xx01", "CHANGELINK");
        addAction("1xx1x011", "CHANGELINK");
    }
    
    private static void addAction(String mask, String action) {
        Set<Integer> masks = expandMask(mask);
        for (Integer m : masks) {
            //System.out.println("add " + expandMask(m) + " (" + m + ")");
            ACTIONS.put(m, action);
        }
    }
    
    public static Set<Integer> expandMask(String mask) {
        return expandMask(0, 0, mask.charAt(0), mask.substring(1));
    }
    
    private static Set<Integer> expandMask(int bitPos, int prefix, char bit, String suffix) {
        switch (bit) {
            case '0':
            case '1':
                int bitValue = (bit == '0') ? 0 : (int)Math.pow(2, bitPos);
                if (suffix.isEmpty()) {
                    Set<Integer> masks = new HashSet<Integer>();
                    masks.add(prefix + bitValue);
                    return masks;
                }
                return expandMask(bitPos + 1, prefix + bitValue, suffix.charAt(0), suffix.substring(1));
            default:
                Set<Integer> masks = new HashSet<Integer>();
                masks.addAll(expandMask(bitPos, prefix, '0', suffix));
                masks.addAll(expandMask(bitPos, prefix, '1', suffix));
                return masks;
        }
    }
    
    /*
    private static void checkAction(String mask, String action) {
        Set<Integer> masks = expandMask(mask);
        for (Integer m : masks) {
            String act = ACTIONS.get(m);
            if (act == null) {
                System.out.println("mask " + expandMask(m) + " (" + m + ") is not permitted but should be " + action);
                continue;
            }
            if (! act.equals(action))
                System.out.println("mask " + expandMask(m) + " (" + m + ") is " + act + " but should be " + action);
        }
    }
    
    private static String expandMask(int mask) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 8; i++)
            b.append(((mask & (int)Math.pow(2, i)) > 0) ? "1" : "0");
        return b.toString();
    }
    
    private static void dumpAction(String action) {
        List<String> masks = new ArrayList<String>();
        for (int mask : ACTIONS.keySet()) {
            if (! ACTIONS.get(mask).equals(action)) continue;
            String m = expandMask(mask);
            if (! masks.contains(m)) masks.add(m);
        }
        Collections.sort(masks);
        for (String mask : masks)
            System.out.println(mask);
    }
    */
    
    private Map<Player,Location> playerLocations = new HashMap<Player,Location>();
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Location location = block.getLocation();
        Context ctx = new Context(event.getPlayer());

        LocalGateImpl triggerGate = Gates.findGateForTrigger(location);
        LocalGateImpl switchGate = Gates.findGateForSwitch(location);
        if ((triggerGate == null) && (switchGate == null)) return;
        if ((triggerGate != null) && (switchGate != null) && (triggerGate != switchGate)) switchGate = null;
        
        LocalGateImpl testGate = (triggerGate == null) ? switchGate : triggerGate;
        Player player = event.getPlayer();
        Gates.setSelectedGate(player, testGate);
        
        int key = 
                (testGate.isOpen() ? 1 : 0) +
                (Permissions.has(player, "trp.gate.open." + testGate.getFullName()) ? 2 : 0) +
                (Permissions.has(player, "trp.gate.close." + testGate.getFullName()) ? 4 : 0) +
                (Permissions.has(player, "trp.gate.changeLink." + testGate.getFullName()) ? 8 : 0) +
                (testGate.hasValidDestination() ? 16 : 0) +
                (testGate.isLastLink() ? 32 : 0) +
                ((triggerGate != null) ? 64 : 0) +
                ((switchGate != null) ? 128 : 0);
        String value = ACTIONS.get(key);
        Utils.debug("gate key/action is %s/%s", key, value);
        
        if (value == null) {
            ctx.send("not permitted");
            return;
        }
        String[] actions = value.split(",");
        
        for (String action : actions) {
            
            if (action.equals("OPEN")) {
                try {
                    testGate.open();
                    ctx.send("opened gate '%s'", testGate.getName());
                    Utils.debug("player '%s' open gate '%s'", player.getName(), testGate.getName());
                } catch (GateException ee) {
                    ctx.warnLog(ee.getMessage());
                }
            }
            
            if (action.equals("CLOSE")) {
                testGate.close();
                ctx.send("closed gate '%s'", testGate.getName());
                Utils.debug("player '%s' closed gate '%s'", player.getName(), testGate.getName());
            }
            
            if (action.equals("CHANGELINK")) {
                try {
                    testGate.nextLink();
                    Utils.debug("player '%s' changed link for gate '%s'", player.getName(), testGate.getName());
                } catch (TransporterException te) {
                    ctx.warnLog(te.getMessage());
                }
            }
        }
                
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location loc = quantizePlayerLocation(event.getPlayer(), event.getTo());
        if (loc == null) return;
        
  //Utils.debug(Utils.blockCoords(loc));
        
        Player player = event.getPlayer();
        LocalGateImpl fromGate = Gates.findGateForPortal(loc);
        if (fromGate == null) {
            ReservationImpl.removeGateLock(player);
            return;
        }
        if (ReservationImpl.isGateLocked(player)) {
            return;
        }

        Context ctx = new Context(player);
        try {
            ReservationImpl r = new ReservationImpl(player, fromGate);
            r.depart();
            Location newLoc = r.getToLocation();
            if (newLoc != null) {
                event.setFrom(newLoc);
                event.setTo(newLoc);
            }
        } catch (ReservationException re) {
            ctx.warnLog(re.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        Location location = event.getTo();
        if ((location == null) ||
            (location.getWorld() == null)) return;
        
        Realm.onTeleport(player, location);
        
        for (Server server : Servers.getAll())
            server.sendPlayerChangeWorld(player);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ReservationImpl r = ReservationImpl.get(player);
        
        if (r == null) {
            // Realm handling
            if (Realm.onJoin(player)) return;
        }
        
        for (Server server : Servers.getAll())
            server.sendPlayerJoin(player, r != null);
        if (r == null) {
            ReservationImpl.addGateLock(player);
            return;
        }
        try {
            r.arrive();
            event.setJoinMessage(null);
            Realm.savePlayer(player);
        } catch (ReservationException e) {
            Context ctx = new Context(player);
            ctx.warnLog("there was a problem processing your arrival: ", e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ReservationImpl r = ReservationImpl.get(player);
        
        // Realm handling
        Realm.onQuit(player);
        
        for (Server server : Servers.getAll())
            server.sendPlayerQuit(player, r != null);
        if (r != null)
            event.setQuitMessage(null);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerKick(PlayerKickEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        ReservationImpl r = ReservationImpl.get(player);
        
        // Realm handling
        Realm.onKick(player);
        
        for (Server server : Servers.getAll())
            server.sendPlayerKick(player, r != null);
        if (r != null)
            event.setLeaveMessage(null);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = (Player)event.getEntity();
        for (Server server : Servers.getAll())
            server.sendPlayerDeath(player);
        
        // Realm handling
        Realm.onDeath(player, event.getDeathMessage());
        
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChat(PlayerChatEvent event) {
        if (event.isCancelled()) return;
        Chat.send(event.getPlayer(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerEnterBed(PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;
        Realm.onSetHome(event.getPlayer(), event.getPlayer().getLocation());
    }
    
    private Location quantizePlayerLocation(Player player, Location location) {
        Location newQLoc = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Location qLoc = playerLocations.get(player);
        if ((qLoc != null) && qLoc.equals(newQLoc)) return null;
        playerLocations.put(player, newQLoc);
        return newQLoc;
    }
    
}
