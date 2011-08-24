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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bennedum.transporter.Config;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Permissions;
import org.bennedum.transporter.PlayerProxy;
import org.bennedum.transporter.Players;
import org.bennedum.transporter.TransporterException;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class GlobalCommands extends TrpCommandProcessor {
    
    @Override
    public boolean matches(Context ctx, Command cmd, List<String> args) {
        return super.matches(ctx, cmd, args) && (
               ("list".startsWith(args.get(0).toLowerCase())) ||
               ("get".startsWith(args.get(0).toLowerCase())) ||
               ("set".startsWith(args.get(0).toLowerCase()))
            );
    }
    
    @Override
    public List<String> getUsage(Context ctx) {
        List<String> cmds = new ArrayList<String>();
        cmds.add(getPrefix(ctx) + "list");
        cmds.add(getPrefix(ctx) + "get <option>|*");
        cmds.add(getPrefix(ctx) + "set <option> <value>");
        return cmds;
    }
    
    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        if (args.isEmpty())
            throw new CommandException("do what?");
        String subCmd = args.remove(0).toLowerCase();

        if ("list".startsWith(subCmd)) {
            Permissions.require(ctx.getPlayer(), "trp.list");
            if (Players.getAll().isEmpty())
                ctx.send("there are no players");
            else {
                List<PlayerProxy> players = Players.getAll();
                Collections.sort(players, new Comparator<PlayerProxy>() {
                    @Override
                    public int compare(PlayerProxy a, PlayerProxy b) {
                        if ((a.getServerName() != null) && (b.getServerName() == null)) return 1;
                        if ((a.getServerName() == null) && (b.getServerName() != null)) return -1;
                        int res = 0;
                        if ((a.getServerName() != null) && (b.getServerName() != null))
                            res = a.getServerName().compareToIgnoreCase(b.getServerName());
                        if (res == 0)
                            res = a.getName().compareToIgnoreCase(b.getName());
                        return res;
                    }
                });
                ctx.send("%d players:", players.size());
                String lastServer = "*";
                for (PlayerProxy player : players) {
                    if (! lastServer.equals(player.getServerName())) {
                        if (player.getServerName() != null) {
                            ctx.send("  server: %s", player.getServerName());
                            lastServer = player.getServerName();
                        }
                    }
                    if (lastServer.equals("*"))
                        ctx.send("  %s (%s)", player.getDisplayName(), player.getWorldName());
                    else
                        ctx.send("    %s (%s)", player.getDisplayName(), player.getWorldName());
                }
            }
            return;
        }
        
        if ("set".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("option name required");
            String option = args.remove(0);
            if (args.isEmpty())
                throw new CommandException("option value required");
            String value = args.remove(0);
            Config.setOption(ctx, option, value);
            return;
        }

        if ("get".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("option name required");
            String option = args.remove(0);
            Config.getOptions(ctx, option);
            return;
        }
        
    }
    
}
