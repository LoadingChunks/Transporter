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
package com.frdfsnlght.transporter;

import com.frdfsnlght.transporter.api.GateException;
import com.frdfsnlght.transporter.api.event.RemotePlayerChatEvent;
import com.frdfsnlght.transporter.api.event.RemotePlayerPMEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Chat {

    private static Pattern colorPattern = Pattern.compile("%(\\w+)%");

    public static String colorize(String msg) {
        Matcher matcher = colorPattern.matcher(msg);
        StringBuffer b = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            try {
                ChatColor color = Utils.valueOf(ChatColor.class, name);
                matcher.appendReplacement(b, color.toString());
            } catch (IllegalArgumentException iae) {
                matcher.appendReplacement(b, matcher.group());
            }
        }
        matcher.appendTail(b);
        return b.toString();
    }

    public static void send(Player player, String message) {
        Map<Server,Set<RemoteGateImpl>> servers = new HashMap<Server,Set<RemoteGateImpl>>();

        // add all servers that relay all chat
        for (Server server : Servers.getAll())
            if (server.getSendChat())
                servers.put(server, null);

        Location loc = player.getLocation();
        RemoteGateImpl destGate;
        Server destServer;
        for (LocalGateImpl gate : Gates.getLocalGates()) {
            if (gate.isOpen() && gate.getSendChat() && gate.isInChatSendProximity(loc)) {
                try {
                    GateImpl dg = gate.getDestinationGate();
                    if (! (dg instanceof RemoteGateImpl)) continue;
                    destGate = (RemoteGateImpl)dg;
                    destServer = (Server)destGate.getRemoteServer();
                    if (servers.containsKey(destServer)) {
                        if (servers.get(destServer) == null) continue;
                    } else
                        servers.put(destServer, new HashSet<RemoteGateImpl>());
                    servers.get(destServer).add(destGate);
                } catch (GateException e) {}
            }
        }
        for (Server server : servers.keySet()) {
            server.sendChat(player, message, servers.get(server));
        }
    }

    public static void receive(RemotePlayerImpl player, String message, List<String> toGates) {
        RemotePlayerChatEvent event = new RemotePlayerChatEvent(player, message);
        Global.plugin.getServer().getPluginManager().callEvent(event);

        Player[] players = Global.plugin.getServer().getOnlinePlayers();

        final Set<Player> playersToReceive = new HashSet<Player>();
        if ((toGates == null) && ((Server)player.getRemoteServer()).getReceiveChat())
            Collections.addAll(playersToReceive, players);
        else if ((toGates != null) && (! toGates.isEmpty())) {
            for (String gateName : toGates) {
                GateImpl g = Gates.get(gateName);
                if ((g == null) || (! (g instanceof LocalGateImpl))) continue;
                LocalGateImpl gate = (LocalGateImpl)g;
                if (! gate.getReceiveChat()) continue;
                for (Player p : players) {
                    if (gate.isInChatReceiveProximity(p.getLocation()))
                        playersToReceive.add(p);
                }
            }
        }

        if (playersToReceive.isEmpty()) return;

        String format = player.format(Config.getServerChatFormat());
        format = format.replace("%message%", message);
        format = colorize(format);
        for (Player p : playersToReceive)
            p.sendMessage(format);
    }

    public static void receivePrivateMessage(RemotePlayerImpl remotePlayer, String localPlayerName, String message) {
        Player localPlayer = Global.plugin.getServer().getPlayer(localPlayerName);
        if (localPlayer == null) return;
        RemotePlayerPMEvent event = new RemotePlayerPMEvent(remotePlayer, localPlayer, message);
        Global.plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        String format = Config.getServerPMFormat();
        if (format == null) return;
        format = format.replaceAll("%fromPlayer%", remotePlayer.getDisplayName());
        format = format.replaceAll("%fromWorld%", remotePlayer.getRemoteWorld().getName());
        format = format.replaceAll("%fromServer%", remotePlayer.getRemoteServer().getName());
        format = format.replaceAll("%toPlayer%", localPlayer.getDisplayName());
        format = format.replaceAll("%toWorld%", localPlayer.getWorld().getName());
        format = format.replaceAll("%message%", message);
        format = colorize(format);
        if (! format.isEmpty())
            localPlayer.sendMessage(format);
    }

}
