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
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.util.config.ConfigurationNode;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class DesignBlockDetail {

    private BuildableBlock buildBlock = null;
    private BuildableBlock openBlock = null;
    private boolean isScreen = false;
    private boolean isPortal = false;
    private boolean isTrigger = false;
    private boolean isSwitch = false;
    private boolean isInsert = false;
    private SpawnDirection spawn = null;
    private boolean isLightning = false;

    public DesignBlockDetail(DesignBlockDetail src, BlockFace direction) {
        if (src.buildBlock != null)
            buildBlock = new BuildableBlock(src.buildBlock, direction);
        if (src.openBlock != null)
            openBlock = new BuildableBlock(src.openBlock, direction);
        isScreen = src.isScreen;
        isPortal = src.isPortal;
        isTrigger = src.isTrigger;
        isSwitch = src.isSwitch;
        isInsert = src.isInsert;
        if (src.spawn != null)
            spawn = src.spawn.rotate(direction);
        isLightning = src.isLightning;
    }

    public DesignBlockDetail(String blockType) throws BlockException {
        buildBlock = new BuildableBlock(blockType);
        if (! buildBlock.hasType()) buildBlock = null;
    }

    public DesignBlockDetail(ConfigurationNode node) throws BlockException {
        ConfigurationNode subNode;
        String str;

        subNode = node.getNode("build");
        if (subNode == null) {
            str = node.getString("build");
            if (str != null)
                buildBlock = new BuildableBlock(str);
        } else
            buildBlock = new BuildableBlock(subNode);
        if ((buildBlock != null) && (! buildBlock.hasType())) buildBlock = null;

        subNode = node.getNode("open");
        if (subNode == null) {
            str = node.getString("open");
            if (str != null)
                openBlock = new BuildableBlock(str);
        } else
            openBlock = new BuildableBlock(subNode);
        if ((openBlock != null) && (! openBlock.hasType())) openBlock = null;

        isScreen = node.getBoolean("screen", false);
        isPortal = node.getBoolean("portal", false);
        isTrigger = node.getBoolean("trigger", false);
        isSwitch = node.getBoolean("switch", false);
        isInsert = node.getBoolean("insert", false);

        str = node.getString("spawn");
        if (str != null) {
            try {
                spawn = Utils.valueOf(SpawnDirection.class, str);
            } catch (IllegalArgumentException iae) {
                throw new BlockException("invalid or ambiguous spawn '%s'", str);
            }
        }

        isLightning = node.getBoolean("lightning", false);
        
        if (isScreen && ((buildBlock == null) || (! buildBlock.isSign())))
            throw new BlockException("screen blocks must be wall signs or sign posts");
    }

    public Map<String,Object> encode() {
        Map<String,Object> node = new HashMap<String,Object>();
        if (buildBlock != null) node.put("build", buildBlock.encode());
        if (openBlock != null) node.put("open", openBlock.encode());
        if (isScreen) node.put("screen", isScreen);
        if (isPortal) node.put("portal", isPortal);
        if (isTrigger) node.put("trigger", isTrigger);
        if (isSwitch) node.put("switch", isSwitch);
        if (isInsert) node.put("insert", isInsert);
        if (spawn != null) node.put("spawn", spawn.toString());
        if (isLightning) node.put("lightning", isLightning);
        return node;
    }

    public BuildableBlock getBuildBlock() {
        return buildBlock;
    }

    public BuildableBlock getOpenBlock() {
        return openBlock;
    }

    public boolean isScreen() {
        return isScreen;
    }

    public boolean isPortal() {
        return isPortal;
    }

    public boolean isTrigger() {
        return isTrigger;
    }

    public boolean isSwitch() {
        return isSwitch;
    }

    public boolean isInsert() {
        return isInsert;
    }

    public boolean isSpawn() {
        return spawn != null;
    }

    public SpawnDirection getSpawn() {
        return spawn;
    }

    public boolean isLightning() {
        return isLightning;
    }

    public boolean isInventory() {
        return (buildBlock != null) && (buildBlock.getType() != Material.AIR.getId());
    }

    public boolean isBuildable() {
        return buildBlock != null;
    }

    public boolean isOpenable() {
        return openBlock != null;
    }

    public boolean isMatchable() {
        return (buildBlock != null) && (buildBlock.getMaterial() != Material.AIR);
    }

    @Override
    public int hashCode() {
        return
                ((buildBlock != null) ? buildBlock.hashCode() : 0) +
                ((openBlock != null) ? openBlock.hashCode() : 0) +
                (isScreen ? 10 : 0) +
                (isPortal ? 100 : 0) +
                (isTrigger ? 1000 : 0) +
                (isSwitch ? 10000 : 0) +
                (isInsert ? 100000 : 0) +
                (isLightning ? 1000000 : 0) +
                ((spawn != null) ? spawn.hashCode() : 0);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (! (obj instanceof DesignBlockDetail)) return false;
        DesignBlockDetail other = (DesignBlockDetail)obj;

        if ((buildBlock == null) && (other.buildBlock != null)) return false;
        if ((buildBlock != null) && (other.buildBlock == null)) return false;
        if ((buildBlock != null) && (other.buildBlock != null) &&
            (! buildBlock.equals(other.buildBlock))) return false;

        if ((openBlock == null) && (other.openBlock != null)) return false;
        if ((openBlock != null) && (other.openBlock == null)) return false;
        if ((openBlock != null) && (other.openBlock != null) &&
            (! openBlock.equals(other.openBlock))) return false;

        if (isScreen != other.isScreen) return false;
        if (isPortal != other.isPortal) return false;
        if (isTrigger != other.isTrigger) return false;
        if (isSwitch != other.isSwitch) return false;
        if (isInsert != other.isInsert) return false;
        if (spawn != other.spawn) return false;
        if (isLightning != other.isLightning) return false;

        return true;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Detail[");
        buf.append("scr=").append(isScreen).append(",");
        buf.append("prt=").append(isPortal).append(",");
        buf.append("trg=").append(isTrigger).append(",");
        buf.append("swt=").append(isSwitch).append(",");
        buf.append("ins=").append(isInsert).append(",");
        buf.append("spw=").append(spawn).append(",");
        buf.append("lng=").append(isLightning).append(",");
        buf.append(buildBlock).append(",");
        buf.append(openBlock);
        buf.append("]");
        return buf.toString();
    }
}
