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
package com.frdfsnlght.transporter.command;

import com.frdfsnlght.transporter.Context;
import com.frdfsnlght.transporter.Permissions;
import com.frdfsnlght.transporter.Realm;
import com.frdfsnlght.transporter.api.TransporterException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class RealmCommand  extends TrpCommandProcessor {

    private static final String GROUP = "realm ";

    @Override
    public boolean matches(Context ctx, Command cmd, List<String> args) {
        return super.matches(ctx, cmd, args) &&
               GROUP.startsWith(args.get(0).toLowerCase());
    }

    @Override
    public List<String> getUsage(Context ctx) {
        List<String> cmds = new ArrayList<String>();
        cmds.add(getPrefix(ctx) + GROUP + "enable");
        cmds.add(getPrefix(ctx) + GROUP + "disable");
        cmds.add(getPrefix(ctx) + GROUP + "get <option>|*");
        cmds.add(getPrefix(ctx) + GROUP + "set <option> <value>");

        return cmds;
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        args.remove(0);
        if (args.isEmpty())
            throw new CommandException("do what with realm support?");
        String subCmd = args.get(0).toLowerCase();
        args.remove(0);

        if ("set".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("option name required");
            String option = args.remove(0);
            if (args.isEmpty())
                throw new CommandException("option value required");
            String value = args.remove(0);
            Realm.setOption(ctx, option, value);
            return;
        }

        if ("get".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("option name required");
            String option = args.remove(0);
            Realm.getOptions(ctx, option);
            return;
        }

        if ("enable".startsWith(subCmd)) {
            Permissions.require(ctx.getPlayer(), "trp.realm.enable");
            Realm.setEnabled(ctx, true);
            return;
        }

        if ("disable".startsWith(subCmd)) {
            Permissions.require(ctx.getPlayer(), "trp.realm.disable");
            Realm.setEnabled(ctx, false);
            return;
        }

        throw new CommandException("do what with realm support?");
    }

}
