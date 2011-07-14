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
import java.util.Arrays;
import java.util.List;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.TransporterException;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class HelpCommand extends TrpCommandProcessor {

    private static final int linesPerPage = 19;

    @Override
    protected String[] getSubCommands() { return new String[] {"help", "?"}; }

    @Override
    public String getUsage(Context ctx) {
        return super.getUsage(ctx) + " [pageno]";
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args)  throws TransporterException {
        super.process(ctx, cmd, args);
        List<String> help = new ArrayList<String>();
        for (CommandProcessor cp : Global.commands) {
            if (cp.isHidden()) continue;
            if (cp.requiresPlayer() && (! ctx.isPlayer())) continue;
            if (cp.requiresOp() && (! ctx.isOp())) continue;
            if (cp.requiresConsole() && (! ctx.isConsole())) continue;
            String usage = cp.getUsage(ctx);
            if (usage != null)
                help.addAll(Arrays.asList(usage.split("\n")));
        }

        if (ctx.isConsole()) {
            for (String line : help)
                ctx.send(line);
        } else {
            int page = 1;
            int pages = (int)Math.ceil((double)help.size() / (double)linesPerPage);
            if (! args.isEmpty()) {
                try {
                    page = Integer.parseInt(args.get(0));
                } catch (NumberFormatException nfe) {}
                if (page < 1) page = 1;
                if (page > pages) page = pages;
            }
            int min = (page - 1) * linesPerPage;
            int max = (page * linesPerPage) - 1;
            if (max > (help.size() - 1)) max = help.size() - 1;

            ctx.send("Help page " + page + " of " + pages);
            for (int i = min; i <= max; i++)
                ctx.send(help.get(i));
        }
    }

}
