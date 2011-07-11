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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.TransporterException;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class WorldCommand extends TrpCommandProcessor {

    @Override
    protected String[] getSubCommands() { return new String[] {"world"}; }

    @Override
    public String getUsage(Context ctx) {
        return
                super.getUsage(ctx) + " list\n" +
                super.getUsage(ctx) + " create <world> [<env>] [<seed>]\n" +
                super.getUsage(ctx) + " load <world>\n" +
                super.getUsage(ctx) + " unload <world>";
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        super.process(ctx, cmd, args);
        if (args.isEmpty())
            throw new CommandException("do what with a world?");
        String subCmd = args.remove(0).toLowerCase();

        if ("list".startsWith(subCmd)) {
            ctx.requireAllPermissions("trp.world.list");
            List<World> worlds = new ArrayList<World>(Global.plugin.getServer().getWorlds());
            Collections.sort(worlds, new Comparator<World>() {
                @Override
                public int compare(World a, World b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            ctx.send("%d worlds:", worlds.size());
            for (World world : worlds)
                ctx.send("  %s", world.getName());
            return;
        }

        if ("create".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("new name required");
            String newName = args.remove(0);
            Environment env = Environment.NORMAL;
            Long seed = null;
            if (! args.isEmpty()) {
                String arg = args.remove(0);
                if (arg.matches("^\\d+$"))
                    try {
                        seed = Long.parseLong(arg);
                    } catch (NumberFormatException e) {
                        throw new CommandException("illegal seed value");
                    }
                else
                    try {
                        env = Environment.valueOf(arg.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new CommandException("unknown environment");
                    }
            }
            ctx.requireAllPermissions("trp.world.create");

            if (seed == null)
                Global.plugin.getServer().createWorld(newName, env);
            else
                Global.plugin.getServer().createWorld(newName, env, seed);
            ctx.sendLog("created world '%s'", newName);
            return;
        }

        if ("load".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("world name required");
            String name = args.remove(0);

            ctx.requireAllPermissions("trp.world.load");

            if (Global.plugin.getServer().getWorld(name) != null)
                throw new CommandException("world is already loaded");
            File worldFolder = new File(name);
            if (! worldFolder.isDirectory())
                throw new CommandException("world doesn't exist");
            Global.plugin.getServer().createWorld(name, Environment.NORMAL);
            ctx.sendLog("loaded world");
            return;
        }

        if ("unload".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("world name required");
            String name = args.remove(0);

            ctx.requireAllPermissions("trp.world.unload");

            if (Global.plugin.getServer().getWorld(name) == null)
                throw new CommandException("world is not loaded");
            if (! Global.plugin.getServer().unloadWorld(name, true))
                throw new CommandException("unable to unload world");
            ctx.sendLog("unloaded world");
            return;
        }

        throw new CommandException("do what with a world?");
    }

}
