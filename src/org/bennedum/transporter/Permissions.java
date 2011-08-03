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

import com.nijiko.permissions.PermissionHandler;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Permissions {

    private static final String OPS_FILE = "ops.txt";
    private static final String BANNEDIPS_FILE = "banned-ips.txt";
    private static final String BANNEDPLAYERS_FILE = "banned-players.txt";
    private static final String WHITELIST_FILE = "white-list.txt";
    private static final String SERVERPROPERTIES_FILE = "server.properties";
    private static final String PERMISSIONS_FILE = "permissions.properties";

    private static final File permissionsFile =
            new File(Global.plugin.getDataFolder(), PERMISSIONS_FILE);
    
    private static Map<String,ListFile> listFiles = new HashMap<String,ListFile>();
    private static Map<String,PropertiesFile> propertiesFiles = new HashMap<String,PropertiesFile>();

    private static PermissionHandler permissionsPlugin = null;
    
    public static boolean permissionsAvailable() {
        if (! Global.config.getBoolean("usePermissions", false)) return false;
        if (permissionsPlugin != null) return true;
        Plugin p = Global.plugin.getServer().getPluginManager().getPlugin("Permissions");
        if ((p == null) || (! p.isEnabled())) return false;
        permissionsPlugin = ((com.nijikokun.bukkit.Permissions.Permissions)p).getHandler();
        return true;
    }
    
    public static boolean hasBasic(Player player, String perm) {
        return hasBasic(player.getName(), perm);
    }

    public static boolean hasBasic(String name, String perm) {
        Properties permissions = getProperties(permissionsFile);
        String[] parts = perm.split("\\.");
        String builtPerm = null;
        for (String part : parts) {
            if (builtPerm == null)
                builtPerm = part;
            else
                builtPerm = builtPerm + "." + part;
            String prop = permissions.getProperty(builtPerm);
            if (prop == null)
                prop = permissions.getProperty(builtPerm + ".*");
            if (prop == null)
                continue;
            String[] players = prop.split("\\s*,\\s*");
            for (String player : players)
                if (player.equals("*") || player.equals(name)) return true;
        }
        return false;
    }

    public static boolean has(Player player, String perm) {
        try {
            require(player.getWorld().getName(), player.getName(), true, perm);
            return true;
        } catch (PermissionsException e) {
            return false;
        }
    }
    
    public static void require(Player player, String perm) throws PermissionsException {
        if (player == null) return;
        require(player.getWorld().getName(), player.getName(), true, perm);
    }
    
    public static void require(Player player, boolean requireAll, String ... perms) throws PermissionsException {
        if (player == null) return;
        require(player.getWorld().getName(), player.getName(), requireAll, perms);
    }

    public static void require(String worldName, String playerName, String perm) throws PermissionsException {
        require(worldName, playerName, true, perm);
    }
    
    private static void require(String worldName, String playerName, boolean requireAll, String ... perms) throws PermissionsException {
        if (isOp(playerName)) return;
        if (permissionsAvailable()) {
            for (String perm : perms) {
                if (requireAll) {
                    if (! permissionsPlugin.permission(worldName, playerName, perm))
                        throw new PermissionsException("not permitted (Permissions)");
                } else {
                    if (permissionsPlugin.permission(worldName, playerName, perm)) return;
                }
            }
            if (! requireAll)
                throw new PermissionsException("not permitted (Permissions)");
            return;
        }
        
        // use built-in permissions
        for (String perm : perms) {
            if (requireAll) {
                if (! hasBasic(playerName, perm))
                    throw new PermissionsException("not permitted (basic)");
            } else {
                if (hasBasic(playerName, perm)) return;
            }
        }
        if (! requireAll)
            throw new PermissionsException("not permitted (basic)");
    }

    // can't check player's IP because it might not be what it is on the sending side due to NAT
    public static void connect(String playerName) throws PermissionsException {
        if (Global.plugin.getServer().getOnlinePlayers().length >= Global.plugin.getServer().getMaxPlayers())
            throw new PermissionsException("maximim players already connected");
        if (getProperties(new File(SERVERPROPERTIES_FILE)).getProperty("white-list", "false").equalsIgnoreCase("true"))
            if (! getList(new File(WHITELIST_FILE)).contains(playerName))
                throw new PermissionsException("player is not white-listed");
        if (getList(new File(BANNEDPLAYERS_FILE)).contains(playerName))
            throw new PermissionsException("player is banned");
    }

    public static boolean isOp(Player player) {
        if (player == null) return true;
        return isOp(player.getName());
    }
    
    public static boolean isOp(String playerName) {
        return getList(new File(OPS_FILE)).contains(playerName);
    }
    
    private static Set<String> getList(File file) {
        ListFile listFile = listFiles.get(file.getAbsolutePath());
        if (listFile == null) {
            listFile = new ListFile();
            listFiles.put(file.getAbsolutePath(), listFile);
        }
        if ((listFile.data == null) || (listFile.lastRead < file.lastModified())) {
            listFile.data = new HashSet<String>();
            try {
                BufferedReader r = new BufferedReader(new FileReader(file));
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    listFile.data.add(line);
                }
                r.close();
                listFile.lastRead = System.currentTimeMillis();
            } catch (IOException ioe) {
                Utils.warning("unable to read %s: %s", file.getAbsolutePath(), ioe.getMessage());
            }
        }
        return listFile.data;
    }

    private static Properties getProperties(File file) {
        PropertiesFile propsFile = propertiesFiles.get(file.getAbsolutePath());
        if (propsFile == null) {
            propsFile = new PropertiesFile();
            propertiesFiles.put(file.getAbsolutePath(), propsFile);
        }
        if ((propsFile.data == null) || (propsFile.lastRead < file.lastModified())) {
            propsFile.data = new Properties();
            try {
                propsFile.data.load(new FileInputStream(file));
                propsFile.lastRead = System.currentTimeMillis();
            } catch (IOException ioe) {
                Utils.warning("unable to read %s: %s", file.getAbsolutePath(), ioe.getMessage());
            }
        }
        return propsFile.data;
    }
    
    private static class ListFile {
        Set<String> data = null;
        long lastRead = 0;
    }
    
    private static class PropertiesFile {
        Properties data = null;
        long lastRead = 0;
    }
    
}
