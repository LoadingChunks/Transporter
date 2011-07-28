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
import com.iConomy.system.Account;
import com.iConomy.system.Holdings;
import com.nijikokun.bukkit.Permissions.Permissions;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitWorker;
import org.bukkit.util.Vector;
import org.bukkit.util.config.Configuration;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class Utils {

    private static final Logger logger = Logger.getLogger("Minecraft");
    private static final File bukkitBaseFolder = new File(".");
    private static final File worldBaseFolder = bukkitBaseFolder;

    private static final String DEBUG_URL = "http://www.bennedum.org/transporter-debug.php";
    private static final String DEBUG_BOUNDARY = "*****";
    private static final int DEBUG_LOG_BYTES = 20 * 1024;

    public static void info(String msg, Object ... args) {
        msg = ChatColor.stripColor(String.format(msg, args));
        logger.log(Level.INFO, String.format("[%s] %s", Global.pluginName, msg));
    }

    public static void warning(String msg, Object ... args) {
        msg = ChatColor.stripColor(String.format(msg, args));
        logger.log(Level.WARNING, String.format("[%s] %s", Global.pluginName, msg));
    }

    public static void severe(Throwable t, String msg, Object ... args) {
        msg = ChatColor.stripColor(String.format(msg, args));
        logger.log(Level.SEVERE, String.format("[%s] %s", Global.pluginName, msg), t);
    }

    public static void debug(String msg, Object ... args) {
        if (! Global.config.getBoolean("debug", false)) return;
        msg = ChatColor.stripColor(String.format(msg, args));
        logger.log(Level.INFO, String.format("[DEBUG] %s", msg));
    }

    public static String blockCoords(Location loc) {
        return String.format("%d,%d,%d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public static String coords(Location loc) {
        return String.format("%4.2f,%4.2f,%4.2f", (float)loc.getX(), (float)loc.getY(), (float)loc.getZ());
    }

    public static String dir(Vector vec) {
        return String.format("%4.2f,%4.2f,%4.2f", (float)vec.getX(), (float)vec.getY(), (float)vec.getZ());
    }

    public static boolean copyFileFromJar(String resPath, File dstFile, boolean overwriteIfOlder) {
        if (dstFile.isDirectory()) {
            int pos = resPath.lastIndexOf('/');
            if (pos != -1)
                dstFile = new File(dstFile, resPath.substring(pos + 1));
            else
                dstFile = new File(dstFile, resPath);
        }
        if (dstFile.exists()) {
            if (! overwriteIfOlder) return false;
            try {
                File jarFile = new File(Utils.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
                if (jarFile.lastModified() <= dstFile.lastModified()) return false;
            } catch (URISyntaxException e) {}
        }
        File parentDir = dstFile.getParentFile();
        if (! parentDir.exists())
            parentDir.mkdirs();
        InputStream is = Utils.class.getResourceAsStream(resPath);
        try {
            OutputStream os = new FileOutputStream(dstFile);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) > 0)
                os.write(buffer, 0, len);
            os.close();
            is.close();
        } catch (IOException ioe) {}
        return true;
    }

    public static boolean copyFilesFromJar(String manifestPath, File dstFolder, boolean overwriteIfOlder) {
        boolean created = false;
        if (! dstFolder.exists()) {
            dstFolder.mkdirs();
            created = true;
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(Utils.class.getResourceAsStream(manifestPath)));
        String line;
        try {
            while ((line = r.readLine()) != null) {
                line = line.replaceAll("^\\s+|\\s+$|\\s*#.*", "");
                if (line.length() == 0) continue;
                copyFileFromJar(line, dstFolder, overwriteIfOlder);
            }
        } catch (IOException ioe) {}
        return created;
    }

    public static File[] listYAMLFiles(File folder) {
        return folder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".yml");
            }
        });
    }

    public static World getWorld(String name) {
        World world = null;
        for (World w : Global.plugin.getServer().getWorlds()) {
            if (w.getName().equals(name)) return w;
            if (w.getName().toLowerCase().startsWith(name.toLowerCase())) {
                if (world != null) return null;
                world = w;
            }
        }
        return world;
    }

    public static File worldFolder(String name) {
        return new File(worldBaseFolder, name);
    }

    public static File worldFolder(World world) {
        return new File(worldBaseFolder, world.getName());
    }

    public static File worldPluginFolder(World world) {
        return new File(worldFolder(world), Global.pluginName);
    }

    // TODO: remove
    /*
    public static BlockFace yawToDirection(float yaw, boolean course) {
        while (yaw < 0) yaw += 360;
        if (course) {
            if ((yaw > 315) || (yaw <= 45)) return BlockFace.WEST;
            if ((yaw > 45) && (yaw <= 135)) return BlockFace.NORTH;
            if ((yaw > 135) && (yaw <= 225)) return BlockFace.EAST;
            return BlockFace.SOUTH;
        } else {
            if ((yaw > 337.5) || (yaw <= 22.5)) return BlockFace.WEST;
            if ((yaw > 22.5) || (yaw <= 67.5)) return BlockFace.NORTH_WEST;
            if ((yaw > 67.5) && (yaw <= 112.5)) return BlockFace.NORTH;
            if ((yaw > 112.5) && (yaw <= 157.5)) return BlockFace.NORTH_EAST;
            if ((yaw > 157.5) && (yaw <= 202.5)) return BlockFace.EAST;
            if ((yaw > 202.5) && (yaw <= 247.5)) return BlockFace.SOUTH_EAST;
            if ((yaw > 247.5) && (yaw <= 292.5)) return BlockFace.SOUTH;
            return BlockFace.SOUTH_WEST;
        }
    }
    */

    public static int directionToYaw(BlockFace direction) {
        switch (direction) {
            case NORTH: return 90;
            case EAST: return 180;
            case SOUTH: return -90;
            case WEST: return 0;
            case NORTH_EAST: return 135;
            case NORTH_WEST: return 45;
            case SOUTH_EAST: return -135;
            case SOUTH_WEST: return -45;
            default: return 0;
        }
    }

    public static BlockFace yawToDirection(float yaw) {
        while (yaw > 180) yaw -= 360;
        while (yaw <= -180) yaw += 360;
        if (yaw < -157.5) return BlockFace.EAST;
        if (yaw < -112.5) return BlockFace.SOUTH_EAST;
        if (yaw < -67.5) return BlockFace.SOUTH;
        if (yaw < -22.5) return BlockFace.SOUTH_WEST;
        if (yaw < 22.5) return BlockFace.WEST;
        if (yaw < 67.5) return BlockFace.NORTH_WEST;
        if (yaw < 112.5) return BlockFace.NORTH;
        if (yaw < 157.5) return BlockFace.NORTH_EAST;
        return BlockFace.EAST;
    }

    public static BlockFace rotate(BlockFace from, BlockFace to) {
        int fromYaw = directionToYaw(from);
        int toYaw = directionToYaw(to);
        int result = fromYaw + toYaw - 90;
        return yawToDirection(result);
    }

    public static Vector rotate(Vector velocity, BlockFace from, BlockFace to) {
        double angle = directionToYaw(from) - directionToYaw(to); // degrees
        angle = angle * Math.PI / 180.0; // radians
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = velocity.getX();
        double z = velocity.getZ();
        velocity.setX((cos * x) + (sin * z));
        velocity.setZ((-sin * x) + (cos * z));
        return velocity;
    }

    public static boolean permissionsAvailable() {
        if (! Global.config.getBoolean("usePermissions", false)) return false;
        if (Global.permissionsPlugin != null) return true;
        Plugin p = Global.plugin.getServer().getPluginManager().getPlugin("Permissions");
        if ((p == null) || (! p.isEnabled())) return false;
        Global.permissionsPlugin = ((Permissions)p).getHandler();
        return true;
    }

    public static boolean iconomyAvailable() {
        if (! Global.config.getBoolean("useIConomy", false)) return false;
        if (Global.iconomyPlugin != null) return true;
        Plugin p = Global.plugin.getServer().getPluginManager().getPlugin("iConomy");
        if ((p == null) || (! p.getClass().getName().equals("com.iConomy.iConomy")) || (! p.isEnabled())) return false;
        Global.iconomyPlugin = (iConomy)p;
        return true;
    }

    public static File getConfigFile() {
        File dataFolder = Global.plugin.getDataFolder();
        return new File(dataFolder, "config.yml");
    }

    public static void loadConfig(Context ctx) {
        Configuration config = new Configuration(getConfigFile());
        config.load();
        Global.config = config;
        ctx.sendLog("loaded configuration");
    }

    public static void saveConfig(Context ctx) {
        File configDir = Global.plugin.getDataFolder();
        if (! configDir.exists()) configDir.mkdirs();
        Global.config.save();
        ctx.sendLog("saved configuration");
    }

    public static boolean isMainThread() {
        return Thread.currentThread() == Global.mainThread;
    }

    public static boolean isWorkerThread() {
        if (isMainThread()) return false;
        Thread t = Thread.currentThread();
        for (BukkitWorker worker : Global.plugin.getServer().getScheduler().getActiveWorkers())
            if (worker.getThread() == t) return true;
        return false;
    }

    public static int fire(Runnable run) {
        if (! Global.enabled) return -1;
        return Global.plugin.getServer().getScheduler().scheduleSyncDelayedTask(Global.plugin, run);
    }

    // delay is millis
    public static int fireDelayed(Runnable run, long delay) {
        if (! Global.enabled) return -1;
        long ticks = delay / 50;
        return Global.plugin.getServer().getScheduler().scheduleSyncDelayedTask(Global.plugin, run, ticks);
    }

    public static <T> Future<T> call(Callable<T> task) {
        if (! Global.enabled) return null;
        return Global.plugin.getServer().getScheduler().callSyncMethod(Global.plugin, task);
    }

    public static int worker(Runnable run) {
        if (! Global.enabled) return -1;
        return Global.plugin.getServer().getScheduler().scheduleAsyncDelayedTask(Global.plugin, run);
    }

    // delay is millis
    public static int workerDelayed(Runnable run, long delay) {
        if (! Global.enabled) return -1;
        long ticks = delay / 50;
        return Global.plugin.getServer().getScheduler().scheduleAsyncDelayedTask(Global.plugin, run, ticks);
    }

    public static void cancelTask(int taskId) {
        if (! Global.enabled) return;
        Global.plugin.getServer().getScheduler().cancelTask(taskId);
    }

    public static boolean prepareChunk(Location loc) {
        World world = loc.getWorld();
        Chunk chunk = world.getChunkAt(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        if (world.isChunkLoaded(chunk)) return false;
        world.loadChunk(chunk);
        return true;
    }

    public static boolean deductFunds(String accountName, double amount, boolean checkBalance) throws FundsException {
        if (! iconomyAvailable() || (amount <= 0)) return false;
        Account account = iConomy.getAccount(accountName);
        if (account == null) {
            Utils.warning("iConomy account '%s' not found", accountName);
            return false;
        }
        Holdings hold = account.getHoldings();
        if (hold.balance() < amount)
            throw new FundsException("insufficient funds");
        if (checkBalance) return false;
        hold.subtract(amount);
        return true;
    }

    public static boolean deductInventory(Player player, Map<Material,Integer> blocks, boolean checkBalance) throws InventoryException {
        if ((player == null) || blocks.isEmpty()) return false;
        PlayerInventory inv = player.getInventory();
        for (Material material : blocks.keySet()) {
            int needed = blocks.get(material);
            if (needed <= 0) continue;
            HashMap<Integer,? extends ItemStack> slots = inv.all(material);
            for (int slotNum : slots.keySet()) {
                ItemStack stack = slots.get(slotNum);
                if (stack.getAmount() > needed) {
                    if (! checkBalance)
                        stack.setAmount(stack.getAmount() - needed);
                    needed = 0;
                } else {
                    needed -= stack.getAmount();
                    if (! checkBalance)
                        inv.clear(slotNum);
                }
                blocks.put(material, needed);
                if (needed <= 0) break;
            }
            if (needed > 0)
                throw new InventoryException("need %d more %s", needed, material);
        }
        return ! checkBalance;
    }

    public static HttpURLConnection openURL(URL url) throws IOException {
        Proxy proxy = Proxy.NO_PROXY;
        String proxyHost = Global.config.getString("httpProxy.host");
        if (proxyHost != null) {
            Proxy.Type proxyType = null;
            try {
                proxyType = Proxy.Type.valueOf(Global.config.getString("httpProxy.type", "HTTP"));
            } catch (IllegalArgumentException iae) {}
            int proxyPort = Global.config.getInt("httpProxy.port", 80);
            proxy = new Proxy(proxyType, new InetSocketAddress(proxyHost, proxyPort));
            debug("using proxy %s", proxy);
        }
        HttpURLConnection conn = (HttpURLConnection)url.openConnection(proxy);
        if (proxy != Proxy.NO_PROXY) {
            String proxyUser = Global.config.getString("httpProxy.user");
            String proxyPassword = Global.config.getString("httpProxy.password");
            if ((proxyUser != null) && (proxyPassword != null)) {
                String enc = Base64.encode(proxyUser + ":" + proxyPassword, "UTF-8");
                conn.setRequestProperty("Proxy-Authorization", enc);
                debug("proxy authentication enabled for user %s", proxyUser);
            }
        }
        return conn;
    }

    // can be called from any thread, upload will happen in a worker thread
    public static void submitDebug(final String message) {
        if (! isWorkerThread()) {
            debug("scheduling debug submission");
            worker(new Runnable() {
                @Override
                public void run() {
                    submitDebug(message);
                }
            });
            return;
        }
        debug("starting debug submission");

        File zipFile = null;
        try {
            zipFile = File.createTempFile(Global.pluginName + "-", "-debug.zip");
        } catch (IOException e) {
            severe(e, "unable to create debug zip file");
            return;
        }

        File file;
        FileInputStream in;
        int length;
        byte[] buffer = new byte[1024];

        try {
            ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFile));

            // add the config file
            file = getConfigFile();
            in = new FileInputStream(file);
            zipOut.putNextEntry(new ZipEntry(file.getName()));
            while ((length = in.read(buffer)) > 0)
                zipOut.write(buffer, 0, length);
            in.close();
            zipOut.closeEntry();

            // add the last DEBUG_LOG_BYTES bytes of the server log
            file = new File(bukkitBaseFolder, "server.log");
            in = new FileInputStream(file);
            zipOut.putNextEntry(new ZipEntry(file.getName()));
            if (file.length() > DEBUG_LOG_BYTES) {
                in.skip(file.length() - DEBUG_LOG_BYTES);
                // read until the end of a line
                while (in.read() != 10) {}
            }
            while ((length = in.read(buffer)) > 0)
                zipOut.write(buffer, 0, length);
            in.close();
            zipOut.closeEntry();

            // add other debug info...
            zipOut.putNextEntry(new ZipEntry("debug.txt"));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(zipOut));
            writer.println(message);
            writer.println();

            // the list of servers...
            List<Server> servers = Global.servers.getAll();
            Collections.sort(servers, new Comparator<Server>() {
                @Override
                public int compare(Server a, Server b) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
            });
            writer.format("%d servers:\n", servers.size());
            for (Server server : servers)
                writer.format("  %s: %s '%s' [%s] %s/%s %s %s %s\n",
                            server.getName(),
                            server.getPluginAddress(),
                            server.getKey(),
                            (server.getMCAddress() == null) ? "*" : server.getMCAddress(),
                            (server.isEnabled() ? "up" : "down"),
                            (server.isConnected() ? "up" : "down"),
                            (server.isConnected() ? (server.isIncoming() ? "incoming" : "outgoing") : ""),
                            (server.isConnected() ? server.getConnection().getName() : ""),
                            (server.isConnected() ? "v" + server.getVersion() : "")
                        );
            writer.println();

            // the list of gates...
            List<Gate> gates = Global.gates.getAll();
            Collections.sort(gates, new Comparator<Gate>() {
                @Override
                public int compare(Gate a, Gate b) {
                    return a.getFullName().compareToIgnoreCase(b.getFullName());
                }
            });
            writer.format("%d gates:\n", gates.size());
            for (Gate gate : gates) {
                writer.format("  %s\n", gate.getFullName());
                writer.format("    design: %s\n", gate.getDesignName());
                if (gate.isSameServer()) {
                    LocalGate localGate = (LocalGate)gate;
                    if (localGate.getLinkLocal())
                        writer.format("local cost: %s/%s\n",
                                localGate.getSendLocalCost(),
                                localGate.getReceiveLocalCost());
                    if (localGate.getLinkWorld())
                        writer.format("world cost: %s/%s\n",
                                localGate.getSendWorldCost(),
                                localGate.getReceiveWorldCost());
                    if (localGate.getLinkServer())
                        writer.format("server cost: %s/%s\n",
                                localGate.getSendServerCost(),
                                localGate.getReceiveServerCost());
                    List<String> links = localGate.getLinks();
                    writer.format("    links: %d\n", links.size());
                    for (String link : links)
                        writer.format("     %s%s\n", link.equals(localGate.getDestinationLink()) ? "*": "", link);
                }
            }
            writer.println();


            writer.flush();
            zipOut.closeEntry();

            zipOut.close();
        } catch (IOException e) {
            severe(e, "unable to create debug zip file '%s':", zipFile.getAbsolutePath());
            zipFile.delete();
            return;
        }

        debug("debug data file '%s' created successfully", zipFile.getAbsolutePath());

        try {
            URL url = new URL(Global.config.getString("debugURL", DEBUG_URL));
            debug("submitting to %s", url);
            HttpURLConnection conn = openURL(url);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + DEBUG_BOUNDARY);
            OutputStream out = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));

            writer.write("--" + DEBUG_BOUNDARY + "\r\n");
            writer.write("Content-Disposition: form-data; name=\"message\"\r\n");
            writer.write("\r\n");
            writer.write(URLEncoder.encode(message, "UTF-8"));
            writer.write("\r\n");

            writer.write("--" + DEBUG_BOUNDARY + "\r\n");
            writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"content.zip\"\r\n");
            writer.write("\r\n");
            writer.flush();

            in = new FileInputStream(zipFile);
            while ((length = in.read(buffer)) > 0)
                out.write(buffer, 0, length);
            in.close();
            out.flush();

            writer.write("\r\n");
            writer.write("--" + DEBUG_BOUNDARY + "--\r\n");
            writer.flush();
            writer.close();

            int status = conn.getResponseCode();
            if ((status >= 200) && (status < 300))
                debug("debug data submitted successfully");
            else
                warning("unable to submit debug data: HTTP status " + status);
        } catch (IOException e) {
            severe(e, "unable to submit debug data:");
        } finally {
            zipFile.delete();
        }

    }

}
