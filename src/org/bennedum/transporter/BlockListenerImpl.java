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

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.SignChangeEvent;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class BlockListenerImpl extends BlockListener {

    @Override
    public void onBlockDamage(BlockDamageEvent event) {
        LocalGate gate = Global.gates.findGateForProtection(event.getBlock().getLocation());
        if (gate != null) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onBlockBreak(BlockBreakEvent event) {
        LocalGate gate = Global.gates.findGateForScreen(event.getBlock().getLocation());
        if (gate != null) {
            Context ctx = new Context(event.getPlayer());
            Global.gates.destroy(gate);
            ctx.sendLog("destroyed gate '%s'", gate.getName());
        }
        gate = Global.gates.findGateForProtection(event.getBlock().getLocation());
        if (gate != null) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        LocalGate gate = Global.gates.findGateForScreen(block.getLocation());
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
        try {
            if (gateName == null) return;
            gate = Global.designs.create(ctx, block.getLocation(), gateName);
            if (gate == null) return;
            ctx.sendLog("created gate '%s'", gate.getName());
            Global.setSelectedGate(event.getPlayer(), gate);

            List<SavedBlock> undoBlocks = Global.getBuildUndo(event.getPlayer());
            if (undoBlocks != null) {
                for (SavedBlock undoBlock : undoBlocks) {
                    if (gate.isOccupyingLocation(undoBlock.getLocation())) {
                        Global.removeBuildUndo(event.getPlayer());
                        break;
                    }
                }
            }

            if (link == null) return;
            ctx.getPlayer().performCommand("trp gate link add \"" + link + "\"");
        } catch (TransporterException te) {
            ctx.warn(te.getMessage());
        }
    }

}
