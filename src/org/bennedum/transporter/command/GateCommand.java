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

import com.iConomy.iConomy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Gate;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.LocalGate;
import org.bennedum.transporter.TransporterException;
import org.bennedum.transporter.Utils;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class GateCommand extends TrpCommandProcessor {

    @Override
    protected String[] getSubCommands() { return new String[] {"gate"}; }

    @Override
    public String getUsage(Context ctx) {
        return
                super.getUsage(ctx) + " list\n" +
                super.getUsage(ctx) + " select <gate>\n" +
                super.getUsage(ctx) + " info [<gate>]\n" +
                super.getUsage(ctx) + " open [<gate>]\n" +
                super.getUsage(ctx) + " close [<gate>]\n" +
                super.getUsage(ctx) + " rebuild [<gate>]\n" +
                super.getUsage(ctx) + " rename <newname> [<gate>]\n" +
                super.getUsage(ctx) + " link add [<from>] <to> [rev]\n" +
                super.getUsage(ctx) + " link remove [<from>] <to> [rev]\n" +
                super.getUsage(ctx) + " pin add <pin> [<gate>]\n" +
                super.getUsage(ctx) + " pin remove <pin>|* [<gate>]\n" +
                super.getUsage(ctx) + " ban add <item> [<gate>]\n" +
                super.getUsage(ctx) + " ban remove <item>|* [<gate>]\n" +
                super.getUsage(ctx) + " allow add <item> [<gate>]\n" +
                super.getUsage(ctx) + " allow remove <item>|* [<gate>]\n" +
                super.getUsage(ctx) + " replace add <old> <new> [<gate>]\n" +
                super.getUsage(ctx) + " replace remove <olditem>|* [<gate>]\n" +
                super.getUsage(ctx) + " get <option>|* [<gate>]\n" +
                super.getUsage(ctx) + " set <option> <value> [<gate>]";
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        super.process(ctx, cmd, args);
        if (args.isEmpty())
            throw new CommandException("do what with a gate?");
        String subCmd = args.remove(0).toLowerCase();

        if ("list".startsWith(subCmd)) {
            ctx.requireAllPermissions("trp.gate.list");
            if (Global.gates.getAll().isEmpty())
                ctx.send("there are no gates");
            else {
                List<Gate> gates = Global.gates.getAll();
                Collections.sort(gates, new Comparator<Gate>() {
                    @Override
                    public int compare(Gate a, Gate b) {
                        return a.getFullName().compareToIgnoreCase(b.getFullName());
                    }
                });
                ctx.send("%d gates:", gates.size());
                for (Gate gate : gates)
                    ctx.send("  %s", gate.getFullName());
            }
            return;
        }

        if ("select".startsWith(subCmd)) {
            if (! ctx.isPlayer())
                throw new CommandException("this command can only be used by a player");
            LocalGate gate = getGate(ctx, args);
            ctx.requireAllPermissions("trp.gate.select." + gate.getName());
            Global.setSelectedGate(ctx.getPlayer(), gate);
            ctx.send("selected gate '%s'", gate.getFullName());
            return;
        }

        if ("info".startsWith(subCmd)) {
            LocalGate gate = getGate(ctx, args);
            ctx.requireAllPermissions("trp.gate.info." + gate.getName());
            ctx.send("Full name: %s", gate.getFullName());
            ctx.send("Design: %s", gate.getDesignName());
            ctx.send("Creator: %s", gate.getCreatorName());
            if (Utils.iconomyAvailable()) {
                if (gate.getLinkLocal())
                    ctx.send("On-world travel cost: %s/%s",
                            iConomy.format(gate.getSendLocalCost()),
                            iConomy.format(gate.getReceiveLocalCost()));
                if (gate.getLinkWorld())
                    ctx.send("Off-world travel cost: %s/%s",
                            iConomy.format(gate.getSendWorldCost()),
                            iConomy.format(gate.getReceiveWorldCost()));
                if (gate.getLinkServer())
                    ctx.send("Off-server travel cost: %s/%s",
                            iConomy.format(gate.getSendServerCost()),
                            iConomy.format(gate.getReceiveServerCost()));
            }
            List<String> links = gate.getLinks();
            ctx.send("Links: %d", links.size());
            for (String link : links)
                ctx.send(" %s%s", link.equals(gate.getDestinationLink()) ? "*": "", link);
            return;
        }

        if ("open".startsWith(subCmd)) {
            LocalGate gate = getGate(ctx, args);
            ctx.requireAllPermissions("trp.gate.open." + gate.getFullName());
            if (gate.isOpen())
                ctx.warn("gate '%s' is already open", gate.getName(ctx));
            else {
                ctx.requireAllPermissions("trp.gate.open." + gate.getName());
                gate.open();
                ctx.sendLog("opened gate '%s'", gate.getName(ctx));
            }
            return;
        }

        if ("close".startsWith(subCmd)) {
            LocalGate gate = getGate(ctx, args);
            if (gate.isOpen()) {
                ctx.requireAllPermissions("trp.gate.close." + gate.getName());
                gate.close();
                ctx.sendLog("closed gate '%s'", gate.getName(ctx));
            } else
                ctx.warn("gate '%s' is already closed", gate.getName(ctx));
            return;
        }

        if ("rebuild".startsWith(subCmd)) {
            LocalGate gate = getGate(ctx, args);
            ctx.requireAllPermissions("trp.gate.rebuild." + gate.getName());
            gate.rebuild();
            ctx.sendLog("rebuilt gate '%s'", gate.getName(ctx));
            return;
        }

        if ("rename".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("new name required");
            String newName = args.remove(0);
            LocalGate gate = getGate(ctx, args);
            String oldName = gate.getName(ctx);
            ctx.requireAllPermissions("trp.gate.rename");
            Global.gates.rename(gate, newName);
            ctx.sendLog("renamed gate '%s' to '%s'", oldName, gate.getName(ctx));
            return;
        }

        if ("link".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with a link?");
            subCmd = args.remove(0).toLowerCase();
            if (args.isEmpty())
                throw new CommandException("to gate required");
            boolean reverse = false;
            if ("reverse".startsWith(args.get(args.size() - 1).toLowerCase())) {
                reverse = true;
                args.remove(args.size() - 1);
            }
            if (args.isEmpty())
                throw new CommandException("to gate required");

            String toGateName = args.remove(args.size() - 1);
            LocalGate fromGate = getGate(ctx, args);
            Gate toGate = Global.gates.get(ctx, toGateName);
            if (toGate == null)
                throw new CommandException("unknown 'to' gate '%s'", toGateName);

            if ("add".startsWith(subCmd)) {
                ctx.requireAnyPermissions("trp.gate.link.add." + fromGate.getName());

                if (fromGate.isLinked() && (! fromGate.getMultiLink()))
                    throw new CommandException("gate '%s' cannot accept multiple links", fromGate.getName(ctx));

                if (fromGate.isSameWorld(toGate) && (! Global.config.getBoolean("allowLinkLocal", true)))
                    throw new CommandException("linking to on-world gates is not permitted");
                else if (toGate.isSameServer() && (! Global.config.getBoolean("allowLinkWorld", true)))
                    throw new CommandException("linking to off-world gates is not permitted");
                else if ((! toGate.isSameServer()) && (! Global.config.getBoolean("allowLinkServer", true)))
                    throw new CommandException("linking to remote server gates is not permitted");

                if (fromGate.isSameWorld(toGate))
                    ctx.requireFunds(fromGate.getLinkLocalCost());
                else if (toGate.isSameServer())
                    ctx.requireFunds(fromGate.getLinkWorldCost());
                else
                    ctx.requireFunds(fromGate.getLinkServerCost());

                if (! fromGate.addLink(toGate.getFullName()))
                    throw new CommandException("gate '%s' already links to '%s'", fromGate.getName(ctx), toGate.getName(ctx));

                ctx.sendLog("added link from '%s' to '%s'", fromGate.getName(ctx), toGate.getName(ctx));

                if (fromGate.isSameWorld(toGate))
                    ctx.chargeFunds(fromGate.getLinkLocalCost(), "debited $$ for on-world linking");
                else if (toGate.isSameServer())
                    ctx.chargeFunds(fromGate.getLinkWorldCost(), "debited $$ for off-world linking");
                else
                    ctx.chargeFunds(fromGate.getLinkServerCost(), "debited $$ for off-server linking");

                if (reverse && (ctx.getSender() != null))
                    Global.plugin.getServer().dispatchCommand(ctx.getSender(), "trp gate link add \"" + toGate.getFullName() + "\" \"" + fromGate.getFullName() + "\"");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                ctx.requireAnyPermissions("trp.gate.link.remove." + fromGate.getName());
                if (! fromGate.removeLink(toGate.getFullName()))
                    throw new CommandException("gate '%s' does not have a link to '%s'", fromGate.getName(ctx), toGate.getName(ctx));

                ctx.sendLog("removed link from '%s' to '%s'", fromGate.getName(ctx), toGate.getName(ctx));

                if (reverse && (ctx.getSender() != null))
                    Global.plugin.getServer().dispatchCommand(ctx.getSender(), "trp gate link remove \"" + fromGate.getFullName() + "\" \"" + toGate.getFullName() + "\"");
                return;
            }
            throw new CommandException("do what with a link?");
        }

        if ("pin".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with a pin?");
            subCmd = args.remove(0).toLowerCase();
            if (args.isEmpty())
                throw new CommandException("pin required");
            String pin = args.remove(0);
            LocalGate gate = getGate(ctx, args);

            if ("add".startsWith(subCmd)) {
                ctx.requireAllPermissions("trp.gate.pin.add." + gate.getName());
                if (gate.addPin(pin))
                    ctx.send("added pin to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("pin is already added");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                ctx.requireAllPermissions("trp.gate.pin.remove." + gate.getName());
                if (pin.equals("*")) {
                    gate.removeAllPins();
                    ctx.send("removed all pins from '%s'", gate.getName(ctx));
                } else if (gate.removePin(pin))
                    ctx.send("removed pin from '%s'", gate.getName(ctx));
                else
                    throw new CommandException("pin not found");
                return;
            }
            throw new CommandException("do what with a pin?");
        }

        if ("ban".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with a ban?");
            subCmd = args.remove(0).toLowerCase();
            if (args.isEmpty())
                throw new CommandException("item required");
            String item = args.remove(0);
            LocalGate gate = getGate(ctx, args);

            if ("add".startsWith(subCmd)) {
                ctx.requireAllPermissions("trp.gate.ban.add." + gate.getName());
                if (gate.addBannedItem(item))
                    ctx.send("added banned item to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("item is already banned");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                ctx.requireAllPermissions("trp.gate.ban.remove." + gate.getName());
                if (item.equals("*")) {
                    gate.removeAllBannedItems();
                    ctx.send("removed all banned items from '%s'", gate.getName(ctx));
                } else if (gate.removeBannedItem(item))
                    ctx.send("removed banned item from '%s'", gate.getName(ctx));
                else
                    throw new CommandException("banned item not found");
                return;
            }
            throw new CommandException("do what with a ban?");
        }

        if ("allow".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with an allow?");
            subCmd = args.remove(0).toLowerCase();
            if (args.isEmpty())
                throw new CommandException("item required");
            String item = args.remove(0);
            LocalGate gate = getGate(ctx, args);

            if ("add".startsWith(subCmd)) {
                ctx.requireAllPermissions("trp.gate.allow.add." + gate.getName());
                if (gate.addAllowedItem(item))
                    ctx.send("added allowed item to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("item is already allowed");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                ctx.requireAllPermissions("trp.gate.allow.remove." + gate.getName());
                if (item.equals("*")) {
                    gate.removeAllAllowedItems();
                    ctx.send("removed all allowed items from '%s'", gate.getName(ctx));
                } else if (gate.removeAllowedItem(item))
                    ctx.send("removed allowed item from '%s'", gate.getName(ctx));
                else
                    throw new CommandException("allowed item not found");
                return;
            }
            throw new CommandException("do what with an allow?");
        }

        if ("replace".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with a replace?");
            subCmd = args.remove(0).toLowerCase();
            if (args.isEmpty())
                throw new CommandException("item required");
            String oldItem = args.remove(0);

            if ("add".startsWith(subCmd)) {
                if (args.isEmpty())
                    throw new CommandException("new item required");
                String newItem = args.remove(0);
                LocalGate gate = getGate(ctx, args);
                ctx.requireAllPermissions("trp.gate.replace.add." + gate.getName());
                if (gate.addReplaceItem(oldItem, newItem))
                    ctx.send("added replace item to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("item is already replaced");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                LocalGate gate = getGate(ctx, args);
                ctx.requireAllPermissions("trp.gate.replace.remove." + gate.getName());
                if (oldItem.equals("*")) {
                    gate.removeAllReplaceItems();
                    ctx.send("removed all replace items from '%s'", gate.getName(ctx));
                } else if ( gate.removeReplaceItem(oldItem))
                    ctx.send("removed replace item from '%s'", gate.getName(ctx));
                else
                    throw new CommandException("replace item not found");
                return;
            }
            throw new CommandException("do what with a replace?");
        }

        if (("get".startsWith(subCmd)) ||
            ("set".startsWith(subCmd))) {

            String option = args.remove(0);
            String value = null;

            if ("set".startsWith(subCmd)) {
                if (args.isEmpty())
                    throw new CommandException("missing option value");
                value = args.remove(0);
            }
            LocalGate gate = getGate(ctx, args);

            if ("set".startsWith(subCmd)) {
                option = gate.resolveOption(option);
                ctx.requireAllPermissions("trp.gate.option.set." + gate.getName() + "." + option);
                gate.setOption(option, value);
                ctx.sendLog("option '%s' set to '%s' for gate '%s'", option, value, gate.getName(ctx));
            } else if ("get".startsWith(subCmd)) {
                List<String> options = new ArrayList<String>();
                if (option.equals("*")) option = ".*";
                for (String name : LocalGate.OPTIONS)
                    try {
                        if ((name.matches(option)) &&
                            ctx.hasAllPermissions("trp.gate.option.get." + gate.getName() + "." + name))
                        options.add(name);
                    } catch (PatternSyntaxException e) {}
                if (options.isEmpty())
                    throw new CommandException("no options match");
                Collections.sort(options);
                for (String name : options) {
                    if (! ctx.hasAllPermissions("trp.gate.option.get." + gate.getName() + "." + name)) continue;
                    ctx.send("%s=%s", name, gate.getOption(name));
                }
            }
            return;
        }

        throw new CommandException("do what with a gate?");
    }

    private LocalGate getGate(Context ctx, List<String> args) throws CommandException {
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
        if (! gate.isSameServer())
            throw new CommandException("this command cannot be used on a remote gate");
        return (LocalGate)gate;
    }

    /*
    private LocalGate getExplicitGate(Context ctx, List<String> args) throws CommandException {
        Gate gate = null;
        if (! args.isEmpty()) {
            gate = Global.gates.get(ctx, args.get(0));
            if (gate == null)
                throw new CommandException("unknown gate '%s'", args.get(0));
            args.remove(0);
        }
        if (gate == null)
            throw new CommandException("gate name required");
        if (! gate.isSameServer())
            throw new CommandException("this command cannot be used on a remote gate");
        return (LocalGate)gate;
    }

    private LocalGate getSelectedGate(Context ctx) throws CommandException {
        Gate gate = null;
        if (ctx.isPlayer())
            gate = Global.getSelectedGate(ctx.getPlayer());
        if (gate == null)
            throw new CommandException("gate name required");
        return (LocalGate)gate;
    }
*/

}
