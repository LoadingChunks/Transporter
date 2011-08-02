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

import java.util.List;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Gate;
import org.bennedum.transporter.Design;
import org.bennedum.transporter.Designs;
import org.bennedum.transporter.Gates;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.Permissions;
import org.bennedum.transporter.TransporterException;
import org.bennedum.transporter.Utils;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class DebugCommand extends TrpCommandProcessor {

    @Override
    public boolean isHidden() { return true; }

    @Override
    public boolean requiresConsole() { return true; }

    @Override
    protected String[] getSubCommands() { return new String[] {"debug"}; }

    @Override
    public String getUsage(Context ctx) {
        return
                super.getUsage(ctx) + " true|false\n" +
                super.getUsage(ctx) + " submit <message>\n" +
                super.getUsage(ctx) + " dump gate <name>\n" +
                super.getUsage(ctx) + " dump design <name>";
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args)  throws TransporterException {
        super.process(ctx, cmd, args);
        if (args.isEmpty())
            throw new CommandException("debug what?");
        String subCmd = args.remove(0).toLowerCase();

        if ("submit".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("message required");
            String message = args.remove(0);
            while (! args.isEmpty())
                message = message + " " + args.remove(0);
            ctx.send("requested submission of debug data");
            Utils.submitDebug(message);
            return;
        }
        
        if ("dump".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("dump what?");
            String what = args.remove(0).toLowerCase();
            
            if ("gate".startsWith(what)) {
                Permissions.require(ctx.getPlayer(), "trp.debug.dump.gate");
                if (args.isEmpty())
                    throw new CommandException("gate name required");
                String name = args.remove(1);
                Gate gate = Gates.get(name);
                if (gate == null)
                    throw new CommandException("unknown or offline gate '%s'", name);
                gate.dump(ctx);
                return;
            }
            
            if ("design".startsWith(what)) {
                Permissions.require(ctx.getPlayer(), "trp.debug.dump.design");
                if (args.isEmpty())
                    throw new CommandException("design name required");
                String name = args.remove(1);
                Design design = Designs.get(name);
                if (design == null)
                    throw new CommandException("unknown design '%s'", name);
                design.dump(ctx);
                return;
            }
            throw new CommandException("dump what?");
        }
        
        Global.config.setProperty("debug", Boolean.parseBoolean(subCmd));
        ctx.send("debug set to %s", Global.config.getBoolean("debug", false));
    }

}
