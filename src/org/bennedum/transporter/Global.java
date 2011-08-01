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
import org.bennedum.transporter.command.CommandProcessor;
import org.bennedum.transporter.command.DebugCommand;
import org.bennedum.transporter.command.DesignCommand;
import org.bennedum.transporter.command.GateCommand;
import org.bennedum.transporter.command.GoCommand;
import org.bennedum.transporter.command.HelpCommand;
import org.bennedum.transporter.command.PinCommand;
import org.bennedum.transporter.command.ReloadCommand;
import org.bennedum.transporter.command.SaveCommand;
import org.bennedum.transporter.command.ServerCommand;
import org.bennedum.transporter.command.WorldCommand;
import org.bennedum.transporter.net.Network;
import org.bukkit.entity.Player;
import org.bukkit.util.config.Configuration;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Global {

    public static Thread mainThread = null;
    public static boolean enabled = false;
    public static Transporter plugin = null;
    public static String pluginName;
    public static String pluginVersion;
    public static Configuration config;
    public static DesignCollection designs = new DesignCollection();
    public static GateCollection gates = new GateCollection();
    public static ServerCollection servers = new ServerCollection();
    public static Network network = new Network();

    public static final List<CommandProcessor> commands = new ArrayList<CommandProcessor>();

    private static Map<Integer,LocalGate> selectedGates = new HashMap<Integer,LocalGate>();
    private static Map<String,List<SavedBlock>> buildUndos = new HashMap<String,List<SavedBlock>>();

    static {
        commands.add(new HelpCommand());
        commands.add(new ReloadCommand());
        commands.add(new SaveCommand());
        commands.add(new PinCommand());
        commands.add(new GoCommand());
        commands.add(new DesignCommand());
        commands.add(new GateCommand());
        commands.add(new ServerCommand());
        commands.add(new WorldCommand());

        commands.add(new DebugCommand());
    }

    public static void setSelectedGate(Player player, LocalGate gate) {
        selectedGates.put((player == null) ? Integer.MAX_VALUE : player.getEntityId(), gate);
    }

    public static LocalGate getSelectedGate(Player player) {
        return selectedGates.get((player == null) ? Integer.MAX_VALUE : player.getEntityId());
    }

    public static void deselectGate(LocalGate gate) {
        for (Integer playerId : new ArrayList<Integer>(selectedGates.keySet()))
            if (selectedGates.get(playerId) == gate)
                selectedGates.remove(playerId);
    }

    public static void setBuildUndo(Player player, List<SavedBlock> savedBlocks) {
        buildUndos.put(player.getName(), savedBlocks);
    }

    public static List<SavedBlock> removeBuildUndo(Player player) {
        return buildUndos.remove(player.getName());
    }

    public static List<SavedBlock> getBuildUndo(Player player) {
        return buildUndos.get(player.getName());
    }

}
