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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bennedum.transporter.net.Connection;
import org.bennedum.transporter.net.NetworkException;
import org.bennedum.transporter.net.Network;
import org.bennedum.transporter.net.Message;
import org.bennedum.transporter.net.Result;
import org.bukkit.entity.Player;
import org.bukkit.util.config.ConfigurationNode;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Server {

    public static final int DEFAULT_MC_PORT = 25565;
    private static final int RECONNECT_INTERVAL = 60000;
    private static final int RECONNECT_SKEW = 10000;

    private static final int SEND_KEEPALIVE_INTERVAL = 60000;
    private static final int RECV_KEEPALIVE_INTERVAL = 90000;

    public static final List<String> OPTIONS = new ArrayList<String>();

    static {
        OPTIONS.add("publicAddress");
        OPTIONS.add("privateAddress");
        OPTIONS.add("sendAllChat");
        OPTIONS.add("receiveAllChat");
    }
    
    public static boolean isValidName(String name) {
        if ((name.length() == 0) || (name.length() > 15)) return false;
        return ! (name.contains(".") || name.contains("*"));
    }

    public static Map<String,Set<Pattern>> makeAddressMap(String addrStr, int defaultPort) throws ServerException {
        Map<String,Set<Pattern>> map = new LinkedHashMap<String,Set<Pattern>>();
        String addresses[] = addrStr.split("\\s+");
        for (String address : addresses) {
            Set<Pattern> patterns = new HashSet<Pattern>();
            String parts[] = address.split(",");
            if (parts.length == 1)
                patterns.add(Pattern.compile(".*"));
            else
                for (int i = 1; i < parts.length; i++) {
                    try {
                        patterns.add(Pattern.compile(parts[i]));
                    } catch (PatternSyntaxException e) {
                        throw new ServerException("invalid pattern '%s'", parts[i]);
                    }
                }
            String addrParts[] = parts[0].split("/");
            if (addrParts.length > 2)
                throw new ServerException("address '%s' has too many parts", parts[0]);
            for (int i = 0; i < addrParts.length; i++)
                try {
                    Network.makeAddress(addrParts[i], defaultPort);
                } catch (NetworkException e) {
                    throw new ServerException("address '%s': %s", addrParts[i], e.getMessage());
                }
            map.put(parts[0], patterns);
        }
        return map;
    }

    private static String makePrivateAddress(String in) throws ServerException {
        if ((in == null) || in.equals("*")) {
            in = null;
            if (Global.network.getListenAddress().getAddress().isAnyLocalAddress()) {
                // take the first IP address on the first interface
                try {
                    for (Enumeration<NetworkInterface> e1 = NetworkInterface.getNetworkInterfaces(); e1.hasMoreElements(); ) {
                        NetworkInterface iface = e1.nextElement();
                        if (! iface.isUp()) continue;
                        if (iface.isLoopback()) continue;
                        InetAddress addr = getInterfaceAddress(iface);
                        if (addr != null) {
                            in = addr.getHostAddress();
                            break;
                        }
                    }
                } catch (SocketException e) {
                    Utils.severe(e, "unable to list network addresses:");
                    throw new ServerException("unable to obtain private address");
                }
            }
            if (in == null) in = "-";

        } else if ((! in.equals("-")) &&
                   (! in.matches("^[\\d\\.]+$"))) {
            try {
                int pos = in.indexOf(':');
                String port = null;
                if (pos != -1) {
                    port = in.substring(pos);
                    in = in.substring(0, pos);
                }
                NetworkInterface iface = NetworkInterface.getByName(in);
                InetAddress addr = getInterfaceAddress(iface);
                if (addr != null)
                    in = addr.getHostAddress();
                else
                    throw new ServerException("unable to get address of interface '%s'", in);
                if (port != null)
                    in += port;
            } catch (SocketException e) {
                throw new ServerException("unable to find interface '%s'", in);
            }
        }
        if ((! in.equals("-")) &&
            (! in.contains(":")))
            in += ":" + Global.plugin.getServer().getPort();
        return in;
    }
    
    private static InetAddress getInterfaceAddress(NetworkInterface iface) {
        for (Enumeration<InetAddress> e = iface.getInetAddresses(); e.hasMoreElements(); ) {
            InetAddress addr = e.nextElement();
            if (addr instanceof Inet4Address) return addr;
        }
        return null;
    }
    
    private String name;
    private String pluginAddress;
    private String key;
    private boolean enabled;
    private Connection connection = null;
    private boolean allowReconnect = true;
    private String version = null;
    private String remotePrivateAddress = null;
    private int reconnectTask = -1;
    private boolean connected = false;

    private String publicAddress = null;
    private String privateAddress = null;
    private boolean sendAllChat = false;
    private boolean receiveAllChat = false;
    
    public Server(String name, String plgAddr, String key) throws ServerException {
        this.name = name;
        pluginAddress = plgAddr;
        this.key = key;
        enabled = true;
        validate();
    }

    public Server(ConfigurationNode node) throws ServerException {
        name = node.getString("name");
        pluginAddress = node.getString("pluginAddress");
        key = node.getString("key");
        enabled = node.getBoolean("enabled", true);
        
        // v6.10 to v6.11
        publicAddress = node.getString("minecraftAddress");
        if (publicAddress != null) {
            node.removeProperty("minecraftAddress");
            node.setProperty("publicAddress", publicAddress);
        }
        
        publicAddress = node.getString("publicAddress");
        privateAddress = node.getString("privateAddress");
        sendAllChat = node.getBoolean("sendAllChat", false);
        receiveAllChat = node.getBoolean("receiveAllChat", false);
        
        validate();
    }

    private void validate() throws ServerException {
        if (name == null)
            throw new ServerException("name is required");
        if (! isValidName(name))
            throw new ServerException("name is not valid");
        if (pluginAddress == null)
            throw new ServerException("pluginAddress is required");
        try {
            Network.makeAddress(pluginAddress);
        } catch (NetworkException ce) {
            throw new ServerException("pluginAddress: %s", ce.getMessage());
        }
        if ((key == null) || key.isEmpty())
            throw new ServerException("key is required");
        
        if (publicAddress != null)
            try {
                makeAddressMap(publicAddress, DEFAULT_MC_PORT);
            } catch (ServerException e) {
                throw new ServerException("publicAddress: %s", e.getMessage());
            }
        try {
            privateAddress = makePrivateAddress(privateAddress);
        } catch (ServerException e) {
            throw new ServerException("privateAddress: %s", e.getMessage());
        }
    }

    public void change(String plgAddr, String key) throws ServerException {
        String oldPluginAddress = this.pluginAddress;
        String oldKey = this.key;
        pluginAddress = plgAddr;
        this.key = key;
        try {
            validate();
        } catch (ServerException se) {
            pluginAddress = oldPluginAddress;
            key = oldKey;
            throw se;
        }
    }

    public String getName() {
        return name;
    }

    public String getKey() {
        return key;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean en) {
        enabled = en;
        if (enabled)
            connect();
        else
            disconnect(false);
    }

    public String getPluginAddress() {
        return pluginAddress;
    }

    public String getPublicAddress() {
        return publicAddress;
    }

    public void setPublicAddress(String address) throws ServerException {
        String saved = publicAddress;
        publicAddress = address;
        try {
            validate();
        } catch (ServerException e) {
            publicAddress = saved;
            throw e;
        }
    }

    public String getPrivateAddress() {
        return publicAddress;
    }
    
    public void setPrivateAddress(String address) throws ServerException {
        String saved = privateAddress;
        privateAddress = address;
        try {
            validate();
        } catch (ServerException e) {
            privateAddress = saved;
            throw e;
        }
    }
    
    public String resolveOption(String option) throws ServerException {
        for (String opt : OPTIONS) {
            if (opt.toLowerCase().startsWith(option.toLowerCase()))
                return opt;
        }
        throw new ServerException("unknown option");
    }

    public void setOption(String option, String value) throws ServerException {
        if (! OPTIONS.contains(option))
            throw new ServerException("unknown option");
        String methodName = "set" +
                option.substring(0, 1).toUpperCase() +
                option.substring(1);
        try {
            Field f = getClass().getDeclaredField(option);
            Class c = f.getType();
            Method m = getClass().getMethod(methodName, c);
            if (c == Boolean.TYPE)
                m.invoke(this, Boolean.parseBoolean(value));
            else if (c == Integer.TYPE)
                m.invoke(this, Integer.parseInt(value));
            else if (c == Float.TYPE)
                m.invoke(this, Float.parseFloat(value));
            else if (c == Double.TYPE)
                m.invoke(this, Double.parseDouble(value));
            else if (c == String.class)
                m.invoke(this, value);
            else
                throw new ServerException("unsupported option type");

        } catch (InvocationTargetException ite) {
            throw (ServerException)ite.getCause();
        } catch (NoSuchMethodException nsme) {
            throw new ServerException("invalid method");
        } catch (IllegalArgumentException iae) {
            throw new ServerException("invalid value");
        } catch (NoSuchFieldException nsfe) {
            throw new ServerException("unknown option");
        } catch (IllegalAccessException iae) {
            throw new ServerException("unable to set the option");
        }
    }

    public String getOption(String option) throws ServerException {
        if (! OPTIONS.contains(option))
            throw new ServerException("unknown option");
        String methodName = "get" +
                option.substring(0, 1).toUpperCase() +
                option.substring(1);
        try {
            Field f = getClass().getDeclaredField(option);
            Class c = f.getType();
            Method m = getClass().getMethod(methodName, c);
            Object value = m.invoke(this);
            if (value == null) return "(null)";
            return value.toString();
        } catch (InvocationTargetException ite) {
            throw (ServerException)ite.getCause();
        } catch (NoSuchMethodException nsme) {
            throw new ServerException("invalid method");
        } catch (NoSuchFieldException nsfe) {
            throw new ServerException("unknown option");
        } catch (IllegalAccessException iae) {
            throw new ServerException("unable to read the option");
        }
    }
    
    public String getReconnectAddressForClient(InetSocketAddress clientAddress) {
        String clientAddrStr = clientAddress.getAddress().getHostAddress();
        
        if (remotePrivateAddress != null) {
            InetSocketAddress remoteAddr = (InetSocketAddress)connection.getChannel().socket().getRemoteSocketAddress();
            if (remoteAddr != null) {
                if (remoteAddr.getAddress().getHostAddress().equals(clientAddrStr)) {
                    Utils.debug("redirect for client %s using private address %s", clientAddrStr, remotePrivateAddress);
                    return remotePrivateAddress;
                }
            }
        }
        
        Map<String,Set<Pattern>> pubAddrs;
        try {
            if (publicAddress == null) {
                String[] parts = pluginAddress.split(":");
                pubAddrs = makeAddressMap(parts[0], DEFAULT_MC_PORT);
            } else
                pubAddrs = makeAddressMap(publicAddress, DEFAULT_MC_PORT);
        } catch (ServerException e) {
            return null;
        }
        for (String addr : pubAddrs.keySet()) {
            Set<Pattern> patterns = pubAddrs.get(addr);
            for (Pattern pattern : patterns)
                if (pattern.matcher(clientAddrStr).matches())
                    return addr;
        }
        return null;
    }

    public void setConnection(Connection conn) {
        connection = conn;
    }

    public Connection getConnection() {
        return connection;
    }

    public String getVersion() {
        return version;
    }

    public Map<String,Object> encode() {
        Map<String,Object> node = new HashMap<String,Object>();
        node.put("name", name);
        node.put("pluginAddress", pluginAddress);
        node.put("key", key);
        node.put("enabled", enabled);
        node.put("publicAddress", publicAddress);
        node.put("privateAddress", privateAddress);
        node.put("sendAllChat", sendAllChat);
        node.put("receiveAllChat", receiveAllChat);
        return node;
    }

    public boolean isConnected() {
        if (connection == null) return false;
        return connection.isOpen();
    }

    public boolean isIncoming() {
        return (connection != null) && connection.isIncoming();
    }

    public void connect() {
        if (isConnected() || Global.network.isStopped() || isIncoming()) return;
        allowReconnect = true;
        cancelOutbound();
        if (connection != null)
            connection.close();
        connected = false;
        connection = new Connection(this, pluginAddress);
        connection.open();
    }

    public void disconnect(boolean allowReconnect) {
        this.allowReconnect = allowReconnect;
        cancelOutbound();
        if (connection == null) return;
        connection.close();
    }

    public boolean isConnecting() {
        return (reconnectTask != -1);
    }


    private void cancelOutbound() {
        if (reconnectTask != -1) {
            Utils.info("cancelling outbound connection attempt to server '%s'", getName());
            Utils.cancelTask(reconnectTask);
            reconnectTask = -1;
        }
    }

    private void reconnect() {
        cancelOutbound();
        if (! allowReconnect) return;
        if (isConnected() || Global.network.isStopped() || isIncoming()) return;
        int time = Global.config.getInt("reconnectInterval", RECONNECT_INTERVAL);
        int skew = Global.config.getInt("reconnectSkew", RECONNECT_SKEW);
        if (skew < 0) skew = RECONNECT_SKEW;
        if (time < skew) time = skew;
        time += (Math.random() * (double)(skew * 2)) - skew;
        Utils.info("will attempt to reconnect to '%s' in about %d seconds", getName(), (time / 1000));
        reconnectTask = Utils.fireDelayed(new Runnable() {
            @Override
            public void run() {
                reconnectTask = -1;
                connect();
            }
        }, time);
    }

    public void refresh() {
        if (! isConnected())
            connect();
        else {
            Message message = createMessage("refresh");
            sendMessage(message);
        }
    }

    public void sendKeepAlive() {
        if (! isConnected()) return;
        if ((System.currentTimeMillis() - connection.getLastMessageSentTime()) < SEND_KEEPALIVE_INTERVAL) return;
        Utils.debug("sending keepalive to '%s'", name);
        Message message = createMessage("nop");
        sendMessage(message);
    }

    public void checkKeepAlive() {
        if (! isConnected()) return;
        if ((System.currentTimeMillis() - connection.getLastMessageReceivedTime()) < RECV_KEEPALIVE_INTERVAL) return;
        Utils.warning("no keepalive received from server '%s'", name);
        disconnect(true);
    }



    public void doPing(final Context ctx, final long timeout) {
        if (! isConnected()) return;
        final Message message = createMessage("ping");
        message.put("time", System.currentTimeMillis());

        Utils.worker(new Runnable() {
            @Override
            public void run() {
                final Result result = connection.sendRequest(message, true);
                try {
                    result.get(timeout);
                } catch (CancellationException ce) {
                } catch (InterruptedException ie) {
                } catch (TimeoutException e) {}

                Utils.fire(new Runnable() {
                    @Override
                    public void run() {
                        if (result.isCancelled())
                            ctx.send("server '%s' went offline during ping", name);
                        else if (result.isTimeout())
                            ctx.send("ping to '%s' timed out after %d millis", name, timeout);
                        else if (result.isWaiting())
                            ctx.send("ping to '%s' was interrupted", name);
                        else {
                            Message m = result.getResult();
                            long diff = System.currentTimeMillis() - m.getLong("time");
                            ctx.send("ping to '%s' took %d millis", name, diff);
                        }
                    }
                });
            }
        });
    }

    public void doGateAdded(LocalGate gate) {
        if (! isConnected()) return;
        Message message = createMessage("addGate");
        message.put("name", gate.getName());
        message.put("worldName", gate.getWorldName());
        message.put("designName", gate.getDesignName());
        sendMessage(message);
    }

    public void doGateRenamed(String oldFullName, String newName) {
        if (! isConnected()) return;
        Message message = createMessage("renameGate");
        message.put("oldName", oldFullName);
        message.put("newName", newName);
        sendMessage(message);
    }

    public void doGateRemoved(LocalGate gate) {
        if (! isConnected()) return;
        Message message = createMessage("removeGate");
        message.put("name", gate.getFullName());
        sendMessage(message);
    }

    public void doGateDestroyed(LocalGate gate) {
        if (! isConnected()) return;
        Message message = createMessage("destroyGate");
        message.put("name", gate.getFullName());
        sendMessage(message);
    }

    public void doGateAttach(RemoteGate toGate, LocalGate fromGate) {
        if (! isConnected()) return;
        Message message = createMessage("attachGate");
        message.put("to", toGate.getWorldName() + "." + toGate.getName());
        message.put("from", fromGate.getFullName());
        sendMessage(message);
    }

    public void doGateDetach(RemoteGate toGate, LocalGate fromGate) {
        if (! isConnected()) return;
        Message message = createMessage("detachGate");
        message.put("to", toGate.getWorldName() + "." + toGate.getName());
        message.put("from", fromGate.getFullName());
        sendMessage(message);
    }

    public void doSendReservation(Reservation res) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("sendReservation");
        message.put("reservation", res.encode());
        sendMessage(message);
    }
    
    public void doReservationApproved(long id) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationApproved");
        message.put("id", id);
        sendMessage(message);
    }
    
    public void doReservationDenied(long id, String reason) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationDenied");
        message.put("id", id);
        message.put("reason", reason);
        sendMessage(message);
    }
    
    public void doReservationArrived(long id) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationArrived");
        message.put("id", id);
        sendMessage(message);
    }

    public void doReservationTimeout(long id) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);
        Message message = createMessage("reservationTimeout");
        message.put("id", id);
        sendMessage(message);
    }

    public void doRelayChat(Player player, String world, String msg, Set<RemoteGate> toGates) {
        if (! isConnected()) return;
        Message message = createMessage("relayChat");
        message.put("player", player.getName());
        message.put("displayName", player.getDisplayName());
        message.put("world", world);
        message.put("message", msg);
        if (toGates != null) {
            List<String> gates = new ArrayList<String>(toGates.size());
            for (RemoteGate gate : toGates)
                gates.add(Gate.makeLocalName(gate.getGlobalName()));
            message.put("toGates", gates);
        }
        sendMessage(message);
    }
    



    // Connection callbacks, called from main network thread.
    // If the task is going to take a while, use a worker thread.

    // outbound connection
    public void onConnected(String version) {
        allowReconnect = true;
        connected = true;
        this.version = version;
        cancelOutbound();
        Utils.info("connected to '%s' (%s), running v%s", getName(), connection.getName(), version);
        sendMessage(handleRefresh());
    }

    public void onDisconnected() {
        if (connected) {
            Utils.info("disconnected from '%s' (%s)", getName(), connection.getName());
            connected = false;
        }
        connection = null;
        Gates.remove(this);
        reconnect();
    }

    public void onMessage(Message message) {
        String error = message.getString("error");
        if (error != null) {
            Utils.warning("server '%s' complained: %s", getName(), error);
            return;
        }
        String command = message.getString("command");
        if (command == null) {
            Utils.warning("missing command from connection with %s", connection);
            disconnect(true);
            return;
        }
        Message response = null;

        Utils.debug("received command '%s' from %s", command, getName());
        try {
            if (command.equals("nop")) return;
            if (command.equals("ping"))
                response = handlePing(message);
            else if (command.equals("refresh"))
                response = handleRefresh();
            else if (command.equals("refreshData"))
                handleRefreshData(message);
            else if (command.equals("addGate"))
                handleAddGate(message);
            else if (command.equals("renameGate"))
                handleRenameGate(message);
            else if (command.equals("removeGate"))
                handleRemoveGate(message);
            else if (command.equals("destroyGate"))
                handleDestroyGate(message);
            else if (command.equals("attachGate"))
                handleAttachGate(message);
            else if (command.equals("detachGate"))
                handleDetachGate(message);
            else if (command.equals("sendReservation"))
                handleSendReservation(message);
            else if (command.equals("reservationApproved"))
                handleReservationApproved(message);
            else if (command.equals("reservationDenied"))
                handleReservationDenied(message);
            else if (command.equals("reservationArrived"))
                handleReservationArrived(message);
            else if (command.equals("reservationTimeout"))
                handleReservationTimeout(message);
                    
            else if (command.equals("relayChat"))
                handleRelayChat(message);

            else
                throw new ServerException("unknown command");
        } catch (Throwable t) {
            Utils.warning("while processing command '%s' from '%s': %s", command, getName(), t.getMessage());
            response = new Message();
            response.put("success", false);
            response.put("error", t.getMessage());
        }
        if ((response != null) && isConnected()) {
            if (! response.containsKey("success"))
                response.put("success", true);
            if (message.containsKey("requestId")) {
                response.put("responseId", message.getInt("requestId"));
                response.remove("requestId");
            }
            sendMessage(response);
        }
    }

    // Command processing

    private Message handlePing(Message message) {
        return message;
    }

    private Message handleRefresh() {
        if (! isConnected()) return null;
        Message out = createMessage("refreshData");

        // NAT stuff
        if (Global.config.getBoolean("detectNAT", true)) {
            if (! "-".equals(privateAddress))
                out.put("privateAddress", privateAddress);
        }
        
        // gate list
        List<Message> gates = new ArrayList<Message>();
        for (LocalGate gate : Gates.getLocalGates()) {
            Message m = new Message();
            m.put("name", gate.getName());
            m.put("worldName", gate.getWorldName());
            m.put("designName", gate.getDesignName());
            gates.add(m);
        }
        out.put("gates", gates);
        
        
        return out;
    }

    private void handleRefreshData(Message message) throws ServerException {
        // NAT stuff
        remotePrivateAddress = message.getString("privateAddress");
        Utils.debug("received privateAddress '%s' from '%s'", privateAddress, name);
        
        // gate list
        Collection<Message> gates = message.getMessageList("gates");
        if (gates != null) {
            Gates.remove(this);
            for (Message m : gates) {
                try {
                    Gates.add(new RemoteGate(this, m));
                } catch (GateException ge) {
                    Utils.warning("received bad gate from '%s'", getName());
                }
            }
            Utils.debug("received %d gates from '%s'", gates.size(), name);
        }
    }

    private void handleAddGate(Message message) throws GateException {
        RemoteGate gate = new RemoteGate(this, message);
        Gates.add(gate);
    }

    private void handleRenameGate(Message message) throws ServerException, GateException {
        String oldName = message.getString("oldName");
        if (oldName == null)
            throw new ServerException("missing oldName");
        oldName = Gate.makeFullName(this, oldName);
        String newName = message.getString("newName");
        if (newName == null)
            throw new ServerException("missing newName");
        Gates.rename(oldName, newName);
    }

    private void handleRemoveGate(Message message) throws ServerException {
        String gateName = message.getString("name");
        if (gateName == null)
            throw new ServerException("missing name");
        gateName = Gate.makeFullName(this, gateName);
        Gate gate = Gates.get(gateName);
        if (gate == null)
            throw new ServerException("unknown gate '%s'", gateName);
        if (gate.isSameServer())
            throw new ServerException("gate '%s' is not remote", gateName);
        Gates.remove((RemoteGate)gate);
    }

    private void handleDestroyGate(Message message) throws ServerException {
        String gateName = message.getString("name");
        if (gateName == null)
            throw new ServerException("missing name");
        gateName = Gate.makeFullName(this, gateName);
        Gate gate = Gates.get(gateName);
        if (gate == null)
            throw new ServerException("unknown gate '%s'", gateName);
        if (gate.isSameServer())
            throw new ServerException("gate '%s' is not remote", gateName);
        Gates.destroy((RemoteGate)gate);
    }

    private void handleAttachGate(Message message) throws ServerException {
        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing toName");
        toName = Gate.makeLocalName(toName);

        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing fromName");
        fromName = Gate.makeFullName(this, fromName);

        Gate toGate = Gates.get(toName);
        if (toGate == null)
            throw new ServerException("unknown to gate '%s'", toName);
        if (! toGate.isSameServer())
            throw new ServerException("to gate '%s' is local", toName);
        Gate fromGate = Gates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown from gate '%s'", fromName);
        if (fromGate.isSameServer())
            throw new ServerException("from gate '%s' is not remote", fromName);
        toGate.attach(fromGate);
    }

    private void handleDetachGate(Message message) throws ServerException {
        String toName = message.getString("to");
        if (toName == null)
            throw new ServerException("missing toName");
        toName = Gate.makeLocalName(toName);

        String fromName = message.getString("from");
        if (fromName == null)
            throw new ServerException("missing fromName");
        fromName = Gate.makeFullName(this, fromName);

        Gate toGate = Gates.get(toName);
        if (toGate == null)
            throw new ServerException("unknown to gate '%s'", toName);
        if (! toGate.isSameServer())
            throw new ServerException("to gate '%s' is local", toName);
        Gate fromGate = Gates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown from gate '%s'", fromName);
        if (fromGate.isSameServer())
            throw new ServerException("from gate '%s' is not remote", fromName);
        toGate.detach(fromGate);
    }

    private void handleSendReservation(Message message) throws ServerException {
        Message resMsg = message.getMessage("reservation");
        if (resMsg == null)
            throw new ServerException("missing reservation");
        Reservation res;
        try {
            res = new Reservation(resMsg, this);
            res.receive();
        } catch (ReservationException e) {
            throw new ServerException("invalid reservation: %s", e.getMessage());
        }
    }
    
    private void handleReservationApproved(Message message) throws ServerException {
        long id = message.getLong("id");
        Reservation res = Reservation.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        res.approved();
    }
    
    private void handleReservationDenied(Message message) throws ServerException {
        long id = message.getLong("id");
        Reservation res = Reservation.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        String reason = message.getString("reason");
        if (reason == null)
            throw new ServerException("missing reason");
        res.denied(reason);
    }
    
    private void handleReservationArrived(Message message) throws ServerException {
        long id = message.getLong("id");
        Reservation res = Reservation.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        res.arrived();
    }

    private void handleReservationTimeout(Message message) throws ServerException {
        long id = message.getLong("id");
        Reservation res = Reservation.get(id);
        if (res == null)
            throw new ServerException("unknown reservation id %s", id);
        res.timeout();
    }
            
    private void handleRelayChat(Message message) throws ServerException {
        String player = message.getString("player");
        if (player == null)
            throw new ServerException("missing player");

        String displayName = message.getString("displayName");
        if (displayName == null)
            displayName = player;

        String world = message.getString("world");
        if (world == null)
            throw new ServerException("missing world");

        String msg = message.getString("message");
        if (msg == null)
            throw new ServerException("missing message");

        List<String> toGates = message.getStringList("toGates");
        if (toGates != null)
            for (int i = 0; i < toGates.size(); i++)
                toGates.set(i, Gate.makeLocalName(toGates.get(i)));

        Chat.receive(player, displayName, world, name, msg, toGates);
    }

    // Utility methods

    private Message createMessage(String command) {
        Message m = new Message();
        m.put("command", command);
        return m;
    }

    private void sendMessage(Message message) {
        Utils.debug("sending command '%s' to %s", message.getString("command", "<none>"), name);
        connection.sendMessage(message, true);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Server[");
        buf.append(name).append(",");
        buf.append(pluginAddress).append(",");
        buf.append(key);
        buf.append("]");
        return buf.toString();
    }

}
