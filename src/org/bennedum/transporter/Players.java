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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Players {
    
    private static final Map<String,PlayerProxy> players = new HashMap<String,PlayerProxy>();
    
    public static List<PlayerProxy> getAll() {
        synchronized (players) {
            return new ArrayList<PlayerProxy>(players.values());
        }
    }

    public static List<PlayerProxy> getLocalPlayers() {
        List<PlayerProxy> locPlayers = new ArrayList<PlayerProxy>();
        synchronized (players) {
            for (PlayerProxy player : players.values())
               if (player.getServerName() == null)
                   locPlayers.add(player);
        }
        return locPlayers;
    }

    public static void onTeleport(Player player, Location to) {
        PlayerProxy proxy;
        synchronized (players) {
            proxy = players.get(player.getName());
        }
        if (! proxy.getWorldName().equals(to.getWorld().getName()))
            for (Server server : Servers.getAll())
                server.doPlayerChangedWorld(player, to.getWorld());
    }
    
    public static void onJoin(Player player, Reservation res) {
        add(new PlayerProxy(player.getName(), player.getDisplayName(), null, player.getWorld().getName()));
        for (Server server : Servers.getAll())
            server.doPlayerJoined(player, res == null);
    }
    
    public static void onQuit(Player player, Reservation res) {
        remove(player.getName());
        for (Server server : Servers.getAll())
            server.doPlayerQuit(player, res == null);
    }
    
    public static void onKick(Player player, Reservation res) {
        remove(player.getName());
        for (Server server : Servers.getAll())
            server.doPlayerKicked(player, res == null);
    }
    
    public static void remoteChangeWorld(Server server, String playerName, String worldName) {
        PlayerProxy proxy;
        synchronized (players) {
            proxy = players.get(playerName);
        }
        if (proxy == null) return;
        proxy.setWorldName(worldName);
    }
    
    public static void remoteJoin(Server server, String name, String displayName, String worldName, boolean announce) {
        PlayerProxy player = new PlayerProxy(name, displayName, server.getName(), worldName);
        add(player);
        if (announce) {
            String message = formatMessage(Config.getServerJoinFormat(), player);
            Global.plugin.getServer().broadcastMessage(message);
        }
    }
    
    public static void remoteQuit(Server server, String name, boolean announce) {
        PlayerProxy player = remove(name);
        if (announce && (player != null)) {
            String message = formatMessage(Config.getServerQuitFormat(), player);
            Global.plugin.getServer().broadcastMessage(message);
        }
    }
    
    public static void remoteKick(Server server, String name, boolean announce) {
        PlayerProxy player = remove(name);
        if (announce && (player != null)) {
            String message = formatMessage(Config.getServerKickFormat(), player);
            Global.plugin.getServer().broadcastMessage(message);
        }
    }
    
    public static void remove(Server server) {
        synchronized (players) {
            for (String name : new ArrayList<String>(players.keySet())) {
                PlayerProxy player = players.get(name);
                if (player.getServerName() == null) continue;
                if (player.getServerName().equals(server.getName()))
                    players.remove(name);
            }
        }
    }
    
    public static void add(PlayerProxy player) {
        synchronized (players) {
            players.put(player.getName(), player);
        }
    }
    
    private static PlayerProxy remove(String name) {
        synchronized (players) {
            return players.remove(name);
        }
    }
    
    private static String formatMessage(String format, PlayerProxy player) {
        format = format.replace("%player%", player.getDisplayName());
        format = format.replace("%world%", player.getWorldName());
        format = format.replace("%server%", player.getServerName());
        return format;
    }
    
}
