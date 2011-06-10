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
package org.bennedum.transporter.command;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Design;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.SavedBlock;
import org.bennedum.transporter.TransporterException;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class DesignCommand extends TrpCommandProcessor {

    @Override
    protected String[] getSubCommands() { return new String[] {"design"}; }

    @Override
    public String getUsage(Context ctx) {
        return
               super.getUsage(ctx) + " list\n" +
               super.getUsage(ctx) + " build <name>|undo";
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        super.process(ctx, cmd, args);
        if (args.isEmpty())
            throw new CommandException("do what with a design?");
        String subCmd = args.remove(0).toLowerCase();

        if ("list".startsWith(subCmd)) {
            ctx.requireAllPermissions("trp.design.list");
            if (Global.designs.getAll().isEmpty())
                ctx.send("there are no designs");
            else {
                List<Design> designs = Global.designs.getAll();
                Collections.sort(designs, new Comparator<Design>() {
                    @Override
                    public int compare(Design a, Design b) {
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                ctx.send("%d designs:", designs.size());
                for (Design design : designs)
                    ctx.send("  %s", design.getName());
            }
            return;
        }

        if ("build".startsWith(subCmd)) {
            if (! ctx.isPlayer())
                throw new CommandException("this command can only be used by a player");
            
            if (! Global.config.getBoolean("allowBuild", true))
                throw new CommandException("building gates is not permitted");

            if (args.isEmpty())
                throw new CommandException("design name required");
            String designName = args.get(0);
            Player player = ctx.getPlayer();
            World world = player.getWorld();

            if (designName.toLowerCase().equals("undo")) {
                ctx.requireAllPermissions("trp.design.build.undo");
                List<SavedBlock> blocks = Global.removeBuildUndo(player);
                if (blocks == null)
                    throw new CommandException("nothing to undo");
                for (SavedBlock block : blocks)
                    block.restore();
                ctx.sendLog("build undone");
                return;
            }

            Design design = Global.designs.get(designName);
            if (design == null)
                throw new CommandException("unknown design '%s'", designName);
            if (! design.isBuildable())
                throw new CommandException("design '%s' is not buildable", design.getName());
            if (! design.isBuildableInWorld(world))
                throw new CommandException("gate type '%s' is not buildable in this world", design.getName());

            ctx.requireAllPermissions("trp.design.build." + design.getName());

            ctx.requireFunds(design.getBuildCost());
            if (design.mustBuildFromInventory())
                ctx.requireInventory(design.getInventoryBlocks());

            List<SavedBlock> blocks = design.build(player.getLocation());

            ctx.chargeFunds(design.getBuildCost(), "debited $$ for gate construction");
            if (design.mustBuildFromInventory())
                ctx.chargeInventory(design.getInventoryBlocks());

            Global.setBuildUndo(player, blocks);

            return;
        }

        throw new CommandException("do what with a design?");
    }

}
