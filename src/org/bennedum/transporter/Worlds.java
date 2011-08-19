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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.util.config.ConfigurationNode;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Worlds {
    
    public static final File WorldBaseFolder = Utils.BukkitBaseFolder;
    private static final Map<String,BukkitWorld> worlds = new HashMap<String,BukkitWorld>();
    
    public static void onConfigLoad(Context ctx) {
        worlds.clear();
        List<ConfigurationNode> worldNodes = Config.getNodeList("worlds");
        if (worldNodes != null) {
            for (ConfigurationNode node : worldNodes) {
                try {
                    BukkitWorld world = new BukkitWorld(node);
                    add(world);
                    if (Config.getAutoLoadWorlds() && world.getAutoLoad())
                        world.load(ctx);
                } catch (WorldException e) {
                    ctx.warn(e.getMessage());
                }
            }
        }
    }
    
    public static void onConfigSave(Context ctx) {
        List<Map<String,Object>> worldNodes = new ArrayList<Map<String,Object>>();
        for (BukkitWorld world : worlds.values())
            worldNodes.add(world.encode());
        Config.setProperty("worlds", worldNodes);
    }
    
    public static void add(BukkitWorld world) {
        worlds.put(world.getName(), world);
    }
    
    public static BukkitWorld get(String name) {
        if (worlds.containsKey(name)) return worlds.get(name);
        BukkitWorld world = null;
        name = name.toLowerCase();
        for (String key : worlds.keySet()) {
            if (key.toLowerCase().startsWith(name)) {
                if (world == null) world = worlds.get(key);
                else return null;
            }
        }
        return world;
    }

    public static List<BukkitWorld> getAll() {
        return new ArrayList<BukkitWorld>(worlds.values());
    }

    public static boolean isEmpty() {
        return size() == 0;
    }

    public static int size() {
        return worlds.size();
    }
    
    public static File worldFolder(String name) {
        return new File(WorldBaseFolder, name);
    }

    public static File worldFolder(World world) {
        return new File(WorldBaseFolder, world.getName());
    }

    public static File worldPluginFolder(World world) {
        return new File(worldFolder(world), Global.pluginName);
    }

}
