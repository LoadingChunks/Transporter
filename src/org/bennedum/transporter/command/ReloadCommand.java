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
import java.util.List;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.TransporterException;
import org.bennedum.transporter.Utils;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class ReloadCommand extends TrpCommandProcessor {

    @Override
    protected String[] getSubCommands() { return new String[] {"reload"}; }

    @Override
    public String getUsage(Context ctx) {
        return super.getUsage(ctx) + " [config|designs|gates|servers]";
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        super.process(ctx, cmd, args);
        List<String> what = new ArrayList<String>();
        if (args.isEmpty()) {
            if (ctx.hasAllPermissions("trp.reload.config"))
                what.add("config");
            if (ctx.hasAllPermissions("trp.reload.designs"))
                what.add("designs");
            if (ctx.hasAllPermissions("trp.reload.gates"))
                what.add("gates");
            if (ctx.hasAllPermissions("trp.reload.servers"))
                what.add("servers");
        } else {
            for (String arg : args) {
                arg = arg.toLowerCase();
                if ("config".startsWith(arg)) arg = "config";
                else if ("designs".startsWith(arg)) arg = "designs";
                else if ("gates".startsWith(arg)) arg = "gates";
                else if ("servers".startsWith(arg)) arg = "servers";
                else
                    throw new CommandException("reload what?");
                ctx.requireAllPermissions("trp.reload." + arg);
                what.add(arg);
            }
        }
        for (String arg : what) {
            if (arg.equals("config"))
                Utils.loadConfig(ctx);
            else if (arg.equals("designs"))
                Global.designs.loadAll(ctx);
            else if (arg.equals("gates"))
                Global.gates.loadAll(ctx);
            else if (arg.equals("servers"))
                Global.servers.loadAll(ctx);
        }
    }

}
