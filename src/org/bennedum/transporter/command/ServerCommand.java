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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.Server;
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
               super.getUsage(ctx) + " add <name> <address> <key> <mcAddr>\n" +
               super.getUsage(ctx) + " connect <server>\n" +
               super.getUsage(ctx) + " disconnect <server>\n" +
               super.getUsage(ctx) + " enable <server>\n" +
               super.getUsage(ctx) + " disable <server>\n" +
               super.getUsage(ctx) + " ping <server> [<timeout>]\n" +
               super.getUsage(ctx) + " refresh <server>\n" +
               super.getUsage(ctx) + " remove <server>\n" +
               super.getUsage(ctx) + " change <server> <address> <key> <mcAddr>\n" +
               super.getUsage(ctx) + " ban add <pattern>\n" +
               super.getUsage(ctx) + " ban remove <pattern>|*\n" +
               super.getUsage(ctx) + " ban list";
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        super.process(ctx, cmd, args);
        if (args.isEmpty())
            throw new CommandException("do what with a server?");
        String subCmd = args.get(0).toLowerCase();
        args.remove(0);

        if ("list".startsWith(subCmd)) {
            ctx.requireAllPermissions("trp.server.list");
            if (Global.servers.getAll().isEmpty())
                ctx.send("there are no servers");
            else {
                List<Server> servers = Global.servers.getAll();
                Collections.sort(servers, new Comparator<Server>() {
                    @Override
                    public int compare(Server a, Server b) {
                        return a.getName().compareToIgnoreCase(b.getName());
                    }
                });
                ctx.send("%d servers:", servers.size());
                for (Server server : servers)
                    ctx.send("  %s: %s '%s' [%s] %s/%s %s %s %s",
                                server.getName(),
                                server.getPluginAddress(),
                                server.getKey(),
                                (server.getMCAddress() == null) ? "*" : server.getMCAddress(),
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
            String what = args.remove(0).toLowerCase();
            if (args.isEmpty())
                throw new CommandException("set %s to what?", what);
            if ("listen".startsWith(what)) {
                ctx.requireAllPermissions("trp.server.set.listen");
                Network.makeAddress(args.get(0));
                Global.config.setProperty("listenAddress", args.get(0));
                what = "listen";
            } else if ("key".startsWith(what)) {
                ctx.requireAllPermissions("trp.server.set.key");
                Global.config.setProperty("serverKey", args.get(0));
                what = "key";
            } else
                throw new CommandException("set what?");
            Utils.saveConfig(ctx);
            ctx.sendLog("set %s to %s", what, args.get(0));
            ctx.send("reload server for changes to take effect");
            return;
        }

        if ("get".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("get what?");
            String what = args.remove(0).toLowerCase();
            if ("listen".startsWith(what)) {
                ctx.requireAllPermissions("trp.server.get.listen");
                ctx.sendLog("listen=%s", Global.network.getListenAddress());
            } else if ("key".startsWith(what)) {
                ctx.requireAllPermissions("trp.server.get.key");
                ctx.sendLog("key=%s", Global.network.getKey());
            } else
                throw new CommandException("get what?");
            return;
        }

        if ("add".startsWith(subCmd)) {
            if (args.size() < 3)
                throw new CommandException("server name, address, and key required");
            ctx.requireAllPermissions("trp.server.add");
            Server server = new Server(args.get(0), args.get(1), args.get(2), (args.size() > 3) ? args.get(3) : null);
            Global.servers.add(server);
            Global.servers.saveAll();
            Utils.saveConfig(ctx);
            ctx.sendLog("added server '%s'", server.getName());
            return;
        }

        if ("connect".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Global.servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            ctx.requireAllPermissions("trp.server.connect");
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
            Server server = Global.servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            ctx.requireAllPermissions("trp.server.disconnect");
            ctx.sendLog("requested server disconnect for '%s'", server.getName());
            server.disconnect(false);
            return;
        }

        if ("enable".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Global.servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            ctx.requireAllPermissions("trp.server.enable");
            server.setEnabled(true);
            Global.servers.saveAll();
            Utils.saveConfig(ctx);
            ctx.sendLog("server '%s' enabled", server.getName());
            return;
        }

        if ("disable".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Global.servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            ctx.requireAllPermissions("trp.server.disable");
            server.setEnabled(false);
            Global.servers.saveAll();
            Utils.saveConfig(ctx);
            ctx.sendLog("server '%s' disabled", server.getName());
            return;
        }

        if ("ping".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Global.servers.get(args.get(0));
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
            ctx.requireAllPermissions("trp.server.ping");
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
            ctx.requireAllPermissions("trp.server.change");
            Server server = Global.servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            server.change(args.get(1), args.get(2), (args.size() > 3) ? args.get(3) : null);
            Global.servers.saveAll();
            Utils.saveConfig(ctx);
            ctx.sendLog("changed server '%s'", server.getName());
            ctx.sendLog("server must be reconnected for change to take effect");
            return;
        }

        if ("refresh".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("server name required");
            Server server = Global.servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            ctx.requireAllPermissions("trp.server.refresh");
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
            Server server = Global.servers.get(args.get(0));
            if (server == null)
                throw new CommandException("unknown server '%s'", args.get(0));
            ctx.requireAllPermissions("trp.server.remove");
            Global.servers.remove(server);
            Global.servers.saveAll();
            Utils.saveConfig(ctx);
            ctx.sendLog("removed server '%s'", server.getName());
            return;
        }

        if ("ban".startsWith(subCmd)) {
            if (args.isEmpty())
                throw new CommandException("do what with bans?");
            subCmd = args.remove(0).toLowerCase();

            if ("list".startsWith(subCmd)) {
                ctx.requireAllPermissions("trp.server.ban.list");
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
                ctx.requireAllPermissions("trp.server.ban.add");
                if (Global.network.addBan(args.get(0)))
                    ctx.sendLog("added ban");
                else
                    throw new CommandException("'%s' is already banned");
                return;
            }

            if ("remove".startsWith(subCmd)) {
                ctx.requireAllPermissions("trp.server.ban.remove");
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
