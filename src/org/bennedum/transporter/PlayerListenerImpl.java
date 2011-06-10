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
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class PlayerListenerImpl extends PlayerListener {

    @Override
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Location location = block.getLocation();
        Context ctx = new Context(event.getPlayer());

        // open a closed gate, change destinations, close an opened gate if the end of the links are reached

        LocalGate gate = Global.gates.findGateForTrigger(location);
        if (gate != null) {
            Global.setSelectedGate(event.getPlayer(), gate);

            if (gate.isClosed()) {
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
                Gate switchGate = Global.gates.findGateForSwitch(location);
                if (switchGate == gate) {
                    // the trigger is the same block as the switch, so do something special
                    if (gate.isLastLink()) {
                        gate.close();
                        ctx.sendLog("closed gate '%s'", gate.getName());
                        try {
                            gate.nextLink();
                        } catch (GateException ge) {
                            ctx.warnLog(ge.getMessage());
                        }
                        return;
                    }
                } else {
                    gate.close();
                    ctx.sendLog("closed gate '%s'", gate.getName());
                    return;
                }
            }
        }

        gate = Global.gates.findGateForSwitch(location);
        if (gate != null) {
            Global.setSelectedGate(event.getPlayer(), gate);
            try {
                gate.nextLink();
            } catch (GateException ge) {
                ctx.warnLog(ge.getMessage());
            }
            return;
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        LocalGate fromGate = Global.gates.findGateForPortal(event.getTo());
        if (fromGate == null) {
            Teleport.removeGateLock(player);
            return;
        }
        if (Teleport.isGateLocked(player)) return;

        Context ctx = new Context(player);
        try {
            Location newLoc = Teleport.send(player, fromGate);
            if (newLoc != null) {
                event.setFrom(newLoc);
                event.setTo(newLoc);
                event.setCancelled(true);
            }
        } catch (TeleportException te) {
            ctx.warnLog(te.getMessage());
        }
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (Teleport.expectingArrival(event.getPlayer())) {
            Context ctx = new Context(event.getPlayer());
            try {
                Location loc = Teleport.arrive(event.getPlayer());
            } catch (TeleportException te) {
                ctx.warnLog("there was a problem processing your arrival: ", te.getMessage());
            }
        }
    }

    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        Teleport.sendChat(event.getPlayer(), event.getMessage());
    }

}
