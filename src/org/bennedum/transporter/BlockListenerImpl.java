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

import org.bennedum.transporter.api.GateException;
import org.bennedum.transporter.api.TransporterException;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.material.Bed;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class BlockListenerImpl implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockCanBuild(BlockCanBuildEvent event) {
        LocalGateImpl gate = Gates.findGateForPortal(event.getBlock().getLocation());
        if ((gate != null) && gate.isOpen())
            event.setBuildable(false);
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockDamage(BlockDamageEvent event) {
        if (event.isCancelled()) return;
        LocalGateImpl gate = Gates.findGateForProtection(event.getBlock().getLocation());
        if (gate != null) {
            event.setCancelled(true);
            gate.onProtect(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        LocalGateImpl gate = Gates.findGateForProtection(event.getBlock().getLocation());
        if (gate != null) {
            event.setCancelled(true);
            gate.onProtect(event.getBlock().getLocation());
            return;
        }
        
        gate = Gates.findGateForScreen(event.getBlock().getLocation());
        if (gate != null) {
            Context ctx = new Context(event.getPlayer());
            try {
                Permissions.require(ctx.getPlayer(), "trp.gate.destroy." + gate.getFullName());
                Gates.destroy(gate, false);
                ctx.sendLog("destroyed gate '%s'", gate.getName());
            } catch (PermissionsException pe) {
                ctx.warn(pe.getMessage());
                event.setCancelled(true);
                gate.onProtect(event.getBlock().getLocation());
            }
        }
        
        if (event.getBlock().getType().equals(Material.BED_BLOCK)) {
            Location playerBedLoc = event.getPlayer().getBedSpawnLocation();
            if (playerBedLoc != null) {
                // figure out the block location of the HEAD of the bed being destroyed
                Location bedLoc;
                Block bedBlock = event.getBlock();
                Bed bed = (Bed)Material.getMaterial(bedBlock.getTypeId()).getNewData(bedBlock.getData());
                if (bed.isHeadOfBed())
                    // trivial case
                    bedLoc = bedBlock.getLocation();
                else {
                    // we have the foot, find the head
                    switch (bed.getFacing()) {
                        case NORTH:
                            bedLoc = bedBlock.getRelative(-1, 0, 0).getLocation();
                            break;
                        case SOUTH:
                            bedLoc = bedBlock.getRelative(1, 0, 0).getLocation();
                            break;
                        case EAST:
                            bedLoc = bedBlock.getRelative(0, 0, -1).getLocation();
                            break;
                        case WEST:
                            bedLoc = bedBlock.getRelative(0, 0, 1).getLocation();
                            break;
                        default:
                            Utils.debug("unhandled bed facing: %s", bed.getFacing());
                            return;
                            //break;
                    }
                }
//      Utils.debug("---------------------");
//      Utils.debug("playerLoc: %s", Utils.blockCoords(event.getPlayer().getLocation()));
//      Utils.debug("blockLoc: %s", Utils.blockCoords(bedBlock.getLocation()));
//      Utils.debug("bedFace: %s", bed.getFacing());
//      Utils.debug("bedLoc: %s", Utils.blockCoords(bedLoc));
//      Utils.debug("playerBedLoc: %s", Utils.blockCoords(playerBedLoc));
      
                if (bedLoc.distance(playerBedLoc) < 0.5)
//      Utils.debug("FOUND THE BED!");
                    Realm.onUnsetHome(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSignChange(SignChangeEvent event) {
        if (event.isCancelled()) return;
        Block block = event.getBlock();
        LocalGateImpl gate = Gates.findGateForScreen(block.getLocation());
        if (gate != null) return;
        Context ctx = new Context(event.getPlayer());
        String gateName = null;
        String link = null;
        for (String line : event.getLines()) {
            if ((line == null) || (line.trim().length() == 0)) continue;
            if (gateName == null)
                gateName = line;
            else if (link == null)
                link = line;
            else
                link += "." + line;
        }
        if (gateName == null) return;
        try {
            DesignMatch match = Designs.matchScreen(block.getLocation());
            if (match == null) return;
            
            Permissions.require(ctx.getPlayer(), "trp.create." + match.design.getName());
            Economy.requireFunds(ctx.getPlayer(), match.design.getCreateCost());
            
            gate = match.design.create(match, ctx.getPlayer().getName(), gateName);
            Gates.add(gate, true);
            ctx.sendLog("created gate '%s'", gate.getName());
            Gates.setSelectedGate(ctx.getPlayer(), gate);
            
            try {
                if (Economy.deductFunds(ctx.getPlayer(), match.design.getCreateCost()))
                    ctx.sendLog("debited %s for gate creation", Economy.format(match.design.getCreateCost()));
            } catch (EconomyException e) {
                Utils.warning("unable to debit gate creation costs for %s: %s", ctx.getPlayer().getName(), e.getMessage());
            }
    
            if (link == null) return;
            ctx.getPlayer().performCommand("trp gate link add \"" + link + "\"");
        } catch (TransporterException te) {
            ctx.warn(te.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (event.isCancelled()) return;
        // This prevents liquid portals from flowing out
        LocalGateImpl gate = Gates.findGateForPortal(event.getBlock().getLocation());
        if (gate != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        LocalGateImpl g = Gates.findGateForTrigger(event.getBlock().getLocation());
        if (! (g instanceof LocalBlockGateImpl)) return;
        LocalBlockGateImpl gate = (LocalBlockGateImpl)g;
        if (gate != null) {
            DesignBlockDetail block = gate.getGateBlock(event.getBlock().getLocation()).getDetail();
            if (gate.isClosed() && (block.getTriggerOpenMode() != RedstoneMode.NONE) && gate.hasValidDestination()) {
                boolean openIt = false;
                switch (block.getTriggerOpenMode()) {
                    case HIGH: openIt = (event.getNewCurrent() > 0) && (event.getOldCurrent() == 0); break;
                    case LOW: openIt = (event.getNewCurrent() == 0) && (event.getOldCurrent() > 0); break;
                }
                if (openIt) {
                    try {
                        gate.open();
                        Utils.debug("gate '%s' opened via redstone", gate.getName());
                    } catch (GateException ee) {
                        Utils.warning(ee.getMessage());
                    }
                }
            }
            
            else if (gate.isOpen() && (block.getTriggerOpenMode() != RedstoneMode.NONE)) {
                boolean closeIt = false;
                switch (block.getTriggerOpenMode()) {
                    case HIGH: closeIt = (event.getNewCurrent() > 0) && (event.getOldCurrent() == 0); break;
                    case LOW: closeIt = (event.getNewCurrent() == 0) && (event.getOldCurrent() > 0); break;
                }
                if (closeIt) {
                    gate.close();
                    Utils.debug("gate '%s' closed via redstone", gate.getName());
                }
            }
            return;
        }
        
        g = Gates.findGateForSwitch(event.getBlock().getLocation());
        if (! (g instanceof LocalBlockGateImpl)) return;
        gate = (LocalBlockGateImpl)g;
        
        if (gate != null) {
            DesignBlockDetail block = gate.getGateBlock(event.getBlock().getLocation()).getDetail();
            boolean nextLink = false;
            switch (block.getSwitchMode()) {
                case HIGH: nextLink = (event.getNewCurrent() > 0) && (event.getOldCurrent() == 0); break;
                case LOW: nextLink = (event.getNewCurrent() == 0) && (event.getOldCurrent() > 0); break;
            }
            if (nextLink) {
                try {
                    gate.nextLink();
                } catch (TransporterException te) {
                    Utils.warning(te.getMessage());
                }
            }
        }
    }
    
}
