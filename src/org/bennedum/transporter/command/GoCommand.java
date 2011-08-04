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
import org.bennedum.transporter.Gate;
import org.bennedum.transporter.Gates;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.Permissions;
import org.bennedum.transporter.Reservation;
import org.bennedum.transporter.ReservationException;
import org.bennedum.transporter.TransporterException;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class GoCommand extends TrpCommandProcessor {
    
    // TODO: move this command into "gate"
    
    private static final String GROUP = "go ";
    
    @Override
    public boolean matches(Context ctx, Command cmd, List<String> args) {
        return super.matches(ctx, cmd, args) &&
               GROUP.startsWith(args.get(0).toLowerCase()) &&
               ctx.isPlayer();
    }
    
    @Override
    public List<String> getUsage(Context ctx) {
        if (! ctx.isPlayer()) return null;
        List<String> cmds = new ArrayList<String>();
        cmds.add(getPrefix(ctx) + GROUP + "[<gate>]");
        return cmds;
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        args.remove(0);
        Gate gate = null;
        if (! args.isEmpty()) {
            gate = Gates.get(ctx, args.get(0));
            if (gate == null)
                throw new CommandException("unknown gate '%s'", args.get(0));
            args.remove(0);
        } else if (ctx.isPlayer())
            gate = Global.getSelectedGate(ctx.getPlayer());
        if (gate == null)
            throw new CommandException("gate name required");

        Permissions.require(ctx.getPlayer(), "trp.go." + gate.getName());
        try {
            Reservation r = new Reservation(ctx.getPlayer(), gate);
            r.depart();
        } catch (ReservationException e) {
            ctx.warnLog(e.getMessage());
        }
    }
    
}
