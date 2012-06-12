/*
 * Copyright 2012 frdfsnlght <frdfsnlght@gmail.com>.
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bennedum.transporter.api.ReservationException;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Realm {
    
    private static final Set<String> OPTIONS = new HashSet<String>();
    private static final Set<String> RESTART_OPTIONS = new HashSet<String>();
    private static final Options options;

    static {
        OPTIONS.add("name");
        OPTIONS.add("saveInterval");
        OPTIONS.add("saveSize");
        OPTIONS.add("defaultServer");
        OPTIONS.add("defaultWorld");
        OPTIONS.add("defaultGate");
        OPTIONS.add("respawnGate");
        OPTIONS.add("dbURL");
        OPTIONS.add("dbUsername");
        OPTIONS.add("dbPassword");
        OPTIONS.add("dbPrefix");

        RESTART_OPTIONS.add("saveInterval");
        RESTART_OPTIONS.add("dbURL");
        RESTART_OPTIONS.add("dbUsername");
        RESTART_OPTIONS.add("dbPassword");
        
        options = new Options(Realm.class, OPTIONS, "trp.realm", new OptionsListener() {
            @Override
            public void onOptionSet(Context ctx, String name, String value) {
                ctx.send("realm option '%s' set to '%s'", name, value);
                if (RESTART_OPTIONS.contains(name))
                    Config.save(ctx);
                    stop(ctx);
                    start(ctx);
            }
            @Override
            public String getOptionPermission(Context ctx, String name) {
                return name;
            }
        });
    }
    
    private static boolean started = false;
    private static Connection db;
    private static int saveAllTask = -1;
    private static int saveDirtyTask = -1;
    private static Set<String> redirectedPlayers = new HashSet<String>();
    private static final Map<String,RealmPlayerImpl> cachedPlayers = new HashMap<String,RealmPlayerImpl>();
    private static Set<String> respawningPlayers = new HashSet<String>();
    
    public static boolean isStarted() {
        return started;
    }
    
    // called from main thread
    public static void start(Context ctx) {
        if (! getEnabled()) return;
        try {
            if (getName() == null)
                throw new RealmException("name is not set");
            if (getDbURL() == null)
                throw new RealmException("dbURL is not set");
            if (getDbUsername() == null)
                throw new RealmException("dbUsername is not set");
            if (getDbPassword() == null)
                throw new RealmException("dbPassword is not set");
            connect();
            started = true;
            scheduleSaveAll();
            redirectedPlayers.clear();
            synchronized (cachedPlayers) {
                cachedPlayers.clear();
            }
            ctx.send("realm support started");

            for (Server server : Servers.getAll())
                if (server.isConnected()) server.sendRefreshData();
            
        } catch (Exception e) {
            ctx.warn("realm support cannot be started: %s", e.getMessage());
        }
    }

    // called from main thread
    public static void stop(Context ctx) {
        if (! started) return;
        started = false;
        try {
            db.close();
        } catch (SQLException se) {}
        db = null;
        if (saveAllTask != -1)
            Global.plugin.getServer().getScheduler().cancelTask(saveAllTask);
        saveAllTask = -1;
        if (saveDirtyTask != -1)
            Global.plugin.getServer().getScheduler().cancelTask(saveDirtyTask);
        saveDirtyTask = -1;
        saveDirtyPlayers();
        respawningPlayers.clear();
        ctx.send("realm support stopped");
    }

    public static void onConfigLoad(Context ctx) {}

    public static void onConfigSave() {}

    // Player events
    
    public static void onTeleport(Player player, Location toLocation) {
        if (! started) return;
        if (redirectedPlayers.contains(player.getName())) return;
        Utils.debug("realm onTeleport '%s'", player.getName());
        try {
            RealmPlayerImpl data = loadPlayer(player.getName(), true);
            if (respawningPlayers.remove(player.getName())) {
                if (data == null) return;
                GateImpl respawnGate = getRespawnGateImpl();
                if (respawnGate != null)
                    sendPlayerToGate(player, respawnGate);
                else
                    sendPlayerHome(player, data.getHome());
            } else {
                if (data == null)
                    data = new RealmPlayerImpl(player);
                else
                    data.update(player);
                data.updateLastLocation(toLocation);
            }
            save(data);
        } catch (SQLException se) {
            Utils.severe("SQL Exception while processing realm player teleport: %s", se.getMessage());
        }
    }
    
    public static void savePlayer(Player player) {
        if (! started) return;
        redirectedPlayers.remove(player.getName());
        Utils.debug("realm save '%s'", player.getName());
        try {
            RealmPlayerImpl data = loadPlayer(player.getName(), true);
            if (data == null)
                data = new RealmPlayerImpl(player);
            else
                data.update(player);
            save(data);
        } catch (SQLException se) {
            Utils.severe("SQL Exception while saving realm player: %s", se.getMessage());
        }
    }
    
    public static boolean onJoin(Player player) {
        if (! started) return false;
        redirectedPlayers.remove(player.getName());
        Utils.debug("realm onJoin '%s'", player.getName());
        try {
            RealmPlayerImpl data = loadPlayer(player.getName(), true);
            if (data == null) {
                // player is new to the realm
            
                GateImpl defaultGate = getDefaultGateImpl();
                if (defaultGate != null) {
                    if (sendPlayerToGate(player, defaultGate)) {
                        save(data);
                        return true;
                    }
                }
                String toServer = getDefaultServer();
                if (toServer != null) {
                    if (! toServer.equals(Global.plugin.getServer().getServerName())) {
                        if (sendPlayerToServer(player, toServer)) {
                            save(data);
                            return true;
                        }
                    }
                }
                String toWorld = getDefaultWorld();
                if (toWorld != null) {
                    if (! toWorld.equals(player.getWorld().getName())) {
                        if (sendPlayerToWorld(player, toWorld, null)) {
                            save(data);
                            return true;
                        }
                    }
                }
                
                data = new RealmPlayerImpl(player);
            } else
                data.apply(player);
            
            String toServer = data.getLastServer();
            if (toServer != null) {
                if (! toServer.equals(Global.plugin.getServer().getServerName())) {
                    if (sendPlayerToServer(player, toServer)) {
                        save(data);
                        return true;
                    }
                    /*
                } else {
                    String toWorld = data.lastWorld;
                    if (toWorld != null) {
                        if (! toWorld.equals(player.getWorld().getName())) {
                            if (sendPlayerToWorld(player, toWorld, data.lastLocation)) {
                                save(data);
                                return true;
                            }
                        }
                    }
                    */
                }
            }

            data.update(player);
            data.setLastJoin(Calendar.getInstance());
            save(data);
            return false;
        } catch (SQLException se) {
            Utils.severe("SQL Exception while processing realm player join: %s", se.getMessage());
            return false;
        }
    }
    
    public static void onQuit(Player player) {
        if (! started) return;
        if (redirectedPlayers.contains(player.getName())) return;
        Utils.debug("realm onQuit '%s'", player.getName());
        try {
            RealmPlayerImpl data = loadPlayer(player.getName(), true);
            if (data == null)
                data = new RealmPlayerImpl(player);
            else
                data.update(player);
            data.setLastQuit(Calendar.getInstance());
            save(data);
        } catch (SQLException se) {
            Utils.severe("SQL Exception while processing realm player quit: %s", se.getMessage());
        }
    }
    
    public static void onKick(Player player) {
        if (! started) return;
        if (redirectedPlayers.contains(player.getName())) return;
        Utils.debug("realm onKick '%s'", player.getName());
        
        try {
            RealmPlayerImpl data = loadPlayer(player.getName(), true);
            if (data == null)
                data = new RealmPlayerImpl(player);
            else
                data.update(player);
            data.setLastKick(Calendar.getInstance());
            save(data);
        } catch (SQLException se) {
            Utils.severe("SQL Exception while processing realm player kick: %s", se.getMessage());
        }
    }
    
    public static void onDeath(Player player, String message) {
        if (! started) return;
        Utils.debug("realm onDeath '%s'", player.getName());
        try {
            RealmPlayerImpl data = loadPlayer(player.getName(), true);
            if (data == null)
                data = new RealmPlayerImpl(player);
            else
                data.update(player);
            data.addDeath(message);
            save(data);
        } catch (SQLException se) {
            Utils.severe("SQL Exception while processing realm player death: %s", se.getMessage());
        }
    }
    
    public static void onRespawn(Player player) {
        if (! started) return;
        Utils.debug("realm onRespawn '%s'", player.getName());
        respawningPlayers.add(player.getName());
    }
    
    public static void onSetHome(Player player, Location loc) {
        if (! started) return;
        Utils.debug("realm onSetHome '%s'", player.getName());
        try {
            RealmPlayerImpl data = loadPlayer(player.getName(), true);
            if (data == null)
                data = new RealmPlayerImpl(player);
            else
                data.update(player);
            data.setHome(Global.plugin.getServer().getServerName() + "|" + loc.getWorld().getName() + "|" + loc.getX() + "|" + loc.getY() + "|" + loc.getZ());
            save(data);
        } catch (SQLException se) {
            Utils.severe("SQL Exception while setting realm player home: %s", se.getMessage());
        }
    }

    public static void onUnsetHome(Player player) {
        if (! started) return;
        Utils.debug("realm onUnsetHome '%s'", player.getName());
        try {
            RealmPlayerImpl data = loadPlayer(player.getName(), true);
            if (data == null)
                data = new RealmPlayerImpl(player);
            else
                data.update(player);
            data.setHome(null);
            save(data);
        } catch (SQLException se) {
            Utils.severe("SQL Exception while unsetting realm player home: %s", se.getMessage());
        }
    }
    
    public static void onPlayerKill(Player player, Player killed) {
        if (! started) return;
        Utils.debug("realm onPlayerKill '%s'", player.getName());
        try {
            RealmPlayerImpl data = loadPlayer(player.getName(), true);
            if (data == null)
                data = new RealmPlayerImpl(player);
            else
                data.update(player);
            data.addPlayerKill(killed);
            save(data);
        } catch (SQLException se) {
            Utils.severe("SQL Exception while processing realm player PVP kill: %s", se.getMessage());
        }
    }
    
    public static void onMobKill(Player player, Entity killed) {
        if (! started) return;
        Utils.debug("realm onMobKill '%s'", player.getName());
        try {
            RealmPlayerImpl data = loadPlayer(player.getName(), true);
            if (data == null)
                data = new RealmPlayerImpl(player);
            else
                data.update(player);
            data.addMobKill(killed);
            save(data);
        } catch (SQLException se) {
            Utils.severe("SQL Exception while processing realm player mob kill: %s", se.getMessage());
        }
    }
    
    
    // End Player events
    
    public static Set<String> getPlayerNames() {
        Set<String> names = new HashSet<String>();
        if (! started) return names;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            connect();
            stmt = db.prepareStatement("select name from " + getTableName("players"));
            rs = stmt.executeQuery();
            while (rs.next())
                names.add(rs.getString("name"));
        } catch (SQLException se) {
            Utils.severe("SQL Exception while getting player names: %s", se.getMessage());
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            } catch (SQLException se) {}
        }
        return names;
    }
    
    public static RealmPlayerImpl getPlayer(String playerName) {
        if (! started) return null;
        try {
            return loadPlayer(playerName, false);
        } catch (SQLException se) {
            Utils.severe("SQL Exception while loading realm player: %s", se.getMessage());
            return null;
        }
    }
    
    private static void save(RealmPlayerImpl data) {
        if ((data == null) || (! data.isDirty())) return;
        synchronized (cachedPlayers) {
            cachedPlayers.put(data.getName(), data);
            if (saveDirtyTask == -1) {
                saveDirtyTask = Utils.worker(new Runnable() {
                    @Override
                    public void run() {
                        saveDirtyPlayers();
                    }
                });
            }
        }
    }

    // called from any thread
    private static void saveDirtyPlayers() {
        synchronized (cachedPlayers) {
            for (Iterator<RealmPlayerImpl> i = cachedPlayers.values().iterator(); i.hasNext(); ) {
                RealmPlayerImpl data = i.next();
                try {
                    connect();
                    data.save(db);
                } catch (SQLException se) {
                    Utils.severe("SQL Exception while saving realm player '%s': %s", data.getName(), se.getMessage());
                }
            }
            cachedPlayers.clear();
            saveDirtyTask = -1;
        }
    }
    
    private static boolean sendPlayerToServer(Player player, String toServer) {
        Server server = Servers.find(toServer);
        if (server == null) {
            Utils.warning("Unknown realm server '%s' for player '%s'", toServer, player.getName());
            return false;
        }
        if (! server.isConnected()) {
            Utils.warning("Offline realm server '%s' for player '%s'", toServer, player.getName());
            return false;
        }
        String kickMessage = server.getKickMessage(player.getAddress());
        if (kickMessage == null) return false;
        redirectedPlayers.add(player.getName());
        Utils.schedulePlayerKick(player, kickMessage);
        return true;
    }
    
    private static boolean sendPlayerToGate(Player player, GateImpl gate) {
        try {
            ReservationImpl res = new ReservationImpl(player, gate);
            res.depart();
            return true;
        } catch (ReservationException re) {
            Utils.warning("Reservation exception while sending player '%s' to gate '%s': %s", player.getName(), gate.getLocalName(), re.getMessage());
            return false;
        }
    }
    
    private static boolean sendPlayerHome(Player player, String home) {
        if (home == null) return false;
        Utils.debug("sending realm player '%s' home to %s", player.getName(), home);
        String[] parts = home.split("\\|");
        if (parts.length != 5) {
            Utils.warning("Invalid realm home for player '%s': %s", player.getName(), home);
            return false;
        }
        String homeServer = parts[0];
        String homeWorld = parts[1];
        double x, y, z;
        try {
            x = Double.parseDouble(parts[2]);
            y = Double.parseDouble(parts[3]);
            z = Double.parseDouble(parts[4]);
        } catch (NumberFormatException nfe) {
            Utils.warning("Invalid realm home coordinates for player '%s': %s", player.getName(), home);
            return false;
        }
        
        if (! homeServer.equals(Global.plugin.getServer().getServerName())) {
            Server server = Servers.find(homeServer);
            if (server == null) {
                Utils.warning("Unknown realm home server '%s' for player '%s'", homeServer, player.getName());
                return false;
            }
            if (! server.isConnected()) {
                Utils.warning("Offline realm home server '%s' for player '%s'", homeServer, player.getName());
                return false;
            }

            try {
                ReservationImpl res = new ReservationImpl(player, server, homeWorld, x, y, z);
                res.depart();
                return true;
            } catch (ReservationException re) {
                Utils.warning("Reservation exception while sending player '%s' to realm home '%s': %s", player.getName(), home, re.getMessage());
                return false;
            }
        }
        // already on the right server
        return true;
    }
    
    private static boolean sendPlayerToWorld(Player player, String toWorld, String toCoords) {
        World world = Global.plugin.getServer().getWorld(toWorld);
        Location toLocation;
        if (world == null) {
            Utils.warning("Unknown realm world '%s' for player '%s'", toWorld, player.getName());
            return false;
        }
        if (toCoords == null)
            toLocation = world.getSpawnLocation();
        else {
            String[] coords = toCoords.split(",");
            if (coords.length != 3) {
                Utils.warning("Invalid location coordinates '%s' for player '%s'", toCoords, player.getName());
                return false;
            }
            try {
                double x = Double.parseDouble(coords[0]);
                double y = Double.parseDouble(coords[1]);
                double z = Double.parseDouble(coords[2]);
                toLocation = new Location(world, x, y, z);
            } catch (NumberFormatException nfe) {
                Utils.warning("Invalid location coordinates '%s' for player '%s'", toCoords, player.getName());
                return false;
            }
        }
        Utils.debug("teleporting player '%s' to %s", player.getName(), toLocation);
        return player.teleport(toLocation);
    }
    
    private static List<String> savedPlayers = new ArrayList<String>();
    
    private static void scheduleSaveAll() {
        if (saveAllTask != -1)
            Global.plugin.getServer().getScheduler().cancelTask(saveAllTask);
        saveAllTask = Utils.fireDelayed(new Runnable() {
            @Override
            public void run() {
                //Utils.debug("realm save all players started");
                for (OfflinePlayer player : Global.plugin.getServer().getOfflinePlayers()) {
                    redirectedPlayers.remove(player.getName());
                    savedPlayers.remove(player.getName());
                }
                for (Player player : Global.plugin.getServer().getOnlinePlayers())
                    if (! savedPlayers.contains(player.getName()))
                        savedPlayers.add(player.getName());
                if (! savedPlayers.isEmpty()) {
                    for (int playerNum = 0; playerNum < Math.min(getSaveSize(), savedPlayers.size()); playerNum++) {
                        String playerName = savedPlayers.remove(0);
                        Utils.debug("saving realm player '%s'", playerName);
                        try {
                            Player player = Global.plugin.getServer().getPlayer(playerName);
                            if (player != null) {
                                RealmPlayerImpl data = loadPlayer(playerName, true);
                                if (data == null)
                                    data = new RealmPlayerImpl(player);
                                else
                                    data.update(player);
                                save(data);
                            }
                        } catch (SQLException se) {
                            Utils.severe("SQL Exception while loading realm player '%s': %s", playerName, se.getMessage());
                        }
                        savedPlayers.add(playerName);
                    }
                }
                saveAllTask = -1;
                scheduleSaveAll();
                //Utils.debug("realm save all players completed");
            }
        }, getSaveInterval());
    }
    
    public static boolean getEnabled() {
        return Config.getBooleanDirect("realm.enabled", false);
    }

    public static void setEnabled(Context ctx, boolean b) {
        Config.setPropertyDirect("realm.enabled", b);
        stop(ctx);
        if (b) start(ctx);
    }
    
    /* Begin options */

    public static String getName() {
        return Config.getStringDirect("realm.name", null);
    }

    public static void setName(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        Config.setPropertyDirect("realm.name", s);
    }
    
    public static int getSaveInterval() {
        return Config.getIntDirect("realm.saveInterval", 20000);
    }

    public static void setSaveInterval(int i) {
        if (i < 1000)
            throw new IllegalArgumentException("saveInterval must be at least 1000");
        Config.setPropertyDirect("realm.saveInterval", i);
    }

    public static int getSaveSize() {
        return Config.getIntDirect("realm.saveSize", 3);
    }

    public static void setSaveSize(int i) {
        if (i < 1)
            throw new IllegalArgumentException("saveSize must be at least 1");
        Config.setPropertyDirect("realm.saveSize", i);
    }

    public static String getDefaultServer() {
        return Config.getStringDirect("realm.defaultServer", null);
    }

    public static void setDefaultServer(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        if (s != null) {
            Server server = Servers.find(s);
            if (server == null)
                throw new IllegalArgumentException("unknown server");
            s = server.getName();
        }
        Config.setPropertyDirect("realm.defaultServer", s);
    }

    public static String getDefaultWorld() {
        return Config.getStringDirect("realm.defaultWorld", null);
    }

    public static void setDefaultWorld(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        if (s != null) {
            LocalWorldImpl world = Worlds.get(s);
            if (world == null)
                throw new IllegalArgumentException("unknown world");
            s = world.getName();
        }
        Config.setPropertyDirect("realm.defaultWorld", s);
    }

    public static String getDefaultGate() {
        return Config.getStringDirect("realm.defaultGate", null);
    }

    public static void setDefaultGate(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        if (s != null) {
            GateImpl gate = Gates.find(s);
            if (gate == null)
                throw new IllegalArgumentException("unknown or offline gate");
            s = gate.getLocalName();
        }
        Config.setPropertyDirect("realm.defaultGate", s);
    }
    
    public static String getRespawnGate() {
        return Config.getStringDirect("realm.respawnGate", null);
    }

    public static void setRespawnGate(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        if (s != null) {
            GateImpl gate = Gates.find(s);
            if (gate == null)
                throw new IllegalArgumentException("unknown or offline gate");
            s = gate.getLocalName();
        }
        Config.setPropertyDirect("realm.respawnGate", s);
    }
    
    public static String getDbURL() {
        return Config.getStringDirect("realm.dbURL", null);
    }

    public static void setDbURL(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        Config.setPropertyDirect("realm.dbURL", s);
    }

    public static String getDbUsername() {
        return Config.getStringDirect("realm.dbUsername", null);
    }

    public static void setDbUsername(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        Config.setPropertyDirect("realm.dbUsername", s);
    }

    public static String getDbPassword() {
        if (getRealDbPassword() == null) return null;
        return "*******";
    }

    public static String getRealDbPassword() {
        return Config.getStringDirect("realm.dbPassword", null);
    }

    public static void setDbPassword(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        Config.setPropertyDirect("realm.dbPassword", s);
    }

    public static String getDbPrefix() {
        return Config.getStringDirect("realm.dbPrefix", null);
    }

    public static void setDbPrefix(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        if (s != null) {
            if (! s.matches("^\\w+$"))
                throw new IllegalArgumentException("illegal character");
        }
        Config.setPropertyDirect("realm.dbPrefix", s);
    }

    public static void getOptions(Context ctx, String name) throws OptionsException, PermissionsException {
        options.getOptions(ctx, name);
    }

    public static String getOption(Context ctx, String name) throws OptionsException, PermissionsException {
        return options.getOption(ctx, name);
    }

    public static void setOption(Context ctx, String name, String value) throws OptionsException, PermissionsException {
        options.setOption(ctx, name, value);
    }

    /* End options */

    private static void connect() throws SQLException {
        if ((db == null) || db.isClosed()) {
            db = DriverManager.getConnection(getDbURL(), getDbUsername(), getRealDbPassword());
            db.setAutoCommit(true);
            db.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        }
    }
    
    private static RealmPlayerImpl loadPlayer(String playerName, boolean fromCache) throws SQLException {
        if (! started) return null;
        if (fromCache) {
            synchronized (cachedPlayers) {
                RealmPlayerImpl player = cachedPlayers.remove(playerName);
                if (player != null) return player;
            }
        }
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            connect();
            stmt = Realm.db.prepareStatement("select * from " + getTableName("players") + " where name=?");
            stmt.setString(1, playerName);
            rs = stmt.executeQuery();
            if (!rs.next()) return null;
            return new RealmPlayerImpl(rs);
        } finally {
            if (rs != null)
                rs.close();
            if (stmt != null)
                stmt.close();
        }
    }

    
    
    public static GateImpl getDefaultGateImpl() {
        String gName = getDefaultGate();
        if (gName == null) return null;
        GateImpl gate = Gates.get(gName);
        return gate;
    }
    
    public static GateImpl getRespawnGateImpl() {
        String gName = getRespawnGate();
        if (gName == null) return null;
        GateImpl gate = Gates.get(gName);
        return gate;
    }
    
    public static String getTableName(String baseName) {
        String pre = getDbPrefix();
        if (pre == null) return baseName;
        return '`' + pre + baseName + '`';
    }
    
}
