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
import org.bennedum.transporter.Global;
import org.bennedum.transporter.Teleport;
import org.bennedum.transporter.TeleportException;
import org.bennedum.transporter.TransporterException;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class GoCommand extends TrpCommandProcessor {
    
    @Override
    public boolean requiresPlayer() { return true; }
    
    @Override
    protected String[] getSubCommands() { return new String[] {"go"}; }
    
    @Override
    public String getUsage(Context ctx) {
        return super.getUsage(ctx) + " [<gate>]";
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        super.process(ctx, cmd, args);
        Gate gate = null;
        if (! args.isEmpty()) {
            gate = Global.gates.get(ctx, args.get(0));
            if (gate == null)
                throw new CommandException("unknown gate '%s'", args.get(0));
            args.remove(0);
        } else if (ctx.isPlayer())
            gate = Global.getSelectedGate(ctx.getPlayer());
        if (gate == null)
            throw new CommandException("gate name required");

        ctx.requireAllPermissions("trp.go." + gate.getName());
        try {
            Teleport.sendDirect(ctx.getPlayer(), gate);
        } catch (TeleportException te) {
            ctx.warnLog(te.getMessage());
        }
    }
    
}
