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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.List;
import javax.sql.rowset.serial.SerialClob;
import org.bennedum.transporter.api.RealmPlayer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class RealmPlayerImpl implements RealmPlayer {

    private boolean fromDb = false;
    private boolean dirty = true;
    
    private String name;
    private String displayName;
    private String address;
    private List<TypeMap> inventory;
    private List<TypeMap> armor;
    private int heldItemSlot;
    private int health;
    private int remainingAir;
    private int fireTicks;
    private int foodLevel;
    private float exhaustion;
    private float saturation;
    private GameMode gameMode;
    private int level;
    private float exp;
    private List<TypeMap> potionEffects;
    
    private String lastServer;
    private String lastWorld;
    private String lastLocation;
    private String home;
    private Calendar lastJoin;
    private Calendar lastQuit;
    private Calendar lastKick;
    private Calendar lastDeath;
    private int deaths = 0;
    private String lastDeathMessage;
    private Calendar lastPlayerKill;
    private int playerKills = 0;
    private String lastPlayerKilled;
    private Calendar lastMobKill;
    private int mobKills = 0;
    private String lastMobKilled;
    
    private Calendar lastUpdated;

    public RealmPlayerImpl(ResultSet rs) throws SQLException {
        fromDb = true;
        dirty = false;
        name = rs.getString("name");
        displayName = rs.getString("displayName");
        address = rs.getString("address");
        Clob invClob = rs.getClob("inventory");
        if (invClob == null) {
            inventory = null;
        } else {
            inventory = (List<TypeMap>) JSON.decode(invClob.getSubString(1L, (int) invClob.length()));
        }
        Clob armorClob = rs.getClob("armor");
        if (armorClob == null) {
            armor = null;
        } else {
            armor = (List<TypeMap>) JSON.decode(armorClob.getSubString(1L, (int) armorClob.length()));
        }
        heldItemSlot = rs.getInt("heldItemSlot");
        health = rs.getInt("health");
        remainingAir = rs.getInt("remainingAir");
        fireTicks = rs.getInt("fireTicks");
        foodLevel = rs.getInt("foodLevel");
        exhaustion = rs.getFloat("exhaustion");
        saturation = rs.getFloat("saturation");
        gameMode = Utils.valueOf(GameMode.class, rs.getString("gameMode"));
        level = rs.getInt("level");
        exp = rs.getFloat("exp");
        Clob peClob = rs.getClob("potionEffects");
        if (peClob == null) {
            potionEffects = null;
        } else {
            potionEffects = (List<TypeMap>) JSON.decode(peClob.getSubString(1L, (int) peClob.length()));
        }
        lastServer = rs.getString("lastServer");
        lastWorld = rs.getString("lastWorld");
        lastLocation = rs.getString("lastLocation");
        home = rs.getString("home");
        lastJoin = getDateTime(rs, "lastJoin");
        lastQuit = getDateTime(rs, "lastQuit");
        lastKick = getDateTime(rs, "lastKick");
        lastDeath = getDateTime(rs, "lastDeath");
        deaths = rs.getInt("deaths");
        lastPlayerKill = getDateTime(rs, "lastPlayerKill");
        playerKills = rs.getInt("playerKills");
        lastPlayerKilled = rs.getString("lastPlayerKilled");
        lastDeath = getDateTime(rs, "lastMobKill");
        mobKills = rs.getInt("mobKills");
        lastMobKilled = rs.getString("lastMobKilled");
        
        lastDeathMessage = rs.getString("lastDeathMessage");
        lastUpdated = getDateTime(rs, "lastUpdated");
        Utils.debug("loaded realm player '%s'", name);
    }

    public RealmPlayerImpl(Player player) {
        name = player.getName();
        update(player);
    }

    public void update(Player player) {
        displayName = player.getDisplayName();
        address = player.getAddress().getAddress().getHostAddress();
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
        gameMode = player.getGameMode();
        level = player.getLevel();
        exp = player.getExp();
        potionEffects = PotionEffects.encodePotionEffects(player.getActivePotionEffects());
        lastServer = Global.plugin.getServer().getServerName();
        if (lastServer == null) {
            lastServer = "unknown";
        }
        Location loc = player.getLocation();
        lastWorld = loc.getWorld().getName();
        updateLastLocation(loc);
    }

    public void updateLastLocation(Location loc) {
        lastLocation = loc.getX() + "," + loc.getY() + "," + loc.getZ();
        dirty = true;
    }

    public void save(Connection conn) throws SQLException {
        PreparedStatement stmt = null;
        try {
            if (fromDb)
                stmt = conn.prepareStatement("update " + Realm.getTableName("players") + " set " +
                        "displayName=?, " +
                        "address=?, " +
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
                        "exp=?, " +
                        "potionEffects=?, " + 
                        "lastServer=?, " +
                        "lastWorld=?, " +
                        "lastLocation=?, " +
                        "home=?, " +
                        "lastJoin=?, " +
                        "lastQuit=?, " +
                        "lastKick=?, " +
                        "lastDeath=?, " +
                        "deaths=?, " +
                        "lastDeathMessage=?, " +
                        "lastPlayerKill=?, " +
                        "playerKills=?, " +
                        "lastPlayerKilled=?, " +
                        "lastMobKill=?, " +
                        "mobKills=?, " +
                        "lastMobKilled=?, " +
                        "lastUpdated=? " +
                    "where name=?");
            else
                stmt = conn.prepareStatement("insert into " + Realm.getTableName("players") + " (" +
                        "displayName," +
                        "address," +
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
                        "exp," +
                        "potionEffects," +
                        "lastServer," +
                        "lastWorld," +
                        "lastLocation," +
                        "home," +
                        "lastJoin," +
                        "lastQuit," +
                        "lastKick," +
                        "lastDeath," +
                        "deaths," +
                        "lastDeathMessage," +
                        "lastPlayerKill," +
                        "playerKills," +
                        "lastPlayerKilled," +
                        "lastMobKill," +
                        "mobKills," +
                        "lastMobKilled," +
                        "lastUpdated," +
                        "name" +
                    ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            int col = 1;
            stmt.setString(col++, displayName);
            stmt.setString(col++, address);
            Clob invClob = new SerialClob(JSON.encode(inventory).toCharArray());
            stmt.setClob(col++, invClob);
            Clob armorClob = new SerialClob(JSON.encode(armor).toCharArray());
            stmt.setClob(col++, armorClob);
            stmt.setInt(col++, heldItemSlot);
            stmt.setInt(col++, health);
            stmt.setInt(col++, remainingAir);
            stmt.setInt(col++, fireTicks);
            stmt.setInt(col++, foodLevel);
            stmt.setFloat(col++, exhaustion);
            stmt.setFloat(col++, saturation);
            stmt.setString(col++, gameMode.toString());
            stmt.setInt(col++, level);
            stmt.setFloat(col++, exp);
            Clob peClob = new SerialClob(JSON.encode(potionEffects).toCharArray());
            stmt.setClob(col++, peClob);
            stmt.setString(col++, lastServer);
            stmt.setString(col++, lastWorld);
            stmt.setString(col++, lastLocation);
            stmt.setString(col++, home);
            setDateTime(stmt, col++, lastJoin);
            setDateTime(stmt, col++, lastQuit);
            setDateTime(stmt, col++, lastKick);
            setDateTime(stmt, col++, lastDeath);
            stmt.setInt(col++, deaths);
            stmt.setString(col++, lastDeathMessage);
            setDateTime(stmt, col++, lastPlayerKill);
            stmt.setInt(col++, playerKills);
            stmt.setString(col++, lastPlayerKilled);
            setDateTime(stmt, col++, lastMobKill);
            stmt.setInt(col++, mobKills);
            stmt.setString(col++, lastMobKilled);
            
            lastUpdated = Calendar.getInstance();
            setDateTime(stmt, col++, lastUpdated);
            stmt.setString(col++, name);
            stmt.execute();
            dirty = false;
            Utils.debug("saved realm player '%s'", name);
        } finally {
            if (stmt != null)
                stmt.close();
        }
    }

    // call from main thread
    public boolean apply(Player player) {
        player.setHealth(health);
        player.setRemainingAir(remainingAir);
        player.setFoodLevel(foodLevel);
        player.setExhaustion(exhaustion);
        player.setSaturation(saturation);
        if (gameMode != null)
            player.setGameMode(gameMode);
        player.setLevel(level);
        player.setExp(exp);
        player.setFireTicks(fireTicks);
        PlayerInventory inv = player.getInventory();
        ItemStack[] invArray = Inventory.decodeItemStackArray(inventory);
        if (invArray != null) {
            for (int slot = 0; slot < invArray.length; slot++) {
                ItemStack stack = invArray[slot];
                if (stack == null) {
                    inv.setItem(slot, new ItemStack(Material.AIR.getId()));
                } else {
                    inv.setItem(slot, stack);
                }
            }
        }
        ItemStack[] armorArray = Inventory.decodeItemStackArray(armor);
        if (armorArray != null) {
            inv.setArmorContents(armorArray);
        }
        PotionEffect[] potionArray = PotionEffects.decodePotionEffects(potionEffects);
        if (potionArray != null) {
            for (PotionEffectType pet : PotionEffectType.values()) {
                if (pet == null) {
                    continue;
                }
                if (player.hasPotionEffect(pet)) {
                    player.removePotionEffect(pet);
                }
            }
            for (PotionEffect effect : potionArray) {
                if (effect == null) {
                    continue;
                }
                player.addPotionEffect(effect);
            }
        }
        player.saveData();
        Utils.debug("restored realm player '%s'", name);
        return true;
    }

    public boolean isDirty() {
        return dirty;
    }
    
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public int getHeldItemSlot() {
        return heldItemSlot;
    }

    @Override
    public int getHealth() {
        return health;
    }

    @Override
    public int getRemainingAir() {
        return remainingAir;
    }

    @Override
    public int getFireTicks() {
        return fireTicks;
    }

    @Override
    public int getFoodLevel() {
        return foodLevel;
    }

    @Override
    public float getExhaustion() {
        return exhaustion;
    }

    @Override
    public float getSaturation() {
        return saturation;
    }

    @Override
    public GameMode getGameMode() {
        return gameMode;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public float getExp() {
        return exp;
    }

    @Override
    public String getLastServer() {
        return lastServer;
    }

    @Override
    public String getLastWorld() {
        return lastWorld;
    }

    @Override
    public String getLastLocation() {
        return lastLocation;
    }

    @Override
    public String getHome() {
        return home;
    }

    public void setHome(String s) {
        home = s;
        dirty = true;
    }
    
    @Override
    public Calendar getLastJoin() {
        return lastJoin;
    }

    public void setLastJoin(Calendar c) {
        lastJoin = c;
        dirty = true;
    }
    
    @Override
    public Calendar getLastQuit() {
        return lastQuit;
    }

    public void setLastQuit(Calendar c) {
        lastQuit = c;
        dirty = true;
    }
    
    @Override
    public Calendar getLastKick() {
        return lastKick;
    }

    public void setLastKick(Calendar c) {
        lastKick = c;
        dirty = true;
    }
    
    @Override
    public Calendar getLastDeath() {
        return lastDeath;
    }

    @Override
    public int getDeaths() {
        return deaths;
    }

    @Override
    public String getLastDeathMessage() {
        return lastDeathMessage;
    }
    
    @Override
    public Calendar getLastPlayerKill() {
        return lastPlayerKill;
    }
    
    @Override
    public int getPlayerKills() {
        return playerKills;
    }

    @Override
    public String getLastPlayerKilled() {
        return lastPlayerKilled;
    }
    
    @Override
    public Calendar getLastMobKill() {
        return lastMobKill;
    }

    @Override
    public int getMobKills() {
        return mobKills;
    }

    @Override
    public String getLastMobKilled() {
        return lastMobKilled;
    }
    
    @Override
    public Calendar getLastUpdated() {
        return lastUpdated;
    }
    
    @Override
    public ItemStack[] getInventory() {
        return Inventory.decodeItemStackArray(inventory);
    }

    @Override
    public ItemStack[] getArmor() {
        return Inventory.decodeItemStackArray(armor);
    }

    @Override
    public PotionEffect[] getPotionEffects() {
        return PotionEffects.decodePotionEffects(potionEffects);
    }

    public void addDeath(String message) {
        lastDeath = Calendar.getInstance();
        deaths++;
        lastDeathMessage = message;
        dirty = true;
    }
    
    public void addPlayerKill(Player killed) {
        lastPlayerKill = Calendar.getInstance();
        playerKills++;
        lastPlayerKilled = killed.getName();
        dirty = true;
    }
    
    public void addMobKill(Entity killed) {
        lastMobKill = Calendar.getInstance();
        mobKills++;
        lastMobKilled = killed.getType().toString();
        dirty = true;
    }

    
    
    private Calendar getDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        if (ts == null) {
            return null;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ts.getTime());
        return c;
    }

    private void setDateTime(PreparedStatement stmt, int col, Calendar c) throws SQLException {
        if (c == null) {
            stmt.setTimestamp(col, null);
        } else {
            Timestamp ts = new Timestamp(c.getTimeInMillis());
            stmt.setTimestamp(col, ts);
        }
    }
    
    
}
