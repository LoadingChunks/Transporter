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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import org.bennedum.transporter.net.Connection;
import org.bennedum.transporter.net.NetworkException;
import org.bennedum.transporter.net.Network;
import org.bennedum.transporter.net.Message;
import org.bennedum.transporter.net.Result;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
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

    public static boolean isValidName(String name) {
        if ((name.length() == 0) || (name.length() > 15)) return false;
        return ! (name.contains(".") || name.contains("*"));
    }

    private String name;
    private String address;
    private String key;
    private boolean enabled;
    private Connection connection = null;
    private boolean allowReconnect = true;
    private String minecraftAddress = null;
    private String version = null;
    private int reconnectTask = -1;
    private boolean connected = false;

    public Server(String name, String address, String key) throws ServerException {
        this.name = name;
        this.address = address;
        this.key = key;
        enabled = true;
        validate();
    }

    public Server(ConfigurationNode node) throws ServerException {
        name = node.getString("name");
        address = node.getString("address");
        key = node.getString("key");
        enabled = node.getBoolean("enabled", true);
        validate();
    }

    private void validate() throws ServerException {
        if (name == null)
            throw new ServerException("name is required");
        if (! isValidName(name))
            throw new ServerException("name is not valid");
        if (address == null)
            throw new ServerException("address is required");
        try {
            Network.makeAddress(address);
        } catch (NetworkException ce) {
            throw new ServerException("address: %s", ce.getMessage());
        }
        if ((key == null) || key.isEmpty())
            throw new ServerException("key is required");
    }

    public void change(String address, String key) throws ServerException {
        String oldAddress = this.address;
        String oldKey = this.key;
        this.address = address;
        this.key = key;
        try {
            validate();
        } catch (ServerException se) {
            this.address = oldAddress;
            this.key = oldKey;
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

    public String getMinecraftAddress(InetSocketAddress addr) {
        return Network.getMinecraftAddress(minecraftAddress, addr.getAddress());
    }

    public String getAddress() {
        return address;
    }

    public void setConnection(Connection conn) {
        connection = conn;
    }

    public Connection getConnection() {
        return connection;
    }

    public Map<String,Object> encode() {
        Map<String,Object> node = new HashMap<String,Object>();
        node.put("name", name);
        node.put("address", address);
        node.put("key", key);
        node.put("enabled", enabled);
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
        connection = new Connection(this, address);
        connection.open();
    }

    public void disconnect(boolean allowReconnect) {
        this.allowReconnect = allowReconnect;
        cancelOutbound();
        if (connection == null) return;
        connection.close();
//        connection = null;
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
                connect();
            }
        }, time);
    }

    public void refresh() {
        if (! isConnected())
            connect();
        else
            doGetInfo();
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

    public void doGetInfo() {
        if (! isConnected()) return;
        Message message = createMessage("getInfo");
        connection.sendMessage(message, true);
    }

    public void doGateAdded(LocalGate gate) {
        if (! isConnected()) return;
        Message message = createMessage("addGate");
        message.put("name", gate.getName());
        message.put("worldName", gate.getWorldName());
        message.put("designName", gate.getDesignName());
        connection.sendMessage(message, true);
    }

    public void doGateRenamed(String oldFullName, String newName) {
        if (! isConnected()) return;
        Message message = createMessage("renameGate");
        message.put("oldName", oldFullName);
        message.put("newName", newName);
        connection.sendMessage(message, true);
    }

    public void doGateRemoved(LocalGate gate) {
        if (! isConnected()) return;
        Message message = createMessage("removeGate");
        message.put("name", gate.getFullName());
        connection.sendMessage(message, true);
    }

    public void doGateDestroyed(LocalGate gate) {
        if (! isConnected()) return;
        Message message = createMessage("destroyGate");
        message.put("name", gate.getFullName());
        connection.sendMessage(message, true);
    }

    public void doGateAttach(RemoteGate toGate, LocalGate fromGate) {
        if (! isConnected()) return;
        Message message = createMessage("attachGate");
        message.put("to", toGate.getWorldName() + "." + toGate.getName());
        message.put("from", fromGate.getFullName());
        connection.sendMessage(message, true);
    }

    public void doGateDetach(RemoteGate toGate, LocalGate fromGate) {
        if (! isConnected()) return;
        Message message = createMessage("detachGate");
        message.put("to", toGate.getWorldName() + "." + toGate.getName());
        message.put("from", fromGate.getFullName());
        connection.sendMessage(message, true);
    }

    // blocks until acknowledgement from remote server is received
    // fromGate can be null if the player is being sent directly
    public void doExpectEntity(Entity entity, LocalGate fromGate, RemoteGate toGate) throws ServerException {
        if (! isConnected())
            throw new ServerException("server '%s' is offline", name);

        Message message = createMessage("expectEntity");
        message.put("fromGate", (fromGate == null) ? null : fromGate.getFullName());
        message.put("toGate", Gate.makeLocalName(toGate.getGlobalName()));
        message.put("fromGateDirection", (fromGate == null) ? null : fromGate.getDirection().toString());

        EntityState entityState = EntityState.extractState(entity);
        message.put("entity", entityState.encode());

        Result futureResult = connection.sendRequest(message, true);
        try {
            Message result = futureResult.get();
            if (result.getBoolean("success")) return;
            throw new ServerException(result.getString("error"));
        } catch (CancellationException e) {
            throw new ServerException("server '%s' went offline during teleportaion", name);
        } catch (InterruptedException e) {
            throw new ServerException("server '%s' was interrupted during teleportaion", name);
        } catch (TimeoutException e) {
            throw new ServerException("request to server '%s' timed out during teleportaion", name);
        }
    }

    // used to tell the sending side we've recieved the entity
    // fromGate can be null if the player is being sent directly
    public void doConfirmArrival(EntityState entityState, String fromGateName, String toGateName) {
        if (! isConnected()) return;
        Message message = createMessage("confirmArrival");
        message.put("fromGate", fromGateName);
        message.put("toGate", toGateName);
        message.put("entity", entityState.encode());
        connection.sendMessage(message, true);
    }

    // used to tell the sending side we didn't receive the entity
    // fromGate can be null if the player is being sent directly
    public void doCancelArrival(EntityState entityState, String fromGateName, String toGateName) {
        if (! isConnected()) return;
        Message message = createMessage("cancelArrival");
        message.put("fromGate", fromGateName);
        message.put("toGate", toGateName);
        message.put("entity", entityState.encode());
        connection.sendMessage(message, true);
    }

    public void doRelayChat(Player player, String msg, Set<RemoteGate> toGates) {
        if (! isConnected()) return;
        Message message = createMessage("relayChat");
        message.put("player", player.getName());
        message.put("displayName", player.getDisplayName());
        message.put("message", msg);
        List<String> gates = new ArrayList<String>(toGates.size());
        for (RemoteGate gate : toGates)
            gates.add(Gate.makeLocalName(gate.getGlobalName()));
        message.put("toGates", gates);
        connection.sendMessage(message, true);
    }




    // Connection callbacks, called from main network thread.
    // If the task is going to take a while, use a worker thread.

    // outbound connection
    public void onConnected() {
        allowReconnect = true;
        connected = true;
        Utils.info("connected to '%s' (%s)", getName(), connection.getName());
        connection.sendMessage(handleGetInfo(), true);
    }

    public void onDisconnected() {
        if (connected) {
            Utils.info("disconnected from '%s' (%s)", getName(), connection.getName());
            connected = false;
        }
        connection = null;
        Global.gates.remove(this);
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
            if (command.equals("ping"))
                response = handlePing(message);
            else if (command.equals("getInfo"))
                response = handleGetInfo();
            else if (command.equals("setInfo"))
                handleSetInfo(message);
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
            else if (command.equals("expectEntity"))
                response = handleExpectEntity(message);
            else if (command.equals("cancelArrival"))
                handleCancelArrival(message);
            else if (command.equals("confirmArrival"))
                handleConfirmArrival(message);
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
            connection.sendMessage(response, true);
        }
    }

    // Command processing

    private Message handlePing(Message message) {
        return message;
    }

    private Message handleGetInfo() {
        if (! isConnected()) return null;
        Message out = createMessage("setInfo");
        String addr = Global.network.getMinecraftAddress();
        out.put("minecraftAddress", addr);
        out.put("version", Global.pluginVersion);
        List<Message> gates = new ArrayList<Message>();
        for (LocalGate gate : Global.gates.getLocalGates()) {
            Message m = new Message();
            m.put("name", gate.getName());
            m.put("worldName", gate.getWorldName());
            m.put("designName", gate.getDesignName());
            gates.add(m);
        }
        out.put("gates", gates);
        return out;
    }

    private void handleSetInfo(Message message) throws ServerException {
        String addr = message.getString("minecraftAddress");
        if (addr == null)
            throw new ServerException("missing minecraftAddress");
        Collection<Message> gates = message.getMessageList("gates");
        if (gates == null)
            throw new ServerException("missing gates");
        version = message.getString("version");
        minecraftAddress = addr;
        Global.gates.remove(this);
        for (Message m : gates) {
            try {
                Global.gates.add(new RemoteGate(this, m));
            } catch (GateException ge) {
                Utils.warning("received bad gate from '%s'", getName());
            }
        }
        Utils.info("received information from '%s', running v%s", name, version);
    }

    private void handleAddGate(Message message) throws GateException {
        RemoteGate gate = new RemoteGate(this, message);
        Global.gates.add(gate);
    }

    private void handleRenameGate(Message message) throws ServerException, GateException {
        String oldName = message.getString("oldName");
        if (oldName == null)
            throw new ServerException("missing oldName");
        oldName = Gate.makeFullName(this, oldName);
        String newName = message.getString("newName");
        if (newName == null)
            throw new ServerException("missing newName");
        Global.gates.rename(oldName, newName);
    }

    private void handleRemoveGate(Message message) throws ServerException {
        String gateName = message.getString("name");
        if (gateName == null)
            throw new ServerException("missing name");
        gateName = Gate.makeFullName(this, gateName);
        Gate gate = Global.gates.get(gateName);
        if (gate == null)
            throw new ServerException("unknown gate '%s'", gateName);
        if (gate.isSameServer())
            throw new ServerException("gate '%s' is not remote", gateName);
        Global.gates.remove((RemoteGate)gate);
    }

    private void handleDestroyGate(Message message) throws ServerException {
        String gateName = message.getString("name");
        if (gateName == null)
            throw new ServerException("missing name");
        gateName = Gate.makeFullName(this, gateName);
        Gate gate = Global.gates.get(gateName);
        if (gate == null)
            throw new ServerException("unknown gate '%s'", gateName);
        if (gate.isSameServer())
            throw new ServerException("gate '%s' is not remote", gateName);
        Global.gates.destroy((RemoteGate)gate);
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

        Gate toGate = Global.gates.get(toName);
        if (toGate == null)
            throw new ServerException("unknown to gate '%s'", toName);
        if (! toGate.isSameServer())
            throw new ServerException("to gate '%s' is local", toName);
        Gate fromGate = Global.gates.get(fromName);
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

        Gate toGate = Global.gates.get(toName);
        if (toGate == null)
            throw new ServerException("unknown to gate '%s'", toName);
        if (! toGate.isSameServer())
            throw new ServerException("to gate '%s' is local", toName);
        Gate fromGate = Global.gates.get(fromName);
        if (fromGate == null)
            throw new ServerException("unknown from gate '%s'", fromName);
        if (fromGate.isSameServer())
            throw new ServerException("from gate '%s' is not remote", fromName);
        toGate.detach(fromGate);
    }

    private Message handleExpectEntity(Message message) throws ServerException {
        String fromGate = message.getString("fromGate");
        if (fromGate != null)
            fromGate = Gate.makeFullName(this, fromGate);

        String toGate = message.getString("toGate");
        if (toGate == null)
            throw new ServerException("missing toGate");
        toGate = Gate.makeLocalName(toGate);

        String fromGateDirectionStr = message.getString("fromGateDirection");
        BlockFace fromGateDirection = null;
        if (fromGateDirectionStr != null) {
            try {
                fromGateDirection = BlockFace.valueOf(fromGateDirectionStr);
            } catch (IllegalArgumentException e) {
                throw new ServerException("invalid fromGateDirection");
            }
        }

        if (! message.containsKey("entity"))
            throw new ServerException("missing entity");
        EntityState entityState = EntityState.extractState(message.getMessage("entity"));
        if (entityState == null)
            throw new ServerException("invalid entity");

        try {
            Teleport.expect(entityState, this, fromGate, toGate, fromGateDirection);
        } catch (TeleportException te) {
            throw new ServerException(te.getMessage());
        }
        return new Message();
    }

    private void handleCancelArrival(Message message) throws ServerException {
        String fromGate = message.getString("fromGate");
        if (fromGate != null)
            fromGate = Gate.makeLocalName(fromGate);

        String toGate = message.getString("toGate");
        if (toGate == null)
            throw new ServerException("missing toGate");
        toGate = Gate.makeFullName(this, toGate);

        if (! message.containsKey("entity"))
            throw new ServerException("missing entity");
        EntityState entityState = EntityState.extractState(message.getMessage("entity"));
        if (entityState == null)
            throw new ServerException("invalid entity");

        try {
            Teleport.cancel(entityState, fromGate, toGate);
        } catch (TeleportException te) {
            throw new ServerException(te.getMessage());
        }
    }

    private void handleConfirmArrival(Message message) throws ServerException {
        String fromGate = message.getString("fromGate");
        if (fromGate != null)
            fromGate = Gate.makeLocalName(fromGate);

        String toGate = message.getString("toGate");
        if (toGate == null)
            throw new ServerException("missing toGate");
        toGate = Gate.makeFullName(this, toGate);

        if (! message.containsKey("entity"))
            throw new ServerException("missing entity");
        EntityState entityState = EntityState.extractState(message.getMessage("entity"));
        if (entityState == null)
            throw new ServerException("invalid entity");

        try {
            Teleport.confirm(entityState, fromGate, toGate);
        } catch (TeleportException te) {
            throw new ServerException(te.getMessage());
        }
    }

    private void handleRelayChat(Message message) throws ServerException {
        String player = message.getString("player");
        if (player == null)
            throw new ServerException("missing player");

        String displayName = message.getString("displayName");
        if (displayName == null)
            displayName = player;

        String msg = message.getString("message");
        if (msg == null)
            throw new ServerException("missing message");

        List<String> toGates = message.getStringList("toGates");
        if ((toGates == null) || toGates.isEmpty())
            throw new ServerException("missing toGates");

        for (int i = 0; i < toGates.size(); i++)
            toGates.set(i, Gate.makeLocalName(toGates.get(i)));

        Teleport.receiveChat(player, displayName, name, msg, toGates);
    }

    // Utility methods

    private Message createMessage(String command) {
        Message m = new Message();
        m.put("command", command);
        return m;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Server[");
        buf.append(name).append(",");
        buf.append(address).append(",");
        buf.append(key);
        buf.append("]");
        return buf.toString();
    }

}
