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
import org.bennedum.transporter.Global;
import org.bennedum.transporter.TransporterException;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class DumpCommand extends TrpCommandProcessor {

    @Override
    public boolean requiresConsole() { return true; }

    @Override
    protected String[] getSubCommands() { return new String[] {"dump"}; }

    @Override
    public String getUsage(Context ctx) {
        return super.getUsage(ctx) + " gate <name> | design <name>";
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args)  throws TransporterException {
        super.process(ctx, cmd, args);
        if (args.size() < 2)
            throw new CommandException("dump what?");
        String what = args.get(0).toLowerCase();
        String name = args.get(1);
        if ("gate".startsWith(what)) what = "gate";
        else if ("design".startsWith(what)) what = "design";
        else
            throw new CommandException("dump what?");
        ctx.requireAllPermissions("trp.dump." + what);
        if (what.equals("gate")) {
            Gate gate = Global.gates.get(ctx, name);
            if (gate == null)
                throw new CommandException("unknown or offline gate '%s'", name);
            gate.dump(ctx);
        } else if (what.equals("design")) {
            Design design = Global.designs.get(name);
            if (design == null)
                throw new CommandException("unknown design '%s'", name);
            design.dump(ctx);
        }
    }

}
