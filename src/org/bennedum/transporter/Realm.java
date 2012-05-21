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

import java.sql.Clob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.rowset.serial.SerialClob;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Realm {
    
    private static final Set<String> OPTIONS = new HashSet<String>();
    private static final Set<String> RESTART_OPTIONS = new HashSet<String>();
    private static final Options options;

    static {
        OPTIONS.add("enabled");
        OPTIONS.add("name");
        OPTIONS.add("saveInterval");
        OPTIONS.add("dbURL");
        OPTIONS.add("dbUsername");
        OPTIONS.add("dbPassword");
        OPTIONS.add("dbPrefix");

        RESTART_OPTIONS.add("name");
        RESTART_OPTIONS.add("dbURL");
        RESTART_OPTIONS.add("dbUsername");
        RESTART_OPTIONS.add("dbPassword");
        RESTART_OPTIONS.add("dbPrefix");
        options = new Options(Realm.class, OPTIONS, "trp.realm", new OptionsListener() {
            @Override
            public void onOptionSet(Context ctx, String name, String value) {
                ctx.send("realm option '%s' set to '%s'", name, value);
                if (RESTART_OPTIONS.contains(name)) {
                    Config.save(ctx);
                    restart(ctx);
                }
            }
            @Override
            public String getOptionPermission(Context ctx, String name) {
                return name;
            }
        });
    }
    
    private static Thread realmThread = null;
    private static ThreadState state = ThreadState.STOPPED;
    private static boolean enabled;
    private static String name;
    private static int saveInterval;
    private static String dbURL;
    private static String dbUsername;
    private static String dbPassword;
    private static String dbPrefix;

    private static Connection db = null;
    private static final List<PlayerData> saveQueue = new ArrayList<PlayerData>();
    private static final List<PlayerData> mergeQueue = new ArrayList<PlayerData>();
    private static long lastSave = 0;
    
    // called from main thread
    public static void start(Context ctx) {
        if (! enabled) return;
        try {
            if (name == null)
                throw new RealmException("name is not set");
            if (dbURL == null)
                throw new RealmException("dbURL is not set");
            if (dbUsername == null)
                throw new RealmException("dbUsername is not set");
            if (dbPassword == null)
                throw new RealmException("dbPassword is not set");
        } catch (Exception e) {
            ctx.warn("realm manager cannot be started (realm support is disabled): %s", e.getMessage());
            return;
        }

        realmThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Realm.run();
            }
        });
        ctx.send("starting realm manager...");
        realmThread.start();
    }

    public static void restart(Context ctx) {
        stop(ctx);
        onConfigLoad(ctx);
        start(ctx);
    }

    // called from main thread
    public static void stop(Context ctx) {
        if ((realmThread == null) ||
            (! realmThread.isAlive()) ||
            (state != ThreadState.RUNNING)) return;
        ctx.send("stopping realm manager...");
        state = ThreadState.STOP;
        synchronized (saveQueue) {
            saveQueue.notify();
        }
        synchronized (mergeQueue) {
            mergeQueue.notify();
        }
        while (realmThread.isAlive()) {
            try {
                realmThread.join();
            } catch (InterruptedException ie) {}
        }
        realmThread = null;
        ctx.send("realm manager stopped");
    }

    public static void onConfigLoad(Context ctx) {
        enabled = getEnabled();
        name = getName();
        saveInterval = getSaveInterval();
        dbURL = getDbURL();
        dbUsername = getDbUsername();
        dbPassword = getDbPassword();
        dbPrefix = getDbPrefix();
    }

    public static void onConfigSave() {
    }

    // called from main thread
    public static void save(Player player) {
        PlayerData data = load(player, true);
        if (data == null) return;
        synchronized (saveQueue) {
            saveQueue.add(data);
            saveQueue.notify();
        }
    }

    // called from main thread
    public static void saveDeath(Player player, String reason) {
        PlayerData data = load(player, true);
        if (data == null) return;
        data.deaths++;
        data.lastDeathReason = reason;
        synchronized (saveQueue) {
            saveQueue.add(data);
            saveQueue.notify();
        }
        // TODO: send the player home
    }

    // called from main thread
    public static void saveQuit(Player player) {
        PlayerData data = load(player, true);
        if (data == null) return;
        data.lastPlayed = Calendar.getInstance();
        synchronized (saveQueue) {
            saveQueue.add(data);
            saveQueue.notify();
        }
    }
    
    // called from any thread
    public static void saveAll() {
        lastSave = System.currentTimeMillis();
        if (! Utils.isMainThread()) {
            Utils.fire(new Runnable() {
                @Override
                public void run() {
                    saveAll();
                }
            });
            return;
        }
        synchronized (mergeQueue) {
            for (Player player : Global.plugin.getServer().getOnlinePlayers())
                mergeQueue.add(new PlayerData(player));
            mergeQueue.notify();
        }
    }
    
    private static PlayerData load(Player player, boolean update) {
        try {
            PlayerData data = PlayerData.load(player);
            if (data == null)
                data = new PlayerData(player);
            else if (update)
                data.update(player);
            return data;
        } catch (SQLException se) {
            Utils.severe("While loading realm player '%s': %s", player.getName(), se.getMessage());
            return null;
        }
    }
    
    /* Begin options */

    public static boolean getEnabled() {
        return Config.getBooleanDirect("realm.enabled", false);
    }

    public static void setEnabled(boolean b) {
        Config.setPropertyDirect("realm.enabled", b);
    }

    public static String getName() {
        return Config.getStringDirect("realm.name", null);
    }

    public static void setName(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        Config.setPropertyDirect("realm.name", s);
    }
    
    public static int getSaveInterval() {
        return Config.getIntDirect("realm.saveInterval", 60000);
    }

    public static void setSaveInterval(int i) {
        if (i < 1000)
            throw new IllegalArgumentException("saveInterval must be at least 1000");
        Config.setPropertyDirect("realm.saveInterval", i);
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

    public static boolean isActive() {
        return enabled && (realmThread != null);
    }
    
    public static boolean isStopped() {
        return (state == ThreadState.STOP) || (state == ThreadState.STOPPING) || (state == ThreadState.STOPPED);
    }
    
    private static void run() {

        Utils.info("realm manager started");
        state = ThreadState.RUNNING;

        PlayerData data;
        
        // processing
        while (true) {
            if (state == ThreadState.STOP)
                state = ThreadState.STOPPING;
            data = null;
            
            synchronized (mergeQueue) {
                for (PlayerData md : mergeQueue) {
                    md.merge();
                }
                mergeQueue.clear();
            }
            synchronized (saveQueue) {
                if (saveQueue.isEmpty()) {
                    if (state == ThreadState.STOPPING) break;
                    try {
                        saveQueue.wait(2000);
                    } catch (InterruptedException ie) {}
                } else
                    data = saveQueue.remove(0);
            }
            if (data != null)
                save(data);
            else if (System.currentTimeMillis() >= (lastSave + saveInterval))
                saveAll();
        }
        Utils.info("realm manager stopped");
        state = ThreadState.STOPPED;

    }
    
    // called from the realm thread
    private static void save(PlayerData data) {
        try {
            data.save();
        } catch (SQLException se) {
            Utils.severe("While saving realm player '%s': %s", data.name, se.getMessage());
        }
    }

    private static void connect() throws SQLException {
        if (db != null) return;
        db = DriverManager.getConnection(dbURL, dbUsername, dbPassword);
    }
    
    private static String tableName(String baseName) {
        if (dbPrefix == null) return baseName;
        return dbPrefix + baseName;
    }
    
    private static final class PlayerData {
        String name;
        String displayName;
        String address;
        String server;
        String world;
        double x, y, z;
        List<TypeMap> inventory;
        List<TypeMap> armor;
        int heldItemSlot;
        int health;
        int remainingAir;
        int fireTicks;
        int foodLevel;
        float exhaustion;
        float saturation;
        String gameMode;
        int level;
        float xp;
        List<TypeMap> potionEffects;
        Calendar lastUpdated = Calendar.getInstance();
        
        static PlayerData load(Player player) throws SQLException {
            connect();
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = db.prepareStatement("select * from " + tableName("players") + " where name=?");
                stmt.setString(1, player.getName());
                rs = stmt.executeQuery();
                if (! rs.next()) return null;
                return new PlayerData(rs);
            } finally {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            }
        }

        private PlayerData(ResultSet rs) throws SQLException {
            name = rs.getString("name");
            displayName = rs.getString("displayName");
            address = rs.getString("address");
            server = rs.getString("server");
            world = rs.getString("world");
            x = rs.getDouble("worldX");
            y = rs.getDouble("worldY");
            z = rs.getDouble("worldZ");
            
            Clob invClob = rs.getClob("inventory");
            TypeMap invMap = TypeMap.decode(invClob.getSubString(0L, (int)invClob.length()));
            inventory = invMap.getMapList("inventory");
                    
            Clob armorClob = rs.getClob("armor");
            TypeMap armorMap = TypeMap.decode(armorClob.getSubString(0L, (int)armorClob.length()));
            armor = armorMap.getMapList("armor");
                    
            heldItemSlot = rs.getInt("heldItemSlot");
            health = rs.getInt("health");
            remainingAir = rs.getInt("remainingAir");
            fireTicks = rs.getInt("fireTicks");
            foodLevel = rs.getInt("foodLevel");
            exhaustion = rs.getFloat("exhaustion");
            saturation = rs.getFloat("saturation");
            gameMode = rs.getString("gameMode");
            level = rs.getInt("level");
            xp = rs.getFloat("xp");
            
            lastUpdated.setTimeInMillis(rs.getTimestamp("lastUpdated").getTime());
            
            Clob peClob = rs.getClob("potionEffects");
            TypeMap peMap = TypeMap.decode(peClob.getSubString(0L, (int)peClob.length()));
            potionEffects = peMap.getMapList("potionEffects");
        }
        
        PlayerData(Player player) {
            name = player.getName();
            displayName = player.getDisplayName();
            address = player.getAddress().getAddress().getHostAddress();
            server = Global.plugin.getServer().getName();
            if (server == null) server = "unknown";
            world = player.getWorld().getName();
            Location loc = player.getLocation();
            x = loc.getX();
            y = loc.getY();
            z = loc.getZ();
            PlayerInventory inv = player.getInventory();
            inventory = Inventory.encodeItemStackArray(inv.getContents());
            armor = Inventory.encodeItemStackArray(inv.getArmorContents());
            heldItemSlot = inv.getHeldItemSlot();
            health = player.getHealth();
            remainingAir = player.getRemainingAir();
            fireTicks = player.getFireTicks();
            foodLevel = player.getFoodLevel();
            exhaustion = player.getExhaustion();
            saturation = player.getSaturation();
            gameMode = player.getGameMode().toString();
            level = player.getLevel();
            xp = player.getExp();
            potionEffects = PotionEffects.encodePotionEffects(player.getActivePotionEffects());
            lastUpdated.setTimeInMillis(System.currentTimeMillis());
        }
        
        // call from main thread
        boolean apply(Player player) {
            
            if (! server.equals(Global.plugin.getServer().getName())) {
                // need to send player to another server...
                Server s = Servers.get(server);
                if (s == null) {
                    Utils.warning("unable to teleport player '%s' to unknown server '%s'", name, server);
                    return false;
                }
                String kickMessage = s.getKickMessage(player.getAddress());
                if (kickMessage == null) return false;
                Utils.debug("kicking player '%s' @%s: %s", player.getName(), player.getAddress().getAddress().getHostAddress(), kickMessage);
                player.kickPlayer(kickMessage);
                return true;
            }
            
            World w = Global.plugin.getServer().getWorld(world);
            if (player.getWorld() != w) {
                if (w == null) {
                    Utils.warning("unable to teleport player '%s' to unknown world '%s'", name, world);
                    return false;
                }
            }
            player.teleport(new Location(w, x, y, z));
            
            if (health < 0) health = 0;
            player.setHealth(health);
            if (remainingAir < 0) remainingAir = 0;
            player.setRemainingAir(remainingAir);
            if (foodLevel < 0) foodLevel = 0;
            player.setFoodLevel(foodLevel);
            if (exhaustion < 0) exhaustion = 0;
            player.setExhaustion(exhaustion);
            if (saturation < 0) saturation = 0;
            player.setSaturation(saturation);
            player.setGameMode(Utils.valueOf(GameMode.class, gameMode));
            if (level < 0) level = 0;
            player.setLevel(level);
            if (xp < 0) xp = 0;
            player.setExp(xp);
            player.setFireTicks(fireTicks);
            PlayerInventory inv = player.getInventory();
            ItemStack[] invArray = Inventory.decodeItemStackArray(inventory);
            for (int slot = 0; slot < invArray.length; slot++) {
                ItemStack stack = invArray[slot];
                if (stack == null)
                    inv.setItem(slot, new ItemStack(Material.AIR.getId()));
                else
                    inv.setItem(slot, stack);
            }
            ItemStack[] armorArray = Inventory.decodeItemStackArray(armor);
            inv.setArmorContents(armorArray);
            PotionEffect[] potionArray = PotionEffects.decodePotionEffects(potionEffects);
            for (PotionEffectType pet : PotionEffectType.values()) {
                if (pet == null) continue;
                if (player.hasPotionEffect(pet))
                    player.removePotionEffect(pet);
            }
            for (PotionEffect effect : potionArray) {
                if (effect == null) continue;
                player.addPotionEffect(effect);
            }
            return true;
        }
        
        void save() throws SQLException {
            connect();
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = db.prepareStatement("select id from " + tableName("players") + " where name=?");
                rs = stmt.executeQuery();
                boolean playerExists = rs.next();
                rs.close();
                rs = null;
                stmt.close();
                if (playerExists)
                    stmt = db.prepareStatement("update " + tableName("players") + " set " +
                                "displayName=?, " +
                                "address=?, " +
                                "server=?, " +
                                "world=?, " +
                                "worldX=?, " +
                                "worldY=?, " +
                                "worldZ=?, " +
                                "inventory=?, " +
                                "armor=?, " +
                                "heldItemSlot=?, " +
                                "health=?, " +
                                "remainingAir=?, " +
                                "fireTicks=?, " +
                                "foodLevel=?, " +
                                "exhaustion=?, " +
                                "saturation=?, " +
                                "gameMode=?, " +
                                "level=?, " +
                                "xp=?, " +
                                "potionEffects=?, " +
                                "lastUpdated=? " +
                            "where name=?");
                else
                    stmt = db.prepareStatement("insert into " + tableName("players") + " (" +
                                "displayName," +
                                "address," +
                                "server," +
                                "world," +
                                "worldX," +
                                "worldY," +
                                "worldZ," +
                                "inventory," +
                                "armor," +
                                "heldItemSlot," +
                                "health," +
                                "remainingAir," +
                                "fireTicks," +
                                "foodLevel," +
                                "exhaustion," +
                                "saturation," +
                                "gameMode," +
                                "level," +
                                "xp," +
                                "potionEffects," +
                                "lastUpdated," +
                                "name" +
                            ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                stmt.setString(1, displayName);
                stmt.setString(2, address);
                stmt.setString(3, server);
                stmt.setString(4, world);
                stmt.setDouble(5, x);
                stmt.setDouble(6, y);
                stmt.setDouble(7, z);
                
                TypeMap invValue = new TypeMap();
                invValue.set("inventory", inventory);
                Clob invClob = new SerialClob(invValue.encode().toCharArray());
                stmt.setClob(8, invClob);
                
                TypeMap armorValue = new TypeMap();
                armorValue.set("armor", armor);
                Clob armorClob = new SerialClob(armorValue.encode().toCharArray());
                stmt.setClob(9, armorClob);
                
                stmt.setInt(10, heldItemSlot);
                stmt.setInt(11, health);
                stmt.setInt(12, remainingAir);
                stmt.setInt(13, fireTicks);
                stmt.setInt(14, foodLevel);
                stmt.setFloat(15, exhaustion);
                stmt.setFloat(16, saturation);
                stmt.setString(17, gameMode);
                stmt.setInt(18, level);
                stmt.setFloat(19, xp);
                
                TypeMap peValue = new TypeMap();
                peValue.set("potionEffects", potionEffects);
                Clob peClob = new SerialClob(peValue.encode().toCharArray());
                stmt.setClob(20, peClob);
                
                Timestamp ts = new Timestamp(lastUpdated.getTimeInMillis());
                stmt.setTimestamp(20, ts);
                
                stmt.setString(21, name);
                stmt.execute();
            } finally {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            }
        }
        
    }
    
}
