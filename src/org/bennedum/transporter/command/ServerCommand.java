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
import java.util.regex.PatternSyntaxException;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.Permissions;
import org.bennedum.transporter.Server;
import org.bennedum.transporter.Servers;
import org.bennedum.transporter.TransporterException;
import org.bennedum.transporter.Utils;
import org.bennedum.transporter.net.Network;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class ServerCommand extends TrpCommandProcessor {

    @Override
    protected String[] getSubCommands() { return new String[] {"server"}; }

    @Override
    public String getUsage(Context ctx) {
        return
               super.getUsage(ctx) + " set listen <address>\n" +
               super.getUsage(ctx) + " get listen\n" +
               super.getUsage(ctx) + " set key <key>\n" +
               super.getUsage(ctx) + " get key\n" +
               super.getUsage(ctx) + " list\n" +
               super.getUsage(ctx) + " add <name> <plgAddr> <key>\n" +
               super.getUsage(ctx) + " connect <server>\n" +
               super.getUsage(ctx) + " disconnect <server>\n" +
               super.getUsage(ctx) + " enable <server>\n" +
               super.getUsage(ctx) + " disable <server>\n" +
               super.getUsage(ctx) + " ping <server> [<timeout>]\n" +
               super.getUsage(ctx) + " refresh <server>\n" +
               super.getUsage(ctx) + " remove <server>\n" +
               super.getUsage(ctx) + " change <server> <plgAddr> <key>\n" +
               super.getUsage(ctx) + " ban add <pattern>\n" +
               super.getUsage(ctx) + " ban remove <pattern>|*\n" +
               super.getUsage(ctx) + " ban list\n" +
               super.getUsage(ctx) + " get <option>|* <server>\n" +
               super.getUsage(ctx) + " set <option> <value> <server>";
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        super.process(ctx, cmd, args);
        if (args.isEmpty())
            throw new CommandException("do what with a server?");
        String subCmd = args.get(0).toLowerCase();
        args.remove(0);

        if ("list".startsWith(subCmd)) {
            Permissions.require(ctx.getPlayer(), "trp.server.list");
            if (Servers.getAll().isEmpty())
                ctx.send("there are no servers");
            else {
                List<Server> servers = Servers.getAll();
                Collections.sort(servers, new Comparator<Server>() {
                    @Override
                    public int compare(Server a, Server b) {
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                ctx.send("%d servers:", servers.size());
                // TODO: move stuff from this to new status command
                for (Server server : servers)
                    ctx.send("  %s: %s '%s' [%s] [%s] %s/%s %s %s %s",
                                server.getName(),
                                server.getPluginAddress(),
                                server.getKey(),
                                (server.getPublicAddress() == null) ? "*" : server.getPublicAddress(),
                                (server.getPrivateAddress() == null) ? "*" : server.getPrivateAddress(),
                                (server.isEnabled() ? "up" : "down"),
                                (server.isConnected() ? "up" : "down"),
                                (server.isConnected() ? (server.isIncoming() ? "incoming" : "outgoing") : ""),
                                (server.isConnected() ? server.getConnection().getName() : ""),
                                (server.isConnected() ? "v" + server.getVersion() : "")
                            );
            }
            return;
        }

        if ("set".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("set what?");
            String option = args.remove(0).toLowerCase();
            if (args.isEmpty())
                throw new CommandException("set %s to what?", option);
            String value = args.remove(0);
            
            if (args.isEmpty()) {
                // global setting
                if ("listen".startsWith(option)) {
                    Permissions.require(ctx.getPlayer(), "trp.server.set.listen");
                    Network.makeAddress(value);
                    Global.config.setProperty("listenAddress", value);
                    option = "listen";
                } else if ("key".startsWith(option)) {
                    Permissions.require(ctx.getPlayer(), "trp.server.set.key");
                    Global.config.setProperty("serverKey", value);
                    option = "key";
                } else
                    throw new CommandException("set what?");
                ctx.sendLog("set %s to %s", option, value);
            } else {
                // server specific
                String name = args.remove(0);
                Server server = Servers.get(name);
                if (server == null)
                    throw new CommandException("unknown server '%s'", name);
                option = server.resolveOption(option);
                Permissions.require(ctx.getPlayer(), "trp.server.option.set." + option);
                server.setOption(option, value);
                ctx.sendLog("option '%s' set to '%s' for server '%s'", option, value, server.getName());
            }
            Utils.saveConfig(ctx);
            return;
        }

        if ("get".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("get what?");
            String option = args.remove(0).toLowerCase();
            
            if (args.isEmpty()) {
                // global setting
                if ("listen".startsWith(option)) {
                    Permissions.require(ctx.getPlayer(), "trp.server.get.listen");
                    ctx.sendLog("listen=%s", Global.network.getListenAddress());
                } else if ("key".startsWith(option)) {
                    Permissions.require(ctx.getPlayer(), "trp.server.get.key");
                    ctx.sendLog("key=%s", Global.network.getKey());
                } else
                    throw new CommandException("get what?");
            } else {
                // server specific
                String name = args.remove(0);
                Server server = Servers.get(name);
                if (server == null)
                    throw new CommandException("unknown server '%s'", name);
                
                List<String> options = new ArrayList<String>();
                if (option.equals("*")) option = ".*";
                for (String opt : Server.OPTIONS)
                    try {
                        if ((opt.matches(option)) &&
                            Permissions.has(ctx.getPlayer(), "trp.server.option.get." + opt))
                        options.add(opt);
                    } catch (PatternSyntaxException e) {}
                if (options.isEmpty())
                    throw new CommandException("no options match");
                Collections.sort(options);
                for (String opt : options) {
                    if (! Permissions.has(ctx.getPlayer(), "trp.gate.option.get." + opt)) continue;
                    ctx.send("%s=%s", opt, server.getOption(opt));
                }
            }
            return;
        }

        if ("add".startsWith(subCmd)) {
            if (args.size() < 3)
                throw new CommandException("server name, address, and key required");
            Permissions.require(ctx.getPlayer(), "trp.server.add");
            String name = args.remove(0);
            String plgAddr = args.remove(0);
            String key = args.remove(0);
            Server server = new Server(name, plgAddr, key);
            Servers.add(server);
            Servers.saveAll();
            Utils.saveConfig(ctx);
            ctx.sendLog("added server '%s'", server.getName());
            return;
        }

        if ("connect".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            Permissions.require(ctx.getPlayer(), "trp.server.connect");
            if (server.isConnected())
                ctx.sendLog("server '%s' is already connected", server.getName());
            else {
                ctx.sendLog("requested server connect for '%s'", server.getName());
                server.connect();
            }
            return;
        }

        if ("disconnect".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            Permissions.require(ctx.getPlayer(), "trp.server.disconnect");
            ctx.sendLog("requested server disconnect for '%s'", server.getName());
            server.disconnect(false);
            return;
        }

        if ("enable".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            Permissions.require(ctx.getPlayer(), "trp.server.enable");
            server.setEnabled(true);
            Servers.saveAll();
            Utils.saveConfig(ctx);
            ctx.sendLog("server '%s' enabled", server.getName());
            return;
        }

        if ("disable".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            Permissions.require(ctx.getPlayer(), "trp.server.disable");
            server.setEnabled(false);
            Servers.saveAll();
            Utils.saveConfig(ctx);
            ctx.sendLog("server '%s' disabled", server.getName());
            return;
        }

        if ("ping".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            args.remove(0);
            long timeout = 5000;
            if (! args.isEmpty())
                try {
                    timeout = Long.parseLong(args.get(0));
                } catch (IllegalArgumentException e) {
                    ctx.send("'%s' is not a valid number of millis", args.get(0));
                    return;
                }
            Permissions.require(ctx.getPlayer(), "trp.server.ping");
            if (! server.isEnabled())
                throw new CommandException("server '%s' is not enabled", server.getName());
            if (! server.isConnected())
                throw new CommandException("server '%s' is not connected", server.getName());
            server.doPing(ctx, timeout);
            ctx.send("pinging '%s'...", server.getName());
            return;
        }

        if ("change".startsWith(subCmd)) {
            if (args.size() < 3)
                throw new CommandException("server name, address, and key required");
            Permissions.require(ctx.getPlayer(), "trp.server.change");
            String name = args.remove(0);
            String plgAddr = args.remove(0);
            String key = args.remove(0);
            Server server = Servers.get(name);
            if (server == null)
                throw new CommandException("unknown server '%s'", name);
            server.change(plgAddr, key);
            Servers.saveAll();
            Utils.saveConfig(ctx);
            ctx.sendLog("changed server '%s'", server.getName());
            ctx.sendLog("server must be reconnected for change to take effect");
            return;
        }

        if ("refresh".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            Permissions.require(ctx.getPlayer(), "trp.server.refresh");
            if (! server.isConnected())
                ctx.sendLog("server '%s' is not connected", server.getName());
            else {
                server.refresh();
                ctx.sendLog("requested server refresh for '%s'", server.getName());
            }
            return;
        }

        if ("remove".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            Permissions.require(ctx.getPlayer(), "trp.server.remove");
            Servers.remove(server);
            Servers.saveAll();
            Utils.saveConfig(ctx);
            ctx.sendLog("removed server '%s'", server.getName());
            return;
        }

        if ("ban".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with bans?");
            subCmd = args.remove(0).toLowerCase();

            if ("list".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.server.ban.list");
                List<String> banned = Global.network.getBanned();
                if (banned.isEmpty())
                    ctx.send("there are no banned servers");
                else {
                    ctx.send("%d banned servers:", banned.size());
                    for (String pattern : banned)
                        ctx.send("  %s", pattern);
                }
                return;
            }

            if (args.isEmpty())
                throw new CommandException("address pattern required");
            String pattern = args.remove(0);

            if ("add".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.server.ban.add");
                if (Global.network.addBan(args.get(0)))
                    ctx.sendLog("added ban");
                else
                    throw new CommandException("'%s' is already banned");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                Permissions.require(ctx.getPlayer(), "trp.server.ban.remove");
                if (pattern.equals("*")) {
                    Global.network.removeAllBans();
                    ctx.sendLog("removed all bans");
                } else if (Global.network.removeBan(args.get(0)))
                    ctx.sendLog("removed ban");
                else
                    throw new CommandException("'%s' is not banned");
                return;
            }
            throw new CommandException("do what with a ban?");
        }

        throw new CommandException("do what with a server?");
    }

}
