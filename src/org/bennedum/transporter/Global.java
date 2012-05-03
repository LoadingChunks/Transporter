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
import java.util.List;
import org.bennedum.transporter.command.APICommand;
import org.bennedum.transporter.command.CommandProcessor;
import org.bennedum.transporter.command.DebugCommand;
import org.bennedum.transporter.command.DesignCommand;
import org.bennedum.transporter.command.GateCommand;
import org.bennedum.transporter.command.GlobalCommands;
import org.bennedum.transporter.command.HelpCommand;
import org.bennedum.transporter.command.NetworkCommand;
import org.bennedum.transporter.command.PinCommand;
import org.bennedum.transporter.command.ReloadCommand;
import org.bennedum.transporter.command.SaveCommand;
import org.bennedum.transporter.command.ServerCommand;
import org.bennedum.transporter.command.WorldCommand;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Global {

    public static final int DEFAULT_PLUGIN_PORT = 25555;
    public static final int DEFAULT_MC_PORT = 25565;

    public static Thread mainThread = null;
    public static boolean enabled = false;
    public static Transporter plugin = null;
    public static String pluginName;
    public static String pluginVersion;
    public static boolean started = false;

    public static final List<CommandProcessor> commands = new ArrayList<CommandProcessor>();

    static {
        commands.add(new HelpCommand());
        commands.add(new ReloadCommand());
        commands.add(new SaveCommand());
        commands.add(new PinCommand());
        commands.add(new GlobalCommands());
        commands.add(new DesignCommand());
        commands.add(new GateCommand());
        commands.add(new ServerCommand());
        commands.add(new NetworkCommand());
        commands.add(new WorldCommand());
        commands.add(new APICommand());

        commands.add(new DebugCommand());
    }

}
