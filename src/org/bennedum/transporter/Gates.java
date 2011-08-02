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

import com.iConomy.iConomy;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Manages a collection of both local and remote gates.
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Gates {

    // Local gates are stored as worldName.gateName
    // Remote gates are stored as serverName.worldName.gateName
    private static final Map<String,Gate> gates = new HashMap<String,Gate>();

    // Gate build blocks that are protected
    private static final GateMap protectBlocks = new GateMap();

    // Gate screens for local gates
    private static final GateMap screenBlocks = new GateMap();

    // Gate switches for local gates
    private static final GateMap switchBlocks = new GateMap();

    // Gate triggers for local gates
    private static final GateMap triggerBlocks = new GateMap();

    // Portal blocks for open, local gates
    private static final GateMap portalBlocks = new GateMap();


    //private Set<String> teleportingPlayers = new HashSet<String>();


    public static void loadAll(Context ctx) {
        for (String name : new ArrayList<String>(gates.keySet()))
            if (gates.get(name).isSameServer())
                gates.remove(name);
        List<String> loadedWorlds = new ArrayList<String>();
        for (World world : Global.plugin.getServer().getWorlds()) {
            loadedWorlds.add(world.getName());
            loadGatesForWorld(ctx, world);
        }

        // check all loaded, local gate links for non-loaded worlds and load them and their gates
        for (;;) {
            List<String> newWorlds = new ArrayList<String>();
            for (LocalGate gate : getLocalGates()) {
                for (String link : gate.getLinks()) {
                    String world = LocalGate.getLocalLinkWorldName(link);
                    if (world == null) continue;
                    if ((! newWorlds.contains(world)) && (! loadedWorlds.contains(world)))
                        newWorlds.add(world);
                }
            }
            if (newWorlds.isEmpty()) break;
            for (String worldName : newWorlds) {
                ctx.sendLog("loading world '%s'...", worldName);
                // This is a hack since there's no "loadWorld" method
                World world = Global.plugin.getServer().createWorld(worldName, World.Environment.NORMAL);
                loadedWorlds.add(worldName);
                loadGatesForWorld(ctx, world);
            }
        }

        if (isEmpty())
            ctx.send("no gates loaded");
    }

    public static void loadGatesForWorld(Context ctx, World world) {
        File worldFolder = Utils.worldPluginFolder(world);
        File gatesFolder = new File(worldFolder, "gates");
        if (! gatesFolder.exists()) return;
        for (File gateFile : Utils.listYAMLFiles(gatesFolder)) {
            try {
                LocalGate gate = new LocalGate(world, gateFile);
                try {
                    add(gate);
                    gate.initialize();
                    ctx.sendLog("loaded gate '%s' for world '%s'", gate.getName(), world.getName());
                } catch (GateException ge) {
                    ctx.warnLog("unable to load gate '%s' for world '%s': %s", gate.getName(), world.getName(), ge.getMessage());
                }
            } catch (TransporterException ge) {
                ctx.warnLog("'%s' contains an invalid gate: %s", gateFile.getPath(), ge.getMessage());
            }
        }
    }

    public static void saveAll(Context ctx) {
        exportJSON();
        if (size() == 0) {
            ctx.send("no gates to save");
            return;
        }
        for (String name : gates.keySet()) {
            Gate gate = gates.get(name);
            if (! gate.isSameServer()) continue;
            ((LocalGate)gate).save();
            ctx.sendLog("saved gate '%s' for world '%s'", gate.getName(), gate.getWorldName());
        }
    }

    public static void exportJSON() {
        String fileName = Global.config.getString("exportedGatesFile");
        if (fileName == null) return;
        File file = new File(fileName);
        if (! file.isAbsolute())
            file = new File(Global.plugin.getDataFolder(), fileName);
        Utils.debug("exporting gates to %s", file.getAbsolutePath());
        try {
            PrintStream out = new PrintStream(file);
            out.println("[");
            for (Iterator<LocalGate> i = getLocalGates().iterator(); i.hasNext();) {
                LocalGate gate = i.next();
                Vector center = gate.getCenter();
                out.println("  {");
                out.println("    \"name\": \"" + gate.getName() + "\",");
                out.println("    \"world\": \"" + gate.getWorldName() + "\",");
                out.println("    \"links\": [");
                for (Iterator<String> li = gate.getLinks().iterator(); li.hasNext();) {
                    out.print("      \"" + li.next() + "\"");
                    out.println(li.hasNext() ? "," : "");
                }
                out.println("    ],");
                out.println("    \"x\": " + center.getX() + ",");
                out.println("    \"y\": " + center.getY() + ",");
                out.println("    \"z\": " + center.getZ() + ",");
                if (Economy.isAvailable()) {
                    if (gate.getLinkLocal()) {
                        out.println("    \"onWorldSend\": \"" + iConomy.format(gate.getSendLocalCost()) + "\",");
                        out.println("    \"onWorldReceive\": \"" + iConomy.format(gate.getReceiveLocalCost()) + "\",");
                    }
                    if (gate.getLinkWorld()) {
                        out.println("    \"offWorldSend\": \"" + iConomy.format(gate.getSendWorldCost()) + "\",");
                        out.println("    \"offWorldReceive\": \"" + iConomy.format(gate.getReceiveWorldCost()) + "\",");
                    }
                    if (gate.getLinkServer()) {
                        out.println("    \"offServerSend\": \"" + iConomy.format(gate.getSendServerCost()) + "\",");
                        out.println("    \"offServerReceive\": \"" + iConomy.format(gate.getReceiveServerCost()) + "\",");
                    }
                }
                out.println("    \"design\": \"" + gate.getDesignName() + "\",");
                out.println("    \"creator\": \"" + gate.getCreatorName() + "\"");
                out.println("  }" + (i.hasNext() ? "," : ""));
            }
            out.println("]");
            out.close();
        } catch (IOException ioe) {
            Utils.warning("unable to write %s: %s", file.getAbsolutePath(), ioe.getMessage());
        }
    }

    public static void add(Gate gate) throws GateException {
        String name = gate.getFullName();
        if (gates.containsKey(name))
            throw new GateException("a gate with the same name already exists here");
        gates.put(name, gate);
        if (gate.isSameServer()) {
            LocalGate localGate = (LocalGate)gate;
            screenBlocks.putAll(localGate.getScreenBlocks());
            triggerBlocks.putAll(localGate.getTriggerBlocks());
            switchBlocks.putAll(localGate.getSwitchBlocks());
        }
        for (Gate g : gates.values()) {
            if (! g.isSameServer()) continue;
            ((LocalGate)g).onGateAdded(gate);
        }
        if (gate.isSameServer()) {
            exportJSON();
            for (Server server : Servers.getAll())
                server.doGateAdded((LocalGate)gate);
        }
    }

    public static void remove(LocalGate gate) {
        String name = gate.getFullName();
        if (! gates.containsKey(name)) return;
        gates.remove(name);
        screenBlocks.removeGate(gate);
        switchBlocks.removeGate(gate);
        triggerBlocks.removeGate(gate);
        Global.deselectGate(gate);
        for (Gate g : gates.values()) {
            if (! g.isSameServer()) continue;
            ((LocalGate)g).onGateRemoved(gate);
        }
        for (Server server : Servers.getAll())
            server.doGateRemoved(gate);
        exportJSON();
    }

    public static void remove(RemoteGate gate) {
        String name = gate.getFullName();
        if (! gates.containsKey(name)) return;
        gates.remove(name);
        for (Gate g : gates.values()) {
            if (! g.isSameServer()) continue;
            ((LocalGate)g).onGateRemoved(gate);
        }
    }

    public static void remove(Server server) {
        for (RemoteGate gate : getRemoteGates()) {
            if (gate.getServer() == server)
                remove(gate);
        }
    }

    public static void remove(World world) {
        for (LocalGate gate : getLocalGates()) {
            if (gate.getWorldName().equals(world.getName()))
                remove(gate);
        }
    }

    public static void destroy(LocalGate gate, boolean unbuild) {
        String name = gate.getFullName();
        if (! gates.containsKey(name)) return;
        gates.remove(name);
        gate.destroy(unbuild);
        screenBlocks.removeGate(gate);
        switchBlocks.removeGate(gate);
        triggerBlocks.removeGate(gate);
        Global.deselectGate(gate);
        for (Gate g : gates.values()) {
            if (! g.isSameServer()) continue;
            ((LocalGate)g).onGateDestroyed(gate);
        }
        for (Server server : Servers.getAll())
            server.doGateDestroyed(gate);
        exportJSON();
    }

    public static void destroy(RemoteGate gate) {
        String name = gate.getFullName();
        if (! gates.containsKey(name)) return;
        gates.remove(name);
        for (Gate g : gates.values()) {
            if (! g.isSameServer()) continue;
            ((LocalGate)g).onGateDestroyed(gate);
        }
    }

    public static void rename(String fullName, String newName) throws GateException {
        Gate gate = get(fullName);
        if (gate == null)
            throw new GateException("gate not found");
        rename(gate, newName);
    }

    public static void rename(Gate gate, String newName) throws GateException {
        if (! Gate.isValidName(newName))
            throw new GateException("the gate name is invalid");
        String oldFullName = gate.getFullName();
        String oldName = gate.getName();
        gate.setName(newName);
        String newFullName = gate.getFullName();
        if (gates.containsKey(newFullName)) {
            gate.setName(oldName);
            throw new GateException("a gate with the same name already exists");
        }
        gates.remove(oldFullName);
        gates.put(newFullName, gate);
        gate.onRenameComplete();
        for (Gate g : gates.values()) {
            if (! g.isSameServer()) continue;
            ((LocalGate)g).onGateRenamed(gate, oldFullName);
        }
        if (gate.isSameServer()) {
            exportJSON();
            for (Server server : Servers.getAll())
                server.doGateRenamed(oldFullName, newName);
        }
    }

    public static Gate get(Context ctx, String name) {
        int pos = name.indexOf('.');
        if (pos == -1) {
            // asking for a local gate in the player's current world
            if (! ctx.isPlayer()) return null;
            name = ctx.getPlayer().getWorld().getName() + "." + name;
        }
        return get(name);
    }

    public static Gate get(String name) {
        if (gates.containsKey(name)) return gates.get(name);
        Gate gate = null;
        name = name.toLowerCase();
        for (String key : gates.keySet()) {
            if (key.toLowerCase().startsWith(name)) {
                if (gate == null) gate = gates.get(key);
                else return null;
            }
        }
        return gate;
    }

    public static LocalGate getLocalGate(Context ctx, String name) {
        int pos = name.indexOf('.');
        if (pos == -1) {
            // asking for a local gate in the player's current world
            if (! ctx.isPlayer()) return null;
            name = ctx.getPlayer().getWorld().getName() + "." + name;
        }
        return getLocalGate(name);

    }

    public static LocalGate getLocalGate(String name) {
        Gate gate = null;
        if (gates.containsKey(name))
            gate = gates.get(name);
        else {
            name = name.toLowerCase();
            for (String key : gates.keySet()) {
                if ((key.toLowerCase().startsWith(name)) && gates.get(key).isSameServer()) {
                    if (gate == null) gate = gates.get(key);
                    else return null;
                }
            }
        }
        return ((gate != null) && gate.isSameServer()) ? (LocalGate)gate : null;
    }

    public static List<Gate> getAll() {
        return new ArrayList<Gate>(gates.values());
    }

    public static Collection<LocalGate> getLocalGates() {
        Collection<LocalGate> g = new ArrayList<LocalGate>();
        for (Gate gate : gates.values())
            if (gate.isSameServer()) g.add((LocalGate)gate);
        return g;
    }

    public static Collection<RemoteGate> getRemoteGates() {
        Collection<RemoteGate> g = new ArrayList<RemoteGate>();
        for (Gate gate : gates.values())
            if (! gate.isSameServer()) g.add((RemoteGate)gate);
        return g;
    }

    public static boolean isEmpty() {
        return size() == 0;
    }

    public static int size() {
        return gates.size();
    }

    public static LocalGate findGateForProtection(Location loc) {
        return protectBlocks.getGate(loc);
    }

    public static LocalGate findGateForScreen(Location loc) {
        return screenBlocks.getGate(loc);
    }

    public static LocalGate findGateForSwitch(Location loc) {
        return switchBlocks.getGate(loc);
    }

    public static LocalGate findGateForTrigger(Location loc) {
        return triggerBlocks.getGate(loc);
    }

    public static LocalGate findGateForPortal(Location loc) {
        return portalBlocks.getGate(loc);
    }

    public static void addPortalBlocks(GateMap portalBlocks) {
        portalBlocks.putAll(portalBlocks);
    }

    public static void removePortalBlocks(LocalGate gate) {
        portalBlocks.removeGate(gate);
    }

    public static void addProtectBlocks(GateMap protectBlocks) {
        protectBlocks.putAll(protectBlocks);
    }

    public static void removeProtectBlocks(LocalGate gate) {
        protectBlocks.removeGate(gate);
    }

}
