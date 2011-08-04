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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
        OPTIONS.add("pluginAddress");
        OPTIONS.add("key");
        OPTIONS.add("publicAddress");
        OPTIONS.add("privateAddress");
        OPTIONS.add("sendAllChat");
        OPTIONS.add("receiveAllChat");
    }
    
    public static boolean isValidName(String name) {
        if ((name.length() == 0) || (name.length() > 15)) return false;
        return ! (name.contains(".") || name.contains("*"));
    }

    
    private String name;
    private String pluginAddress;   // can be IP/DNS name, with opt port
    private String key;
    private boolean enabled;

    // The address we tell players so they can connect to our MC server.
    // This address is given to the plugin on the other end of the connection.
    // The string is a space separated list of values.
    // Each value is a slash (/) delimited list of 1 or 2 items.
    // The first item is the address/port a player should connect to.
    // The second item is a regular expression to match against the player's address.
    // If no second item is provided, it defaults to ".*".
    // The first item can be a "*", which means "use the pluginAddress".
    // The first item can be an interface name, which means use the first address of the named local interface.
    // The default value is "*".
    private String publicAddress = null;
    private String normalizedPublicAddress = null;
    
    // The address of our MC server host.
    // This address is given to the plugin on the other end of the connection if global setting sendPrivateAddress is true (the default).
    // This is an address/port.
    // The value can be "-", which means don't send a private address to the remote side no matter what the sendPrivateAddress setting is.
    // The value can be a "*", which means use the configured MC server address/port. If the wildcard address was configured, use the first address on the first interface.
    // The value can be an interface name, which means use the first address of the named local interface.
    // The default value is "*".
    private String privateAddress = null;
    private InetSocketAddress normalizedPrivateAddress = null;
    
    // Should all chat messages on the local server be sent to the remote server?
    private boolean sendAllChat = false;
    
    // Should all chat messages received from the remote server be echoed to local users?
    private boolean receiveAllChat = false;
    
    private Connection connection = null;
    private boolean allowReconnect = true;
    private int reconnectTask = -1;
    private boolean connected = false;
    private String remoteVersion = null;
    private Map<String,Set<Pattern>> remotePublicAddressMap = null;
    private String remotePublicAddress = null;
    private String remotePrivateAddress = null;
    private String remoteCluster = null;
    
    public Server(String name, String plgAddr, String key) throws ServerException {
        try {
            setName(name);
            setPluginAddress(plgAddr);
            setKey(key);
            enabled = true;
        } catch (IllegalArgumentException e) {
            throw new ServerException(e.getMessage());
        }
    }

    public Server(ConfigurationNode node) throws ServerException {
        // v6.10 to v6.11
        if (node.getString("minecraftAddress") != null)
            node.removeProperty("minecraftAddress");
        
        try {
            setName(node.getString("name"));
            setPluginAddress(node.getString("pluginAddress"));
            setKey(node.getString("key"));
            enabled = node.getBoolean("enabled", true);
            setPublicAddress(node.getString("publicAddress", "*"));
            setPrivateAddress(node.getString("privateAddress", "*"));
            setSendAllChat(node.getBoolean("sendAllChat", false));
            setReceiveAllChat(node.getBoolean("receiveAllChat", false));
        } catch (IllegalArgumentException e) {
            throw new ServerException(e.getMessage());
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) throws ServerException {
        if (name == null)
            throw new ServerException("name is required");
        if (! isValidName(name))
            throw new ServerException("name is not valid");
        this.name = name;
    }
    
    public String getPluginAddress() {
        return pluginAddress;
    }

    public void setPluginAddress(String addr) {
        if (addr == null)
            throw new IllegalArgumentException("pluginAddress is required");
        try {
            Network.makeInetSocketAddress(addr, Network.DEFAULT_PORT, true);
        } catch (NetworkException e) {
            throw new IllegalArgumentException("pluginAddress: " + e.getMessage());
        }
        pluginAddress = addr;
    }
    
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        if ((key == null) || key.isEmpty())
            throw new IllegalArgumentException("key is required");
        this.key = key;
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

    /* Begin options */
    
    public String getPublicAddress() {
        return publicAddress;
    }

    public void setPublicAddress(String address) {
        if (address == null)
            throw new IllegalArgumentException("publicAddress is required");
        try {
            normalizePublicAddress(address);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("publicAddress: " + e.getMessage());
        }
        publicAddress = address;
    }

    public String getNormalizedPublicAddress() {
        return normalizedPublicAddress;
    }
    
    public String getPrivateAddress() {
        return privateAddress;
    }
    
    public void setPrivateAddress(String address) {
        if (address == null)
            throw new IllegalArgumentException("privateAddress is required");
        try {
            normalizePrivateAddress(address);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("privateAddress: " + e.getMessage());
        }
        privateAddress = address;
    }
    
    public InetSocketAddress getNormalizedPrivateAddress() {
        return normalizedPrivateAddress;
    }
    
    public boolean getSendAllChat() {
        return sendAllChat;
    }
    
    public void setSendAllChat(boolean b) {
        sendAllChat = b;
    }
    
    public boolean getReceiveAllChat() {
        return receiveAllChat;
    }
    
    public void setReceiveAllChat(boolean b) {
        receiveAllChat = b;
    }

    public String getRemotePublicAddress() {
        return remotePublicAddress;
    }

    public String getRemotePrivateAddress() {
        return remotePrivateAddress;
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
            Method m = getClass().getMethod(methodName);
            Object value = m.invoke(this);
            if (value == null) return "(null)";
            return value.toString();
        } catch (InvocationTargetException ite) {
            throw (ServerException)ite.getCause();
        } catch (NoSuchMethodException nsme) {
            throw new ServerException("invalid method");
        } catch (IllegalAccessException iae) {
            throw new ServerException("unable to read the option");
        }
    }
    
    /* End options */
    
    public String getReconnectAddressForClient(InetSocketAddress clientAddress) {
        String clientAddrStr = clientAddress.getAddress().getHostAddress();
        
        if (Global.config.getBoolean("usePrivateAddress", true) && (remotePrivateAddress != null)) {
            InetSocketAddress remoteAddr = (InetSocketAddress)connection.getChannel().socket().getRemoteSocketAddress();
            if (remoteAddr != null) {
                if (remoteAddr.getAddress().getHostAddress().equals(clientAddrStr)) {
                    Utils.debug("reconnect for client %s using private address %s", clientAddrStr, remotePrivateAddress);
                    return remotePrivateAddress;
                }
            }
        }
        
        if (remotePublicAddressMap == null) {
            String[] parts = pluginAddress.split(":");
            return parts[0] + ":" + DEFAULT_MC_PORT;
        }
        
        for (String address : remotePublicAddressMap.keySet()) {
            Set<Pattern> patterns = remotePublicAddressMap.get(address);
            for (Pattern pattern : patterns)
                if (pattern.matcher(clientAddrStr).matches())
                    return address;
        }
        return null;
    }
    
    public void setConnection(Connection conn) {
        connection = conn;
    }

    public Connection getConnection() {
        return connection;
    }

    public String getRemoteVersion() {
        return remoteVersion;
    }

    public String getRemoteCluster() {
        return remoteCluster;
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
        remoteVersion = version;
        cancelOutbound();
        Utils.info("connected to '%s' (%s), running v%s", getName(), connection.getName(), remoteVersion);
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

        out.put("publicAddress", normalizedPublicAddress);
        out.put("cluster", Global.network.getClusterName());
        
        // NAT stuff
        if (Global.config.getBoolean("sendPrivateAddress", true) &&
            (! privateAddress.equals("-")))
            out.put("privateAddress",
                    normalizedPrivateAddress.getAddress().getHostAddress() + ":" +
                    normalizedPrivateAddress.getPort());
        
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
        remotePublicAddress = message.getString("publicAddress");
        remoteCluster = message.getString("cluster");
        try {
            expandPublicAddress(remotePublicAddress);
        } catch (IllegalArgumentException e) {
            throw new ServerException(e.getMessage());
        }
        Utils.debug("received publicAddress '%s' from '%s'", remotePublicAddress, name);
        
        // NAT stuff
        remotePrivateAddress = message.getString("privateAddress");
        Utils.debug("received privateAddress '%s' from '%s'", remotePrivateAddress, name);
        
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

        Chat.receive(player, displayName, world, this, msg, toGates);
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

    private void normalizePrivateAddress(String addrStr) {
        if (addrStr.equals("-")) {
            normalizedPrivateAddress = null;
            return;
        }
        String[] parts = addrStr.split(":");
        String address = parts[0];
        int port = Global.plugin.getServer().getPort();
        if (parts.length > 1) {
            try {
                port = Integer.parseInt(parts[1]);
                if ((port < 1) || (port > 65535))
                    throw new IllegalArgumentException("invalid port " + parts[1]);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("invalid port " + parts[1]);
            }
        }
        
        if (address.equals("*")) {
            address = Global.plugin.getServer().getIp();
            if (parts.length == 1) port = Global.plugin.getServer().getPort();
            if ((address == null) || address.equals("0.0.0.0") || address.isEmpty()) {
                try {
                    InetAddress a = Network.getInterfaceAddress();
                    if (a == null)
                        throw new IllegalArgumentException("unable to get local interface address");
                    address = a.getHostAddress();
                } catch (NetworkException e) {
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
        } else {
            try {
                NetworkInterface iface = NetworkInterface.getByName(address);
                InetAddress a = Network.getInterfaceAddress(iface);
                if (a == null)
                    throw new IllegalArgumentException("unable to get local interface address for interface " + address);
                address = a.getHostAddress();    
            } catch (SocketException e) {
                // assume address is a DNS name or IP address
            }
        }
        normalizedPrivateAddress = new InetSocketAddress(address, port);
    }
    
    private void normalizePublicAddress(String addrStr) {
        StringBuilder sb = new StringBuilder();
        
        String patternMaps[] = addrStr.split("\\s+");
        for (String patternMap : patternMaps) {
            String items[] = patternMap.split("/");
            if (items.length > 1)
                for (int i = 1; i < items.length; i++) {
                    try {
                        Pattern.compile(items[i]);
                    } catch (PatternSyntaxException e) {
                        throw new IllegalArgumentException("invalid pattern: " + items[i]);
                    }
                }
            
            String[] parts = items[0].split(":");
            String address = parts[0];
            int port = Global.plugin.getServer().getPort();
            if (parts.length > 1) {
                try {
                    port = Integer.parseInt(parts[1]);
                    if ((port < 1) || (port > 65535))
                        throw new IllegalArgumentException("invalid port " + parts[1]);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("invalid port " + parts[1]);
                }
            }
            if (! address.equals("*")) {
                try {
                    NetworkInterface iface = NetworkInterface.getByName(address);
                    InetAddress a = Network.getInterfaceAddress(iface);
                    if (a == null)
                        throw new IllegalArgumentException("unable to get local interface address for interface " + address);
                    address = a.getHostAddress();    
                } catch (SocketException e) {
                    // assume address is a DNS name or IP address
                }
            }

            sb.append(address).append(":").append(port);
            if (items.length > 1)
                for (int i = 1; i < items.length; i++)
                    sb.append("/").append(items[i]);
            sb.append(" ");
        }
     
        normalizedPublicAddress = sb.toString().trim();
    }
    
    // called on the receiving side to expand the address given by the sending side
    private void expandPublicAddress(String addrStr) {
        if (addrStr == null)
            throw new IllegalArgumentException("publicAddress is required");
        
        remotePublicAddressMap = new HashMap<String,Set<Pattern>>();
        StringBuilder sb = new StringBuilder();
        
        String patternMaps[] = addrStr.split("\\s+");
        for (String patternMap : patternMaps) {
            Set<Pattern> patterns = new HashSet<Pattern>();
            String items[] = patternMap.split("/");
            if (items.length == 1)
                patterns.add(Pattern.compile(".*"));
            else
                for (int i = 1; i < items.length; i++) {
                    try {
                        patterns.add(Pattern.compile(items[i]));
                    } catch (PatternSyntaxException e) {
                        throw new IllegalArgumentException("invalid pattern: " + items[i]);
                    }
                }
            
            String[] parts = items[0].split(":");
            String address = parts[0];
            int port = DEFAULT_MC_PORT;
            if (parts.length > 1) {
                try {
                    port = Integer.parseInt(parts[1]);
                    if ((port < 1) || (port > 65535))
                        throw new IllegalArgumentException("invalid port " + parts[1]);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("invalid port " + parts[1]);
                }
            }
            if (address.equals("*"))
                address = pluginAddress.split(":")[0];
            
            remotePublicAddressMap.put(address + ":" + port, patterns);
            sb.append(address).append(":").append(port);
            if (items.length > 1)
                for (int i = 1; i < items.length; i++)
                    sb.append("/").append(items[i]);
            sb.append(" ");
        }
        remotePublicAddress = sb.toString().trim();
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
