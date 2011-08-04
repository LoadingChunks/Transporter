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
import org.bennedum.transporter.ServerException;
import org.bennedum.transporter.Servers;
import org.bennedum.transporter.TransporterException;
import org.bennedum.transporter.Utils;
import org.bennedum.transporter.net.Network;
import org.bennedum.transporter.net.NetworkException;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class ServerCommand extends TrpCommandProcessor {

    private static final String GROUP = "server ";
    
    @Override
    public boolean matches(Context ctx, Command cmd, List<String> args) {
        return super.matches(ctx, cmd, args) &&
               GROUP.startsWith(args.get(0).toLowerCase());
    }
    
    @Override
    public List<String> getUsage(Context ctx) {
        List<String> cmds = new ArrayList<String>();
        cmds.add(getPrefix(ctx) + GROUP + "list");
        cmds.add(getPrefix(ctx) + GROUP + "add <name> <plgAddr> <key>");
        cmds.add(getPrefix(ctx) + GROUP + "connect <server>");
        cmds.add(getPrefix(ctx) + GROUP + "disconnect <server>");
        cmds.add(getPrefix(ctx) + GROUP + "enable <server>");
        cmds.add(getPrefix(ctx) + GROUP + "disable <server>");
        cmds.add(getPrefix(ctx) + GROUP + "ping <server> [<timeout>]");
        cmds.add(getPrefix(ctx) + GROUP + "refresh <server>");
        cmds.add(getPrefix(ctx) + GROUP + "remove <server>");
        cmds.add(getPrefix(ctx) + GROUP + "ban add <pattern>");
        cmds.add(getPrefix(ctx) + GROUP + "ban remove <pattern>|*");
        cmds.add(getPrefix(ctx) + GROUP + "ban list");
        cmds.add(getPrefix(ctx) + GROUP + "get <option>|* [<server>]");
        cmds.add(getPrefix(ctx) + GROUP + "set <option> <value> [<server>]");
        return cmds;
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        args.remove(0);
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
                for (Server server : servers) {
                    ctx.send("  %s: %s '%s' %s/%s",
                                server.getName(),
                                server.getPluginAddress(),
                                server.getKey(),
                                (server.isEnabled() ? "up" : "down"),
                                (! server.isConnected() ? "down" :
                                    String.format("up %s %s v%s",
                                        server.isIncoming() ? "incoming" : "outgoing",
                                        server.getConnection().getName(),
                                        server.getRemoteVersion()))
                            );
                    ctx.send("    publicAddress:        %s (%s)",
                            server.getPublicAddress(),
                            server.getNormalizedPublicAddress()
                            );
                    ctx.send("    privateAddress:       %s",
                            server.getPrivateAddress().equals("-") ?
                                "-" :
                                String.format("%s (%s:%d)",
                                    server.getPrivateAddress(),
                                    server.getNormalizedPrivateAddress().getAddress().getHostAddress(),
                                    server.getNormalizedPrivateAddress().getPort()));
                    if (server.isConnected()) {
                        ctx.send("    remotePublicAddress:  %s",
                                server.getRemotePublicAddress());
                        ctx.send("    remotePrivateAddress: %s",
                                (server.getRemotePrivateAddress() == null) ?
                                    "-" : server.getRemotePrivateAddress());
                        ctx.send("    remoteCluster:        %s",
                                (server.getRemoteCluster() == null) ?
                                    "-" : server.getRemoteCluster());
                    }
                }
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
                // network setting
                option = Global.network.resolveOption(option);
                Permissions.require(ctx.getPlayer(), "trp.network.option.set." + option);
                Global.network.setOption(option, value);
                ctx.sendLog("network option '%s' set to '%s'", option, value);
                ctx.sendLog("reload the server for the changes to take effect");
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
                // network setting
                List<String> options = new ArrayList<String>();
                try {
                    String opt = Global.network.resolveOption(option);
                    Permissions.require(ctx.getPlayer(), "trp.network.option.get." + opt);
                    options.add(opt);
                } catch (NetworkException e) {}
                if (options.isEmpty()) {
                    if (option.equals("*")) option = ".*";
                    for (String opt : Network.OPTIONS)
                        try {
                            if ((opt.matches(option)) &&
                                Permissions.has(ctx.getPlayer(), "trp.network.option.get." + opt))
                            options.add(opt);
                        } catch (PatternSyntaxException e) {}
                }
                if (options.isEmpty())
                    throw new CommandException("no options match");
                Collections.sort(options);
                for (String opt : options) {
                    if (! Permissions.has(ctx.getPlayer(), "trp.network.option.get." + opt)) continue;
                    ctx.send("%s=%s", opt, Global.network.getOption(opt));
                }
            } else {
                // server specific
                String name = args.remove(0);
                Server server = Servers.get(name);
                if (server == null)
                    throw new CommandException("unknown server '%s'", name);
                
                List<String> options = new ArrayList<String>();
                try {
                    String opt = server.resolveOption(option);
                    Permissions.require(ctx.getPlayer(), "trp.server.option.get." + opt);
                    options.add(opt);
                } catch (ServerException e) {}
                if (options.isEmpty()) {
                    if (option.equals("*")) option = ".*";
                    for (String opt : Server.OPTIONS)
                        try {
                            if ((opt.matches(option)) &&
                                Permissions.has(ctx.getPlayer(), "trp.server.option.get." + opt))
                            options.add(opt);
                        } catch (PatternSyntaxException e) {}
                }
                if (options.isEmpty())
                    throw new CommandException("no options match");
                Collections.sort(options);
                for (String opt : options) {
                    if (! Permissions.has(ctx.getPlayer(), "trp.server.option.get." + opt)) continue;
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
