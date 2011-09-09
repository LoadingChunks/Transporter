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

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class PlayerListenerImpl extends PlayerListener {

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Location location = block.getLocation();
        Context ctx = new Context(event.getPlayer());

        // open a closed gate, change destinations, close an opened gate if the end of the links are reached

        LocalGate gate = Gates.findGateForTrigger(location);
        if (gate != null) {
            Global.setSelectedGate(event.getPlayer(), gate);

            if (gate.isClosed() &&
                Permissions.has(ctx.getPlayer(), "trp.gate.open." + gate.getName())) {
                if (gate.hasValidDestination()) {
                    try {
                        gate.open();
                        ctx.sendLog("opened gate '%s'", gate.getName());
                        return;
                    } catch (GateException ge) {
                        ctx.warnLog(ge.getMessage());
                    }
                }
            } else {
                Gate switchGate = Gates.findGateForSwitch(location);
                if (switchGate == gate) {
                    // the trigger is the same block as the switch, so do something special
                    if (gate.isLastLink() &&
                        Permissions.has(ctx.getPlayer(), "trp.gate.close." + gate.getName()) &&
                        Permissions.has(ctx.getPlayer(), "trp.gate.changeLink." + gate.getName())) {
                        gate.close();
                        ctx.sendLog("closed gate '%s'", gate.getName());
                        try {
                            gate.nextLink();
                        } catch (GateException ge) {
                            ctx.warnLog(ge.getMessage());
                        }
                        return;
                    }
                } else if (Permissions.has(ctx.getPlayer(), "trp.gate.close." + gate.getName())) {
                    gate.close();
                    ctx.sendLog("closed gate '%s'", gate.getName());
                    return;
                }
            }
        }

        gate = Gates.findGateForSwitch(location);
        if (gate != null) {
            Global.setSelectedGate(event.getPlayer(), gate);
            try {
                Permissions.require(ctx.getPlayer(), "trp.gate.changeLink." + gate.getName());
                gate.nextLink();
            } catch (TransporterException te) {
                ctx.warnLog(te.getMessage());
            }
            return;
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        LocalGate fromGate = Gates.findGateForPortal(event.getTo());
        if (fromGate == null) {
            Reservation.removeGateLock(player);
            return;
        }
        if (Reservation.isGateLocked(player)) {
            return;
        }

        Context ctx = new Context(player);
        try {
            Reservation r = new Reservation(player, fromGate);
            r.depart();
            Location newLoc = r.getToLocation();
            if (newLoc != null) {
                event.setFrom(newLoc);
                event.setTo(newLoc);
                // cancelling the event is bad in RB 953!
                //event.setCancelled(true);
            }
        } catch (ReservationException re) {
            ctx.warnLog(re.getMessage());
        }
    }

    @Override
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location location = event.getTo();
        Players.onTeleport(player, location);
    }
    
    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Reservation r = Reservation.get(player);
        Players.onJoin(player, r);
        if (r == null) {
            Reservation.addGateLock(player);
            return;
        }
        try {
            r.arrive();
            event.setJoinMessage(null);
        } catch (ReservationException e) {
            Context ctx = new Context(player);
            ctx.warnLog("there was a problem processing your arrival: ", e.getMessage());
        }
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Reservation r = Reservation.get(player);
        Players.onQuit(player, r);
        if (r != null)
            event.setQuitMessage(null);
    }

    @Override
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();
        Reservation r = Reservation.get(player);
        Players.onKick(player, r);
        if (r != null)
            event.setLeaveMessage(null);
    }
    
    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        Chat.send(event.getPlayer(), event.getMessage());
    }

}
