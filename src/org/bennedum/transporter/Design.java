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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bennedum.transporter.api.GateException;
import org.bennedum.transporter.api.TransporterException;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class Design {

    public static boolean isValidName(String name) {
        if ((name.length() == 0) || (name.length() > 15)) return false;
        return ! (name.contains(".") || name.contains("*"));
    }

    private String name;
    private int duration;
    private boolean buildable;
    private boolean buildFromInventory;
    private boolean linkLocal;
    private boolean linkWorld;
    private boolean linkServer;
    private String linkNoneFormat;
    private String linkUnselectedFormat;
    private String linkOfflineFormat;
    private String linkLocalFormat;
    private String linkWorldFormat;
    private String linkServerFormat;
    private boolean multiLink;
    private boolean restoreOnClose;
    private boolean requirePin;
    private boolean requireValidPin;
    private int invalidPinDamage;
    private boolean protect;
    private boolean sendChat;
    private int sendChatDistance;
    private boolean receiveChat;
    private int receiveChatDistance;
    private boolean requireAllowedItems;
    private boolean receiveInventory;
    private boolean deleteInventory;
    private boolean receiveGameMode;
    private String allowGameModes;
    private GameMode gameMode;
    private boolean receiveXP;
    private boolean receivePotions;
    private boolean requireAllowedPotions;
    private boolean receiveStats;
    private boolean randomNextLink;
    private boolean sendNextLink;
    private String teleportFormat;
    private String noLinksFormat;
    private String noLinkSelectedFormat;
    private String invalidLinkFormat;
    private String unknownLinkFormat;
    private String markerFormat;
    protected boolean hidden;
    protected int linkAddDistance;

    // Economy
    private double buildCost;
    private double createCost;
    private double linkLocalCost;
    private double linkWorldCost;
    private double linkServerCost;
    private double sendLocalCost;
    private double sendWorldCost;
    private double sendServerCost;
    private double receiveLocalCost;
    private double receiveWorldCost;
    private double receiveServerCost;

    private Set<String> bannedItems = new HashSet<String>();
    private Set<String> allowedItems = new HashSet<String>();
    private Map<String,String> replaceItems = new HashMap<String,String>();

    private List<Pattern> buildWorlds = null;
    private List<DesignBlock> blocks = null;

    private int sizeX, sizeY, sizeZ;    // calculated

    @SuppressWarnings("unchecked")
    public Design(File file) throws DesignException, BlockException {
        if (! file.exists())
            throw new DesignException("%s not found", file.getAbsolutePath());
        if (! file.isFile())
            throw new DesignException("%s is not a file", file.getAbsolutePath());
        if (! file.canRead())
            throw new DesignException("unable to read %s", file.getAbsoluteFile());
        TypeMap conf = new TypeMap(file);
        conf.load();

        name = conf.getString("name");
        duration = conf.getInt("duration", -1);
        buildable = conf.getBoolean("buildable", true);
        buildFromInventory = conf.getBoolean("buildFromInventory", false);
        linkLocal = conf.getBoolean("linkLocal", true);
        linkWorld = conf.getBoolean("linkWorld", true);
        linkServer = conf.getBoolean("linkServer", true);
        linkNoneFormat = conf.getString("linkNoneFormat", "%fromGate%\\n\\n<none>");
        linkUnselectedFormat = conf.getString("linkUnselectedFormat", "%fromGate%\\n\\n<unselected>");
        linkOfflineFormat = conf.getString("linkOfflineFormat", "%fromGate%\\n\\n<offline>");
        linkLocalFormat = conf.getString("linkLocalFormat", "%fromGate%\\n%toGate%");
        linkWorldFormat = conf.getString("linkWorldFormat", "%fromGate%\\n%toWorld%\\n%toGate%");
        linkServerFormat = conf.getString("linkServerFormat", "%fromGate%\\n%toServer%\\n%toWorld%\\n%toGate%");
        multiLink = conf.getBoolean("multiLink", true);
        restoreOnClose = conf.getBoolean("restoreOnClose", false);
        requirePin = conf.getBoolean("requirePin", false);
        requireValidPin = conf.getBoolean("requireValidPin", true);
        invalidPinDamage = conf.getInt("invalidPinDamage", 0);
        protect = conf.getBoolean("protect", false);
        sendChat = conf.getBoolean("sendChat", false);
        sendChatDistance = conf.getInt("sendChatDistance", 1000);
        receiveChat = conf.getBoolean("receiveChat", false);
        receiveChatDistance = conf.getInt("receiveChatDistance", 1000);
        requireAllowedItems = conf.getBoolean("requireAllowedItems", true);
        receiveInventory = conf.getBoolean("receiveInventory", true);
        deleteInventory = conf.getBoolean("deleteInventory", false);
        receiveGameMode = conf.getBoolean("receiveGameMode", false);
        allowGameModes = conf.getString("allowGameModes", "*");
        receiveXP = conf.getBoolean("receiveXP", false);
        receivePotions = conf.getBoolean("receivePotions", false);
        requireAllowedPotions = conf.getBoolean("requireAllowedPotions", true);
        receiveStats = conf.getBoolean("receiveStats", true);
        randomNextLink = conf.getBoolean("randomNextLink", false);
        sendNextLink = conf.getBoolean("sendNextLink", false);
        teleportFormat = conf.getString("teleportFormat", "%GOLD%teleported to '%toGateCtx%'");
        noLinksFormat = conf.getString("noLinksFormat", "this gate has no links");
        noLinkSelectedFormat = conf.getString("noLinkSelectedFormat", "no link is selected");
        invalidLinkFormat = conf.getString("invalidLinkFormat", "invalid link selected");
        unknownLinkFormat = conf.getString("unknownLinkFormat", "unknown or offline destination endpoint");
        markerFormat = conf.getString("markerFormat", "%name%");
        hidden = conf.getBoolean("hidden", false);
        linkAddDistance = conf.getInt("linkAddDistance", -1);

        String gameModeStr = conf.getString("gameMode", null);
        if (gameModeStr == null)
            gameMode = null;
        else {
            try {
                gameMode = Utils.valueOf(GameMode.class, gameModeStr);
            } catch (IllegalArgumentException iae) {
                throw new DesignException(iae.getMessage() + " game mode '%s'", gameModeStr);
            }
        }
        
        List<String> items = conf.getStringList("bannedItems", new ArrayList<String>());
        for (String item : items) {
            String i = Inventory.normalizeItem(item);
            if (i == null)
                throw new DesignException("invalid banned item '%s'", item);
            bannedItems.add(i);
        }

        items = conf.getStringList("allowedItems", new ArrayList<String>());
        for (String item : items) {
            String i = Inventory.normalizeItem(item);
            if (i == null)
                throw new DesignException("invalid allowed item '%s'", item);
            allowedItems.add(i);
        }

        items = conf.getKeys("replaceItems");
        if (items != null) {
            for (String oldItem : items) {
                String oi = Inventory.normalizeItem(oldItem);
                if (oi == null)
                    throw new DesignException("invalid replace item '%s'", oldItem);
                String newItem = conf.getString("replaceItems." + oldItem);
                String ni = Inventory.normalizeItem(newItem);
                if (ni == null)
                    throw new DesignException("invalid replace item '%s'", newItem);
                replaceItems.put(oi, ni);
            }
        }

        // Economy
        buildCost = conf.getDouble("buildCost", 0);
        createCost = conf.getDouble("createCost", 0);
        linkLocalCost = conf.getDouble("linkLocalCost", 0);
        linkWorldCost = conf.getDouble("linkWorldCost", 0);
        linkServerCost = conf.getDouble("linkServerCost", 0);
        sendLocalCost = conf.getDouble("sendLocalCost", 0);
        sendWorldCost = conf.getDouble("sendWorldCost", 0);
        sendServerCost = conf.getDouble("sendServerCost", 0);
        receiveLocalCost = conf.getDouble("receiveLocalCost", 0);
        receiveWorldCost = conf.getDouble("receiveWorldCost", 0);
        receiveServerCost = conf.getDouble("receiveServerCost", 0);

        buildWorlds = new ArrayList<Pattern>();
        String pattern = conf.getString("buildWorlds");
        if (pattern != null)
            try {
                buildWorlds.add(Pattern.compile(pattern));
            } catch (PatternSyntaxException pse) {
                throw new DesignException("invalid buildWorld pattern '%s': %s", pattern, pse.getMessage());
            }
        else {
            List<String> patternList = conf.getStringList("buildWorlds", null);
            if (patternList == null)
                buildWorlds.add(Pattern.compile(".*"));
            else {
                for (String pat : patternList) {
                    try {
                        buildWorlds.add(Pattern.compile(pat));
                    } catch (PatternSyntaxException pse) {
                        throw new DesignException("invalid buildWorld pattern '%s': %s", pat, pse.getMessage());
                    }
                }
            }
        }

        List<String> blockKeys = conf.getKeys("blockKey");
        if (blockKeys == null)
            throw new DesignException("blockKey mappings are required");
        Map<Character,DesignBlockDetail> blockKey = new HashMap<Character,DesignBlockDetail>();
        for (String key : blockKeys) {
            if (key.length() > 1)
                throw new DesignException("blockKey keys must be a single character: %s", key);
            DesignBlockDetail db;
            TypeMap blockKeyMap = conf.getMap("blockKey." + key);
            if (blockKeyMap == null) {
                String blockType = conf.getString("blockKey." + key);
                if (blockType == null)
                    throw new DesignException("missing material for blockKey key '%s'", key);
                else
                    db = new DesignBlockDetail(blockType);
            } else
                db = new DesignBlockDetail(blockKeyMap);
            blockKey.put(key.charAt(0), db);
        }

        blocks = new ArrayList<DesignBlock>();
        sizeX = sizeY = sizeZ = -1;
        int x, y, z;
        List<Object> blocksMap = conf.getList("blocks");
        if (blocksMap == null)
            throw new DesignException("at least one block slice is required");
        z = sizeZ = blocksMap.size();
        for (Object o : blocksMap) {
            z--;
            if ((! (o instanceof List)) ||
                ((List)o).isEmpty() ||
                (! (((List)o).get(0) instanceof String)))
                throw new DesignException("block slice %d is not a list of strings", sizeZ - z);
            List<String> lines = (List<String>)o;
            if (sizeY == -1)
                sizeY = lines.size();
            else if (sizeY != lines.size())
                throw new DesignException("block slice %d does not have %d lines", sizeZ - z, sizeY);
            y = sizeY;
            for (String line : lines) {
                y--;
                line = line.trim();
                if (sizeX == -1)
                    sizeX = line.length();
                else if (sizeX != line.length())
                    throw new DesignException("block slice %d, line %d does not have %d blocks", sizeZ - z, sizeY - y, sizeX);
                x = sizeX;
                for (char ch : line.toCharArray()) {
                    x--;
                    if (! blockKey.containsKey(ch))
                        throw new DesignException("block slice %d, line %d, block %d '%s' does not have a mapping in the blockKey", sizeZ - z, sizeY - y, sizeX - x, ch);
                    DesignBlockDetail db = blockKey.get(ch);
                    if (db == null)
                        throw new DesignException("unknown block key '%s'", ch);
                    blocks.add(new DesignBlock(x, y, z, db));
                }
            }
        }

        if (name == null)
            throw new DesignException("name is required");
        if (! isValidName(name))
            throw new DesignException("name is not valid");

        if (sizeX > 255)
            throw new DesignException("must be less than 255 blocks wide");
        if (sizeY > 255)
            throw new DesignException("must be less than 255 blocks high");
        if (sizeZ > 255)
            throw new DesignException("must be less than 255 blocks deep");
//        if ((sizeX * sizeY * sizeZ) < 4)
//            throw new DesignException("volume of gate must be at least 4 cubic meters");

        int screenCount = 0,
            triggerCount = 0,
            switchCount = 0,
            spawnCount = 0,
            portalCount = 0,
            insertCount = 0;
        for (DesignBlock db : blocks) {
            DesignBlockDetail d = db.getDetail();
            if (d.isScreen()) screenCount++;
            if (d.isTrigger()) triggerCount++;
            if (d.isSwitch()) switchCount++;
            if (d.isPortal()) portalCount++;
            if (d.isInsert()) insertCount++;
            if (d.isSpawn()) spawnCount++;
        }

//        if (screenCount == 0)
//            throw new DesignException("must have at least one screen block");
        if (insertCount != 1)
            throw new DesignException("must have exactly one insert block");
//        if (triggerCount == 0)
//            throw new DesignException("must have at least one trigger block");
        if (portalCount == 0)
            throw new DesignException("must have at least one portal block");
//        if (multiLink && (switchCount == 0))
//            throw new DesignException("must have at least one switch block because multiLink is true");
        if (spawnCount == 0)
            throw new DesignException("must have at least one spawn block");
    }

    public void dump(Context ctx) {
        Utils.debug("Design:");
        Utils.debug("  Blocks:");
        for (DesignBlock db : blocks) {
            Utils.debug("    %s", db);
        }
    }

    public String getName() {
        return name;
    }

    public int getDuration() {
        return duration;
    }

    public boolean isAlwaysOpen() {
        return duration == -1;
    }

    public boolean isBuildable() {
        return buildable;
    }

    public boolean mustBuildFromInventory() {
        return buildFromInventory;
    }

    public boolean getLinkLocal() {
        return linkLocal;
    }

    public boolean getLinkWorld() {
        return linkWorld;
    }

    public boolean getLinkServer() {
        return linkServer;
    }

    public String getLinkNoneFormat() {
        return linkNoneFormat;
    }
    
    public String getLinkUnselectedFormat() {
        return linkUnselectedFormat;
    }
    
    public String getLinkOfflineFormat() {
        return linkOfflineFormat;
    }
    
    public String getLinkLocalFormat() {
        return linkLocalFormat;
    }
    
    public String getLinkWorldFormat() {
        return linkWorldFormat;
    }
    
    public String getLinkServerFormat() {
        return linkServerFormat;
    }
    
    public boolean getMultiLink() {
        return multiLink;
    }

    public boolean getRestoreOnClose() {
        return restoreOnClose;
    }

    public boolean getRequirePin() {
        return requirePin;
    }

    public boolean getRequireValidPin() {
        return requireValidPin;
    }

    public int getInvalidPinDamage() {
        return invalidPinDamage;
    }

    public boolean getProtect() {
        return protect;
    }
    
    public boolean getSendChat() {
        return sendChat;
    }

    public int getSendChatDistance() {
        return sendChatDistance;
    }

    public boolean getReceiveChat() {
        return receiveChat;
    }

    public int getReceiveChatDistance() {
        return receiveChatDistance;
    }

    public boolean getRequireAllowedItems() {
        return requireAllowedItems;
    }

    public Set<String> getBannedItems() {
        return bannedItems;
    }

    public Set<String> getAllowedItems() {
        return allowedItems;
    }

    public Map<String,String> getReplaceItems() {
        return replaceItems;
    }

    public boolean getReceiveInventory() {
        return receiveInventory;
    }

    public boolean getDeleteInventory() {
        return deleteInventory;
    }

    public boolean getReceiveGameMode() {
        return receiveGameMode;
    }

    public String getAllowGameModes() {
        return allowGameModes;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public boolean getReceiveXP() {
        return receiveXP;
    }

    public boolean getReceivePotions() {
        return receivePotions;
    }

    public boolean getRequireAllowedPotions() {
        return requireAllowedPotions;
    }

    public boolean getReceiveStats() {
        return receiveStats;
    }

    public boolean getRandomNextLink() {
        return randomNextLink;
    }

    public boolean getSendNextLink() {
        return sendNextLink;
    }

    public String getTeleportFormat() {
        return teleportFormat;
    }

    public String getNoLinksFormat() {
        return noLinksFormat;
    }

    public String getNoLinkSelectedFormat() {
        return noLinkSelectedFormat;
    }

    public String getInvalidLinkFormat() {
        return invalidLinkFormat;
    }

    public String getUnknownLinkFormat() {
        return unknownLinkFormat;
    }

    public String getMarkerFormat() {
        return markerFormat;
    }

    public boolean getHidden() {
        return hidden;
    }

    public int getLinkAddDistance() {
        return linkAddDistance;
    }

    public double getBuildCost() {
        return buildCost;
    }

    public double getCreateCost() {
        return createCost;
    }

    public double getLinkLocalCost() {
        return linkLocalCost;
    }

    public double getLinkWorldCost() {
        return linkWorldCost;
    }

    public double getLinkServerCost() {
        return linkServerCost;
    }

    public double getSendLocalCost() {
        return sendLocalCost;
    }

    public double getSendWorldCost() {
        return sendWorldCost;
    }

    public double getSendServerCost() {
        return sendServerCost;
    }

    public double getReceiveLocalCost() {
        return receiveLocalCost;
    }

    public double getReceiveWorldCost() {
        return receiveWorldCost;
    }

    public double getReceiveServerCost() {
        return receiveServerCost;
    }

    private Collection<DesignBlock> getScreenBlocks() {
        Collection<DesignBlock> screens = new ArrayList<DesignBlock>();
        for (DesignBlock db : blocks)
            if (db.getDetail().isScreen())
                screens.add(db);
        return screens;
    }

    private DesignBlock getInsertBlock() {
        for (DesignBlock db : blocks)
            if (db.getDetail().isInsert()) return db;
        return null;
    }

    public boolean isBuildableInWorld(World world) {
        String worldName = world.getName();
        for (Pattern pattern : buildWorlds)
            if (pattern.matcher(worldName).matches()) return true;
        return false;
    }

    public Map<Material,Integer> getInventoryBlocks() {
        Map<Material,Integer> ib = new EnumMap<Material,Integer>(Material.class);
        for (DesignBlock db : blocks)
            if (db.getDetail().isInventory()) {
                Material m = db.getDetail().getBuildBlock().getMaterial();
                if (ib.containsKey(m))
                    ib.put(m, ib.get(m) + 1);
                else
                    ib.put(m, 1);
            }
        return ib;
    }

    // Builds a gate at the specified location.
    // Location must include a yaw that indicates the gate's direction.
    public DesignMatch build(Location location, String playerName) throws DesignException {
        
        // must be in a buildable world
        World world = location.getWorld();
        String worldName = world.getName();
        boolean matched = false;
        for (Pattern pattern : buildWorlds)
            if (pattern.matcher(worldName).matches()) {
                matched = true;
                break;
            }
        if (! matched)
            throw new DesignException("unable to build in this world");
        
        DesignBlock insertBlock = getInsertBlock();

        BlockFace direction;
        double yaw = location.getYaw();
        while (yaw < 0) yaw += 360;
        if ((yaw > 315) || (yaw <= 45)) direction = BlockFace.WEST;
        else if ((yaw > 45) && (yaw <= 135)) direction = BlockFace.NORTH;
        else if ((yaw > 135) && (yaw <= 225)) direction = BlockFace.EAST;
        else direction = BlockFace.SOUTH;

        // adjust location to represent 0,0,0 of design blocks
        switch (direction) {
            case NORTH:
                translate(location, insertBlock.getZ(), -insertBlock.getY(), -insertBlock.getX());
                break;
            case EAST:
                translate(location, insertBlock.getX(), -insertBlock.getY(), insertBlock.getZ());
                break;
            case SOUTH:
                translate(location, -insertBlock.getZ(), -insertBlock.getY(), insertBlock.getX());
                break;
            case WEST:
                translate(location, -insertBlock.getX(), -insertBlock.getY(), -insertBlock.getZ());
                break;
        }

        if ((location.getBlockY() + sizeY) > 255)
            throw new DesignException("insertion point is too high to build");
        if (location.getBlockY() < 0)
            throw new DesignException("insertion point is too low to build");

        List<GateBlock> gateBlocks = generateGateBlocks(location, direction);

        // check blocks that will be replaced (can't build in bedrock)
        for (GateBlock gb : gateBlocks) {
            if (! gb.getDetail().isBuildable()) continue;
            if (gb.getLocation().getBlock().getType() == Material.BEDROCK)
                throw new DesignException("unable to build in bedrock");
        }

        // build it!
        List<SavedBlock> savedBlocks = new ArrayList<SavedBlock>();
        for (GateBlock gb : gateBlocks) {
            if (! gb.getDetail().isBuildable()) continue;
            savedBlocks.add(new SavedBlock(gb.getLocation()));
            gb.getDetail().getBuildBlock().build(gb.getLocation());
        }
        Designs.setBuildUndo(playerName, savedBlocks);
        return new DesignMatch(this, gateBlocks, world, direction);
    }

    // Attempts to match the blocks around the given location with this design.
    // The location should be the location of one of the design's screen.
    public DesignMatch matchScreen(Location location) {
        Utils.debug("checking design '%s'", name);

        // must be in a buildable world
        World world = location.getWorld();
        String worldName = world.getName();
        boolean matched = false;
        for (Pattern pattern : buildWorlds)
            if (pattern.matcher(worldName).matches()) {
                matched = true;
                break;
            }
        if (! matched) return null;
        Utils.debug("world is OK");

        Block targetBlock = location.getBlock();
        BlockFace direction = null;
        List<GateBlock> gateBlocks = null;
        matched = false;

        // iterate over each screen trying to find a match with what's around the targetBlock
        for (DesignBlock screenBlock : getScreenBlocks()) {
            location = targetBlock.getLocation();
            direction = screenBlock.getDetail().getBuildBlock().matchTypeAndDirection(targetBlock);
            if (direction == null) continue;

            // adjust location to represent 0,0,0 of design blocks
            switch (direction) {
                case NORTH:
                    translate(location, screenBlock.getZ(), -screenBlock.getY(), -screenBlock.getX());
                    break;
                case EAST:
                    translate(location, screenBlock.getX(), -screenBlock.getY(), screenBlock.getZ());
                    break;
                case SOUTH:
                    translate(location, -screenBlock.getZ(), -screenBlock.getY(), screenBlock.getX());
                    break;
                case WEST:
                    translate(location, -screenBlock.getX(), -screenBlock.getY(), -screenBlock.getZ());
                    break;
                default:
                    continue;
            }
            Utils.debug("matched a screen");

            gateBlocks = generateGateBlocks(location, direction);

            // check the target blocks to make sure they match the design
            matched = true;
            for (GateBlock gb : gateBlocks) {
                if (gb.getDetail().isMatchable() &&
                    (! gb.getDetail().getBuildBlock().matches(gb.getLocation()))) {
                    matched = false;
                    break;
                }
            }
            if (matched) break;
            Utils.debug("blocks don't match");
        }

        if (! matched) {
            Utils.debug("didn't match design");
            return null;
        }
        Utils.debug("matched design!");

        return new DesignMatch(this, gateBlocks, world, direction);
    }
    
    // Returns a new gate if a match in the surrounding blocks is found, otherwise null.
    public LocalBlockGateImpl create(DesignMatch match, String playerName, String gateName) throws GateException {
        LocalBlockGateImpl gate = new LocalBlockGateImpl(match.world, gateName, playerName, match.direction, this, match.gateBlocks);
        return gate;
    }

    // Builds a gate at the specified location, creates it, and returns it.
    // The location must contain a yaw that indicates the gate direction.
    public LocalGateImpl create(Location location, String playerName, String gateName) throws TransporterException {
        DesignMatch match = build(location, playerName);
        return create(match, playerName, gateName);
    }
    
    private List<GateBlock> generateGateBlocks(Location location, BlockFace direction) {
        List<GateBlock> gateBlocks = new ArrayList<GateBlock>();
        Map<DesignBlockDetail,DesignBlockDetail> cache = new HashMap<DesignBlockDetail,DesignBlockDetail>();
        for (DesignBlock db : blocks) {
            DesignBlockDetail detail;
            if (cache.containsKey(db.getDetail())) {
                detail = cache.get(db.getDetail());
            } else {
                detail = new DesignBlockDetail(db.getDetail(), direction);
                cache.put(db.getDetail(), detail);
            }
            gateBlocks.add(new GateBlock(detail, rotate(location, direction, db.getX(), db.getY(), db.getZ())));
        }
        return gateBlocks;
    }

    private Location translate(Location loc, int dx, int dy, int dz) {
        loc.setX(loc.getBlockX() + dx);
        loc.setY(loc.getBlockY() + dy);
        loc.setZ(loc.getBlockZ() + dz);
        return loc;
    }

    private Location rotate(Location loc, BlockFace facing, int offX, int offY, int offZ) {
        switch (facing) {
            case NORTH:
                return new Location(loc.getWorld(),
                        loc.getBlockX() - offZ,
                        loc.getBlockY() + offY,
                        loc.getBlockZ() + offX);
            case EAST:
                return new Location(loc.getWorld(),
                        loc.getBlockX() - offX,
                        loc.getBlockY() + offY,
                        loc.getBlockZ() - offZ);
            case SOUTH:
                return new Location(loc.getWorld(),
                        loc.getBlockX() + offZ,
                        loc.getBlockY() + offY,
                        loc.getBlockZ() - offX);
            case WEST:
                return new Location(loc.getWorld(),
                        loc.getBlockX() + offX,
                        loc.getBlockY() + offY,
                        loc.getBlockZ() + offZ);
        }
        return null;
    }

}
