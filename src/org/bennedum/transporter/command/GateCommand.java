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
import org.bennedum.transporter.Economy;
import org.bennedum.transporter.Gate;
import org.bennedum.transporter.Gates;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.LocalGate;
import org.bennedum.transporter.Permissions;
import org.bennedum.transporter.RemoteGate;
import org.bennedum.transporter.Reservation;
import org.bennedum.transporter.ReservationException;
import org.bennedum.transporter.Server;
import org.bennedum.transporter.Servers;
import org.bennedum.transporter.TransporterException;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class GateCommand extends TrpCommandProcessor {

    private static final String GROUP = "gate ";

    @Override
    public boolean matches(Context ctx, Command cmd, List<String> args) {
        return super.matches(ctx, cmd, args) &&
               GROUP.startsWith(args.get(0).toLowerCase());
    }

    @Override
    public List<String> getUsage(Context ctx) {
        List<String> cmds = new ArrayList<String>();
        cmds.add(getPrefix(ctx) + GROUP + "list");
        cmds.add(getPrefix(ctx) + GROUP + "select <gate>");
        cmds.add(getPrefix(ctx) + GROUP + "info [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "open [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "close [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "rebuild [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "destroy [<gate>] [unbuild]");
        cmds.add(getPrefix(ctx) + GROUP + "rename <newname> [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "link add [<from>] <to> [rev]");
        cmds.add(getPrefix(ctx) + GROUP + "link remove [<from>] <to> [rev]");
        cmds.add(getPrefix(ctx) + GROUP + "pin add <pin> [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "pin remove <pin>|* [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "ban add <item> [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "ban remove <item>|* [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "allow add <item> [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "allow remove <item>|* [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "replace add <old> <new> [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "replace remove <olditem>|* [<gate>]");
        if (ctx.isPlayer())
            cmds.add(getPrefix(ctx) + GROUP + "go [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "get <option>|* [<gate>]");
        cmds.add(getPrefix(ctx) + GROUP + "set <option> <value> [<gate>]");
        return cmds;
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        args.remove(0);
        if (args.isEmpty())
            throw new CommandException("do what with a gate?");
        String subCmd = args.remove(0).toLowerCase();

        if ("list".startsWith(subCmd)) {
            Permissions.require(ctx.getPlayer(), "trp.gate.list");
            if (Gates.getAll().isEmpty())
                ctx.send("there are no gates");
            else {
                List<Gate> gates = Gates.getAll();
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
            LocalGate gate = getGate(ctx, args);
            Permissions.require(ctx.getPlayer(), "trp.gate.select." + gate.getName());
            Global.setSelectedGate(ctx.getPlayer(), gate);
            ctx.send("selected gate '%s'", gate.getFullName());
            return;
        }

        if ("info".startsWith(subCmd)) {
            LocalGate gate = getGate(ctx, args);
            Permissions.require(ctx.getPlayer(), "trp.gate.info." + gate.getName());
            ctx.send("Full name: %s", gate.getFullName());
            ctx.send("Design: %s", gate.getDesignName());
            ctx.send("Creator: %s", gate.getCreatorName());
            if (Economy.isAvailable()) {
                if (gate.getLinkLocal())
                    ctx.send("On-world travel cost: %s/%s",
                            Economy.format(gate.getSendLocalCost()),
                            Economy.format(gate.getReceiveLocalCost()));
                if (gate.getLinkWorld())
                    ctx.send("Off-world travel cost: %s/%s",
                            Economy.format(gate.getSendWorldCost()),
                            Economy.format(gate.getReceiveWorldCost()));
                if (gate.getLinkServer())
                    ctx.send("Off-server travel cost: %s/%s",
                            Economy.format(gate.getSendServerCost()),
                            Economy.format(gate.getReceiveServerCost()));
            }
            List<String> links = gate.getLinks();
            ctx.send("Links: %d", links.size());
            for (String link : links)
                ctx.send(" %s%s", link.equals(gate.getDestinationLink()) ? "*": "", link);
            return;
        }

        if ("open".startsWith(subCmd)) {
            LocalGate gate = getGate(ctx, args);
            if (gate.isOpen())
                ctx.warn("gate '%s' is already open", gate.getName(ctx));
            else {
                Permissions.require(ctx.getPlayer(), "trp.gate.open." + gate.getName());
                gate.open();
                ctx.sendLog("opened gate '%s'", gate.getName(ctx));
            }
            return;
        }

        if ("close".startsWith(subCmd)) {
            LocalGate gate = getGate(ctx, args);
            if (gate.isOpen()) {
                Permissions.require(ctx.getPlayer(), "trp.gate.close." + gate.getName());
                gate.close();
                ctx.sendLog("closed gate '%s'", gate.getName(ctx));
            } else
                ctx.warn("gate '%s' is already closed", gate.getName(ctx));
            return;
        }

        if ("rebuild".startsWith(subCmd)) {
            LocalGate gate = getGate(ctx, args);
            Permissions.require(ctx.getPlayer(), "trp.gate.rebuild." + gate.getName());
            gate.rebuild();
            ctx.sendLog("rebuilt gate '%s'", gate.getName(ctx));
            return;
        }

        if ("destroy".startsWith(subCmd)) {
            boolean unbuild = false;
            if ("unbuild".startsWith(args.get(args.size() - 1).toLowerCase())) {
                unbuild = true;
                args.remove(args.size() - 1);
            }
            LocalGate gate = getGate(ctx, args);
            Permissions.require(ctx.getPlayer(), "trp.gate.destroy." + gate.getName());
            Gates.destroy(gate, unbuild);
            ctx.sendLog("destroyed gate '%s'", gate.getName(ctx));
            return;
        }

        if ("rename".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("new name required");
            String newName = args.remove(0);
            LocalGate gate = getGate(ctx, args);
            String oldName = gate.getName(ctx);
            Permissions.require(ctx.getPlayer(), "trp.gate.rename");
            Gates.rename(gate, newName);
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
            Gate toGate = Gates.get(ctx, toGateName);
            if ((toGate == null) && (! "remove".startsWith(subCmd)))
                throw new CommandException("unknown 'to' gate '%s'", toGateName);

            if ("add".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.link.add." + fromGate.getName());

                if (fromGate.isLinked() && (! fromGate.getMultiLink()))
                    throw new CommandException("gate '%s' cannot accept multiple links", fromGate.getName(ctx));

                if (fromGate.isSameWorld(toGate) && (! Config.getAllowLinkLocal()))
                    throw new CommandException("linking to on-world gates is not permitted");
                else if (toGate.isSameServer() && (! Config.getAllowLinkWorld()))
                    throw new CommandException("linking to off-world gates is not permitted");
                else if ((! toGate.isSameServer()) && (! Config.getAllowLinkServer()))
                    throw new CommandException("linking to remote server gates is not permitted");

                if (fromGate.isSameWorld(toGate))
                    Economy.requireFunds(ctx.getPlayer(), fromGate.getLinkLocalCost());
                else if (toGate.isSameServer())
                    Economy.requireFunds(ctx.getPlayer(), fromGate.getLinkWorldCost());
                else
                    Economy.requireFunds(ctx.getPlayer(), fromGate.getLinkServerCost());

                if (! fromGate.addLink(toGate.getFullName()))
                    throw new CommandException("gate '%s' already links to '%s'", fromGate.getName(ctx), toGate.getName(ctx));

                ctx.sendLog("added link from '%s' to '%s'", fromGate.getName(ctx), toGate.getName(ctx));

                if (fromGate.isSameWorld(toGate) && Economy.deductFunds(ctx.getPlayer(), fromGate.getLinkLocalCost()))
                    ctx.sendLog("debited %s for on-world linking", Economy.format(fromGate.getLinkLocalCost()));
                else if (toGate.isSameServer() && Economy.deductFunds(ctx.getPlayer(), fromGate.getLinkWorldCost()))
                    ctx.sendLog("debited %s for off-world linking", Economy.format(fromGate.getLinkWorldCost()));
                else if (Economy.deductFunds(ctx.getPlayer(), fromGate.getLinkServerCost()))
                    ctx.sendLog("debited %s for off-server linking", Economy.format(fromGate.getLinkServerCost()));

                if (reverse && (ctx.getSender() != null)) {
                    if (toGate.isSameServer())
                        Global.plugin.getServer().dispatchCommand(ctx.getSender(), "trp gate link add \"" + toGate.getFullName() + "\" \"" + fromGate.getFullName() + "\"");
                    else {
                        Server server = Servers.get(toGate.getServerName());
                        if (server == null)
                            ctx.send("unable to add reverse link from unknown or offline server");
                        else
                            server.doAddLink(ctx.getPlayer(), (LocalGate)fromGate, (RemoteGate)toGate);
                    }
                }
                return;
            }

            if ("remove".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.link.remove." + fromGate.getName());
                if (toGate != null) toGateName = toGate.getFullName();

                if (! fromGate.removeLink(toGateName))
                    throw new CommandException("gate '%s' does not have a link to '%s'", fromGate.getName(ctx), toGate.getName(ctx));

                ctx.sendLog("removed link from '%s' to '%s'", fromGate.getName(ctx), toGateName);

                if (reverse && (ctx.getSender() != null) && (toGate != null)) {
                    if (toGate.isSameServer())
                        Global.plugin.getServer().dispatchCommand(ctx.getSender(), "trp gate link remove \"" + fromGate.getFullName() + "\" \"" + toGate.getFullName() + "\"");
                    else {
                        Server server = Servers.get(toGate.getServerName());
                        if (server == null)
                            ctx.send("unable to remove reverse link from unknown or offline server");
                        else
                            server.doRemoveLink(ctx.getPlayer(), (LocalGate)fromGate, (RemoteGate)toGate);
                    }
                }
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
                Permissions.require(ctx.getPlayer(), "trp.gate.pin.add." + gate.getName());
                if (gate.addPin(pin))
                    ctx.send("added pin to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("pin is already added");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.pin.remove." + gate.getName());
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
                Permissions.require(ctx.getPlayer(), "trp.gate.ban.add." + gate.getName());
                if (gate.addBannedItem(item))
                    ctx.send("added banned item to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("item is already banned");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.ban.remove." + gate.getName());
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
                Permissions.require(ctx.getPlayer(), "trp.gate.allow.add." + gate.getName());
                if (gate.addAllowedItem(item))
                    ctx.send("added allowed item to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("item is already allowed");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.gate.allow.remove." + gate.getName());
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
                Permissions.require(ctx.getPlayer(), "trp.gate.replace.add." + gate.getName());
                if (gate.addReplaceItem(oldItem, newItem))
                    ctx.send("added replace item to '%s'", gate.getName(ctx));
                else
                    throw new CommandException("item is already replaced");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                LocalGate gate = getGate(ctx, args);
                Permissions.require(ctx.getPlayer(), "trp.gate.replace.remove." + gate.getName());
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

        if ("set".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("option name required");
            String option = args.remove(0);
            if (args.isEmpty())
                throw new CommandException("option value required");
            String value = args.remove(0);
            LocalGate gate = getGate(ctx, args);
            gate.setOption(ctx, option, value);
            return;
        }

        if ("get".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("option name required");
            String option = args.remove(0);
            LocalGate gate = getGate(ctx, args);
            gate.getOptions(ctx, option);
            return;
        }

        if ("go".startsWith(subCmd)) {
            if (! ctx.isPlayer())
                throw new CommandException("this command can only be used by a player");
            Gate gate = null;
            if (! args.isEmpty()) {
                String name = args.remove(0);
                gate = Gates.get(ctx, name);
                if (gate == null)
                    throw new CommandException("unknown gate '%s'", name);
            } else
                gate = Global.getSelectedGate(ctx.getPlayer());
            if (gate == null)
                throw new CommandException("gate name required");

            Permissions.require(ctx.getPlayer(), "trp.gate.go." + gate.getName());
            try {
                Reservation r = new Reservation(ctx.getPlayer(), gate);
                r.depart();
            } catch (ReservationException e) {
                ctx.warnLog(e.getMessage());
            }
            return;
        }

        throw new CommandException("do what with a gate?");
    }

    private LocalGate getGate(Context ctx, List<String> args) throws CommandException {
        Gate gate = null;
        if (! args.isEmpty()) {
            gate = Gates.get(ctx, args.get(0));
            if (gate == null)
                throw new CommandException("unknown gate '%s'", args.get(0));
            args.remove(0);
        } else
            gate = Global.getSelectedGate(ctx.getPlayer());
        if (gate == null)
            throw new CommandException("gate name required");
        if (! gate.isSameServer())
            throw new CommandException("this command cannot be used on a remote gate");
        return (LocalGate)gate;
    }

}
