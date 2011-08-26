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
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bennedum.transporter.net.Network;
import org.bukkit.ChatColor;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Config {

    private static final int CONFIG_VERSION = 2;

    private static final Set<String> OPTIONS = new HashSet<String>();
    private static final Options options;
    private static Configuration config = null;

    static {
        OPTIONS.add("debug");
        OPTIONS.add("debugURL");
        OPTIONS.add("deleteDebugFile");
        OPTIONS.add("allowBuild");
        OPTIONS.add("allowLinkLocal");
        OPTIONS.add("allowLinkWorld");
        OPTIONS.add("allowLinkServer");
        OPTIONS.add("autoLoadWorlds");
        OPTIONS.add("gateLockExpiration");
        OPTIONS.add("arrivalWindow");
        OPTIONS.add("useGatePermissions");
        OPTIONS.add("serverChatFormat");
        OPTIONS.add("serverJoinFormat");
        OPTIONS.add("serverQuitFormat");
        OPTIONS.add("serverKickFormat");
        OPTIONS.add("useIConomy");
        OPTIONS.add("useBOSEconomy");
        OPTIONS.add("exportedGatesFile");
        OPTIONS.add("usePermissions");
        OPTIONS.add("usePermissionsEx");
        OPTIONS.add("httpProxyHost");
        OPTIONS.add("httpProxyType");
        OPTIONS.add("httpProxyPort");
        OPTIONS.add("httpProxyUser");
        OPTIONS.add("httpProxyPassword");

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
        File confFile = getConfigFile();
        config = new Configuration(confFile);
        config.load();

        int version = config.getInt("configVersion", -9999);
        if (version == -9999) {
            // this is an old version of the config
            ctx.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            ctx.warn("");
            ctx.warn("The configuration file is not compatible with this version of the");
            ctx.warn("plugin.");
            File backupFile = new File(confFile.getParent(), "config-backup.yml");
            if (! confFile.renameTo(backupFile)) {
                ctx.warn("I'm unable to rename it and install the default");
                ctx.warn("configuration, so things may not work as expected.");
            } else {
                Utils.copyFileFromJar("/resources/config.yml", Global.plugin.getDataFolder(), true);
                config = new Configuration(confFile);
                config.load();
                ctx.warn("I've renamed it to %s and installed the", backupFile.getName());
                ctx.warn("default configuration file. You'll have to manually convert");
                ctx.warn("your settings.");
            }
            ctx.warn("");
            ctx.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }

        else if (version < CONFIG_VERSION) {
            // do conversion here

        } else if (version > CONFIG_VERSION) {
            ctx.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            ctx.warn("");
            ctx.warn("The configuration file version is for a newer version of the");
            ctx.warn("plugin. Good luck with that.");
            ctx.warn("");
            ctx.warn("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }

        ctx.sendLog("loaded configuration");
        Worlds.onConfigLoad(ctx);
        Servers.onConfigLoad(ctx);
        Network.onConfigLoad(ctx);
        Pins.onConfigLoad(ctx);
    }

    public static void save(Context ctx) {
        Network.onConfigSave();
        Worlds.onConfigSave();
        Servers.onConfigSave();
        Pins.onConfigSave();
        File configDir = Global.plugin.getDataFolder();
        if (! configDir.exists()) configDir.mkdirs();
        config.save();
        ctx.sendLog("saved configuration");
    }

    public static String getStringDirect(String path) {
        return config.getString(path, null);
    }

    public static String getStringDirect(String path, String def) {
        return config.getString(path, def);
    }

    public static int getIntDirect(String path, int def) {
        return config.getInt(path, def);
    }

    public static boolean getBooleanDirect(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public static List<String> getStringList(String path) {
        return config.getStringList(path, null);
    }

    public static List<ConfigurationNode> getNodeList(String path) {
        return config.getNodeList(path, null);
    }

    public static ConfigurationNode getNode(String path) {
        return config.getNode(path);
    }

    public static void setPropertyDirect(String path, Object v) {
        if (v == null)
            config.removeProperty(path);
        else
            config.setProperty(path, v);
    }



    /* Begin options */

    public static boolean getDebug() {
        return config.getBoolean("global.debug", false);
    }

    public static void setDebug(boolean b) {
        config.setProperty("global.debug", b);
    }

    public static String getDebugURL() {
        return config.getString("global.debugURL", "http://www.bennedum.org/transporter-debug.php");
    }

    public static void setDebugURL(String s) {
        try {
            URL u = new URL(s);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("debugURL is invalid");
        }
        config.setProperty("global.debugURL", s);
    }

    public static boolean getDeleteDebugFile() {
        return config.getBoolean("global.deleteDebugFile", true);
    }

    public static void setDeleteDebugFile(boolean b) {
        config.setProperty("global.deleteDebugFile", b);
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

    public static int getGateLockExpiration() {
        return config.getInt("global.gateLockExpiration", 2000);
    }

    public static void setGateLockExpiration(int i) {
        if (i < 500)
            throw new IllegalArgumentException("gateLockExpiration must be at least 500");
        config.setProperty("global.gateLockExpiration", i);
    }

    public static int getArrivalWindow() {
        return config.getInt("global.arrivalWindow", 20000);
    }

    public static void setArrivalWindow(int i) {
        if (i < 1000)
            throw new IllegalArgumentException("arrivalWindow must be at least 1000");
        config.setProperty("global.arrivalWindow", i);
    }

    public static boolean getUseGatePermissions() {
        return config.getBoolean("global.useGatePermissions", false);
    }

    public static void setUseGatePermissions(boolean b) {
        config.setProperty("global.useGatePermissions", b);
    }

    public static String getServerChatFormat() {
        return config.getString("global.serverChatFormat", "<%player%@%world%@%server%> %message%");
    }

    public static void setServerChatFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        setPropertyDirect("global.serverChatFormat", s);
    }

    public static String getServerJoinFormat() {
        return config.getString("global.serverJoinFormat", ChatColor.YELLOW + "%player%@%world%@%server% joined the game.");
    }

    public static void setServerJoinFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        setPropertyDirect("global.serverJoinFormat", s);
    }

    public static String getServerQuitFormat() {
        return config.getString("global.serverQuitFormat", ChatColor.YELLOW + "%player%@%world%@%server% left the game.");
    }

    public static void setServerQuitFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        setPropertyDirect("global.serverQuitFormat", s);
    }

    public static String getServerKickFormat() {
        return config.getString("global.serverKickFormat", ChatColor.YELLOW + "%player%@%world%@%server% was kicked.");
    }

    public static void setServerKickFormat(String s) {
        if (s != null) {
            if (s.equals("-")) s = "";
            else if (s.equals("*")) s = null;
        }
        setPropertyDirect("global.serverKickFormat", s);
    }

    public static boolean getUseIConomy() {
        return config.getBoolean("global.useIConomy", false);
    }

    public static void setUseIConomy(boolean b) {
        config.setProperty("global.useIConomy", b);
    }

    public static boolean getUseBOSEconomy() {
        return config.getBoolean("global.useBOSEconomy", false);
    }

    public static void setUseBOSEconomy(boolean b) {
        config.setProperty("global.useBOSEconomy", b);
    }

    public static String getExportedGatesFile() {
        return config.getString("global.exportedGatesFile", null);
    }

    public static void setExportedGatesFile(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        setPropertyDirect("global.exportedGatesFile", s);
    }

    public static boolean getUsePermissions() {
        return config.getBoolean("global.usePermissions", false);
    }

    public static void setUsePermissions(boolean b) {
        config.setProperty("global.usePermissions", b);
    }

    public static boolean getUsePermissionsEx() {
        return config.getBoolean("global.usePermissionsEx", false);
    }

    public static void setUsePermissionsEx(boolean b) {
        config.setProperty("global.usePermissionsEx", b);
    }

    public static String getHttpProxyHost() {
        return config.getString("global.httpProxy.host", null);
    }

    public static void setHttpProxyHost(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        setPropertyDirect("global.httpProxy.host", s);
    }

    public static String getHttpProxyType() {
        return config.getString("global.httpProxy.type", "HTTP");
    }

    public static void setHttpProxyType(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        if (s != null) {
            try {
                Utils.valueOf(Proxy.Type.class, s);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("proxy type is ambiguous or invalid");
            }
        }
        setPropertyDirect("global.httpProxy.type", s);
    }

    public static int getHttpProxyPort() {
        return config.getInt("global.httpProxy.port", 80);
    }

    public static void setHttpProxyPort(int i) {
        if ((i < 1) || (i > 65535))
            throw new IllegalArgumentException("proxy port is invalid");
        config.setProperty("global.httpProxy.port", i);
    }

    public static String getHttpProxyUser() {
        return config.getString("global.httpProxy.user", null);
    }

    public static void setHttpProxyUser(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        setPropertyDirect("global.httpProxy.user", s);
    }

    public static String getHttpProxyPassword() {
        return config.getString("global.httpProxy.password", null);
    }

    public static void setHttpProxyPassword(String s) {
        if ((s != null) && (s.equals("-") || s.equals("*"))) s = null;
        setPropertyDirect("global.httpProxy.password", s);
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
