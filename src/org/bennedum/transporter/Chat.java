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
package org.bennedum.transporter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Chat {
    
    public static void send(Player player, String message) {
        Map<Server,Set<RemoteGate>> gates = new HashMap<Server,Set<RemoteGate>>();
        Location loc = player.getLocation();
        Gate destGate;
        Server destServer;
        for (LocalGate gate : Gates.getLocalGates()) {
            if (gate.isInChatProximity(loc) && gate.isOpen()) {
                try {
                    destGate = gate.getDestinationGate();
                    if (! destGate.isSameServer()) {
                        destServer = Servers.get(destGate.getServerName());
                        if (gates.get(destServer) == null)
                            gates.put(destServer, new HashSet<RemoteGate>());
                        gates.get(destServer).add((RemoteGate)destGate);
                    }
                } catch (GateException e) {
                }
            }
        }
        for (Server server : gates.keySet()) {
            server.doRelayChat(player, player.getWorld().getName(), message, gates.get(server));
        }
    }

    public static void receive(String playerName, String displayName, String worldName, String serverName, String message, List<String> toGates) {
        Future<Map<String,Location>> future = Utils.call(new Callable<Map<String,Location>>() {
            @Override
            public Map<String,Location> call() {
                Map<String,Location> players = new HashMap<String,Location>();
                for (Player player : Global.plugin.getServer().getOnlinePlayers())
                    players.put(player.getName(), player.getLocation());
                return players;
            }
        });

        Map<String,Location> players = null;
        try {
            players = future.get();
        } catch (InterruptedException e) {
        } catch (ExecutionException e) {}
        if (players == null) return;

        final Set<String> playersToReceive = new HashSet<String>();
        for (String gateName : toGates) {
            Gate gate = Gates.get(gateName);
            if (gate == null) continue;
            if (! gate.isSameServer()) continue;
            for (String player : players.keySet())
                if (((LocalGate)gate).isInChatProximity(players.get(player)))
                    playersToReceive.add(player);
        }

        if (playersToReceive.isEmpty()) return;

        String format = Global.config.getString("chatFormat", "<%player%@%server%> %message%");
        format.replace("%player%", displayName);
        format.replace("%server%", serverName);
        format.replace("%world%", worldName);
        format.replace("%message%", message);
        final String msg = format;
        Utils.fire(new Runnable() {
            @Override
            public void run() {
                for (String playerName : playersToReceive) {
                    Player player = Global.plugin.getServer().getPlayer(playerName);
                    if ((player != null) && (player.isOnline()))
                        player.sendMessage(msg);
                }
            }
        });
    }
    
}
