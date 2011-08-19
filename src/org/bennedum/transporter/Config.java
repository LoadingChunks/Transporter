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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bennedum.transporter.net.Network;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Config {
    
    private static final int CONFIG_VERSION = 1;
    
    private static final Set<String> OPTIONS = new HashSet<String>();
    private static final Options options;
    private static Configuration config = null;
    
    static {
        OPTIONS.add("debug");
        OPTIONS.add("allowBuild");
        OPTIONS.add("allowLinkLocal");
        OPTIONS.add("allowLinkWorld");
        OPTIONS.add("allowLinkServer");
        OPTIONS.add("autoLoadWorlds");
        
        options = new Options(Config.class, OPTIONS, "trp", new OptionsListener() {
            @Override
            public void onOptionSet(Context ctx, String name, String value) {
                ctx.sendLog("global option '%s' set to '%s'", name, value);
            }
        });
    }
    
    public static File getConfigFile() {
        File dataFolder = Global.plugin.getDataFolder();
        return new File(dataFolder, "config.yml");
    }

    public static void load(Context ctx) {
        Configuration c = new Configuration(getConfigFile());
        c.load();
        config = c;
        
        int version = config.getInt("configVersion", 0);
        if (version < CONFIG_VERSION)
            ctx.warn("configuration file version is out of date, please convert manually");
        if (version > CONFIG_VERSION)
            ctx.warn("configuration file version is too new!?!");
            
        ctx.sendLog("loaded configuration");
        Worlds.onConfigLoad(ctx);
        Servers.onConfigLoad(ctx);
        Network.onConfigLoad(ctx);
    }

    public static void save(Context ctx) {
        Network.onConfigSave(ctx);
        Worlds.onConfigSave(ctx);
        Servers.onConfigSave();
        File configDir = Global.plugin.getDataFolder();
        if (! configDir.exists()) configDir.mkdirs();
        config.save();
        ctx.sendLog("saved configuration");
    }

    public static String getString(String path) {
        return config.getString(path, null);
    }
    
    public static String getString(String path, String def) {
        return config.getString(path, def);
    }
    
    public static int getInt(String path, int def) {
        return config.getInt(path, def);
    }
    
    public static boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }
    
    public static List<String> getStringList(String path) {
        return config.getStringList(path, null);
    }
    
    public static List<ConfigurationNode> getNodeList(String path) {
        return config.getNodeList(path, null);
    }
    
    public static void setProperty(String path, Object v) {
        config.setProperty(path, v);
    }
    
    
    
    /* Begin options */
    
    public static boolean getDebug() {
        return config.getBoolean("global.debug", false);
    }
    
    public static void setDebug(boolean b) {
        config.setProperty("global.debug", b);
    }
    
    public static boolean getAllowBuild() {
        return config.getBoolean("global.allowBuild", true);
    }
    
    public static void setAllowBuild(boolean b) {
        config.setProperty("global.allowBuild", b);
    }

    public static boolean getAllowLinkLocal() {
        return config.getBoolean("global.allowLinkLocal", true);
    }
    
    public static void setAllowLinkLocal(boolean b) {
        config.setProperty("global.allowLinkLocal", b);
    }

    public static boolean getAllowLinkWorld() {
        return config.getBoolean("global.allowLinkWorld", true);
    }
    
    public static void setAllowLinkWorld(boolean b) {
        config.setProperty("global.allowLinkWorld", b);
    }

    public static boolean getAllowLinkServer() {
        return config.getBoolean("global.allowLinkServer", true);
    }
    
    public static void setAllowLinkServer(boolean b) {
        config.setProperty("global.allowLinkServer", b);
    }
    
    public static boolean getAutoLoadWorlds() {
        return config.getBoolean("global.autoLoadWorlds", true);
    }
    
    public static void setAutoLoadWorlds(boolean b) {
        config.setProperty("global.autoLoadWorlds", b);
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
    
}
