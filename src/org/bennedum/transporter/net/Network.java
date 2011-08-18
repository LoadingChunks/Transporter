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
package org.bennedum.transporter.net;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.Global;
import org.bennedum.transporter.Server;
import org.bennedum.transporter.Servers;
import org.bennedum.transporter.Utils;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Network extends Thread {

    private static final int READ_BUFFER_SIZE = 4096;
    private static final int SELECT_INTERVAL = 30000;

    public static final int DEFAULT_PORT = 25555;

    public static final List<String> OPTIONS = new ArrayList<String>();

    static {
        OPTIONS.add("listenAddress");
        OPTIONS.add("serverKey");
        OPTIONS.add("clusterName");
    }

    public static InetSocketAddress makeInetSocketAddress(String addrStr, int defPort, boolean allowWildcard) throws NetworkException {
        String[] parts = addrStr.split(":");
        InetAddress address = null;
        int port = defPort;
        if (parts[0].equals("0.0.0.0") || parts[0].equals("*")) {
            if (! allowWildcard)
                throw new NetworkException("wildcard address not allowed");
        } else {
            try {
                address = InetAddress.getByName(parts[0]);
            } catch (UnknownHostException uhe) {
                throw new NetworkException("unknown host address");
            }
        }
        if (parts.length > 1)
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException nfe) {
                throw new NetworkException("invalid port '%s'", parts[1]);
            }
        if ((port < 1) || (port > 65535))
            throw new NetworkException("invalid port '%d'", port);
        return new InetSocketAddress(address, port);
    }

    public static InetAddress getInterfaceAddress() throws NetworkException {
        try {
            for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements(); ) {
                NetworkInterface iface = e.nextElement();
                if (! iface.isUp()) continue;
                if (iface.isLoopback()) continue;
                InetAddress addr = getInterfaceAddress(iface);
                if (addr == null) continue;
                return addr;
            }
            return null;
        } catch (SocketException e) {
            throw new NetworkException("unable to get local interfaces");
        }
    }

    public static InetAddress getInterfaceAddress(NetworkInterface iface) {
        for (Enumeration<InetAddress> e = iface.getInetAddresses(); e.hasMoreElements(); ) {
            InetAddress addr = e.nextElement();
            if (addr.isLoopbackAddress()) continue;
            if (addr instanceof Inet4Address) return addr;
        }
        return null;
    }

    private State state = State.STOPPED;
    private String listenAddress;
    private InetSocketAddress listenInetSocketAddress;
    private String serverKey;
    private String clusterName;

    private final Set<Pattern> banned = new HashSet<Pattern>();

    private Selector selector = null;
    private final Map<SocketChannel,Connection> channels = new HashMap<SocketChannel,Connection>();
    private final Set<Connection> opening = new HashSet<Connection>();
    private final Set<Connection> closing = new HashSet<Connection>();


    public Network() {
        try {
            setListenAddress(Global.config.getString("listenAddress"));
        } catch (IllegalArgumentException e) {
            Utils.warning(e.getMessage());
        }
        try {
            setServerKey(Global.config.getString("serverKey"));
        } catch (IllegalArgumentException e) {
            Utils.warning(e.getMessage());
        }
        setClusterName(Global.config.getString("clusterName"));
    }

    // called from main thread
    public void start(Context ctx) {
        if ((listenAddress == null) || (serverKey == null))
            Utils.warning("network manager cannot be started");
        else {
            List<String> addresses = Global.config.getStringList("bannedAddresses", null);
            if (addresses != null)
                for (String addressPattern : addresses) {
                    try {
                        Pattern pattern = Pattern.compile(addressPattern);
                        banned.add(pattern);
                    } catch (PatternSyntaxException pse) {
                        Utils.warning("ignored invalid bannedAddress pattern '%s': %s", addressPattern, pse.getMessage());
                    }
                }

            ctx.send("starting network manager");
            start();
        }
    }

    /* Begin options */

    public String getListenAddress() {
        return listenAddress;
    }

    public void setListenAddress(String address) {
        if (address == null)
            throw new IllegalArgumentException("listenAddress is required");
        try {
            listenInetSocketAddress = makeInetSocketAddress(address, DEFAULT_PORT, true);
        } catch (NetworkException e) {
            throw new IllegalArgumentException("listenAddress: " + e.getMessage());
        }
        listenAddress = address;
        Global.config.setProperty("listenAddress", listenAddress);
    }

    public InetSocketAddress getListenInetSocketAddress() {
        return listenInetSocketAddress;
    }

    public String getServerKey() {
        return serverKey;
    }

    public void setServerKey(String key) {
        if ((key == null) || key.equals("none"))
            throw new IllegalArgumentException("serverKey is required");
        serverKey = key;
        Global.config.setProperty("serverKey", serverKey);
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String name) {
        clusterName = name;
        Global.config.setProperty("clusterName", clusterName);
    }

    public String resolveOption(String option) throws NetworkException {
        for (String opt : OPTIONS) {
            if (opt.toLowerCase().startsWith(option.toLowerCase()))
                return opt;
        }
        throw new NetworkException("unknown option");
    }

    public void setOption(String option, String value) throws NetworkException {
        if (! OPTIONS.contains(option))
            throw new NetworkException("unknown option");
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
                throw new NetworkException("unsupported option type");

        } catch (InvocationTargetException ite) {
            throw (NetworkException)ite.getCause();
        } catch (NoSuchMethodException nsme) {
            throw new NetworkException("invalid method");
        } catch (IllegalArgumentException iae) {
            throw new NetworkException("invalid value");
        } catch (NoSuchFieldException nsfe) {
            throw new NetworkException("unknown option");
        } catch (IllegalAccessException iae) {
            throw new NetworkException("unable to set the option");
        }
    }

    public String getOption(String option) throws NetworkException {
        if (! OPTIONS.contains(option))
            throw new NetworkException("unknown option");
        String methodName = "get" +
                option.substring(0, 1).toUpperCase() +
                option.substring(1);
        try {
            Method m = getClass().getMethod(methodName);
            Object value = m.invoke(this);
            if (value == null) return "(null)";
            return value.toString();
        } catch (InvocationTargetException ite) {
            throw (NetworkException)ite.getCause();
        } catch (NoSuchMethodException nsme) {
            throw new NetworkException("invalid method");
        } catch (IllegalAccessException iae) {
            throw new NetworkException("unable to read the option");
        }
    }

    /* End options */

    // called from main thread
    public void stop(Context ctx) {
        if ((! isAlive()) || (state != State.RUNNING)) return;
        ctx.send("stopping network manager...");
        state = State.STOP;
        selector.wakeup();
        while (isAlive()) {
            try {
                this.join();
            } catch (InterruptedException ie) {}
        }
    }

    public boolean isStopped() {
        return (state == State.STOP) || (state == State.STOPPING) || (state == State.STOPPED);
    }

    // called from main thread
    public boolean addBan(String addrStr) throws NetworkException {
        Pattern pattern;
        try {
            pattern = Pattern.compile(addrStr);
        } catch (PatternSyntaxException pse) {
            throw new NetworkException("invalid pattern: %s", pse.getMessage());
        }
        synchronized (banned) {
            if (banned.contains(pattern)) return false;
            banned.remove(pattern);
            saveBannedAddresses();
            return true;
        }
    }

    // called from main thread
    public boolean removeBan(String addrStr) {
        synchronized (banned) {
            Iterator<Pattern> i = banned.iterator();
            while (i.hasNext()) {
                Pattern p = i.next();
                if (p.pattern().equals(addrStr)) {
                    i.remove();
                    saveBannedAddresses();
                    return true;
                }
            }
        }
        return false;
    }

    // called from main thread
    public void removeAllBans() {
        synchronized (banned) {
            banned.clear();
            saveBannedAddresses();
        }
    }

    // called from synchronized block
    private void saveBannedAddresses() {
        List<String> bannedAddresses = new ArrayList<String>(banned.size());
        for (Pattern p : banned)
            bannedAddresses.add(p.pattern());
        Global.config.setProperty("bannedAddresses", bannedAddresses);
    }

    public List<String> getBanned() {
        List<String> l = new ArrayList<String>();
        synchronized (banned) {
            for (Pattern pattern : banned) {
                l.add(pattern.toString());
            }
        }
        return l;
    }

    // called from selection thread
    private void kill(Connection conn) {
        Utils.debug("kill %s", conn);
        SocketChannel channel = conn.getChannel();
        if (channel != null) {
            SelectionKey key = channel.keyFor(selector);
            if (key != null)
                key.cancel();
            try {
                channel.close();
            } catch (IOException e) {}
            channels.remove(channel);
        }
        synchronized (closing) {
            closing.remove(conn);
        }
        synchronized (opening) {
            opening.remove(conn);
        }
        conn.onKilled();
    }

    @Override
    public void run() {

        ServerSocketChannel serverChannel = null;

        try {
            // create the selector
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);

            // bind to address and port
            serverChannel.socket().bind(listenInetSocketAddress);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            Utils.info("network manager listening on %s:%d", listenInetSocketAddress.getAddress().getHostAddress(), listenInetSocketAddress.getPort());
            state = State.RUNNING;

            // processing
            while (true) {
                if (state == State.STOP) {
                    state = State.STOPPING;
                    serverChannel.keyFor(selector).cancel();
                    synchronized (closing) {
                        closing.addAll(channels.values());
                        for (Connection conn : closing)
                            wantWrite(conn);
                    }
                    synchronized (opening) {
                        opening.removeAll(channels.values());
                    }
                }
                if ((state == State.STOPPING) && channels.isEmpty()) break;

                // Close connections that are still waiting to open
                synchronized (closing) {
                    if (! closing.isEmpty()) {
                        for (Connection conn : new HashSet<Connection>(closing)) {
                            if (conn.onHasWriteData()) continue;
                            kill(conn);
                            conn.onClosed();
                        }
                    }
                }
                if ((state == State.STOPPING) && channels.isEmpty()) break;

                // Open connections that are waiting
                synchronized (opening) {
                    if (! opening.isEmpty()) {
                        for (Connection conn : opening) {
                            try {
                                SocketChannel channel = SocketChannel.open();
                                channel.configureBlocking(false);
                                try {
                                    InetSocketAddress address = makeInetSocketAddress(conn.getConnectAddress(), DEFAULT_PORT, false);
                                    channel.connect(address);
                                } catch (NetworkException e) {}
                                channel.register(selector, SelectionKey.OP_CONNECT);
                                channels.put(channel, conn);
                                conn.onOpening(this, channel);
                            } catch (IOException e) {
                                conn.onException(e);
                            }
                        }
                        opening.clear();
                    }
                }

                // Tell connected servers to do keep alives
                for (Server server : Servers.getAll()) {
                    server.sendKeepAlive();
                    server.checkKeepAlive();
                }

                if (selector.select(SELECT_INTERVAL) > 0) {
                    Iterator keys = selector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey key = (SelectionKey)keys.next();
                        keys.remove();
                        if (! key.isValid()) continue;
                        if (key.isAcceptable()) onAccept(key);
                        else if (key.isConnectable()) onConnect(key);
                        else if (key.isReadable()) onRead(key);
                        else if (key.isWritable()) onWrite(key);
                    }
                }

            }

            Utils.info("network manager stopped listening");

        } catch (IOException ioe) {
            Utils.severe(ioe, "network manager IOException: " + ioe.getMessage());
        }
        state = State.STOPPED;

        if (selector != null)
            try {
                selector.close();
            } catch (IOException ioe) {}
        if (serverChannel != null)
            try {
                serverChannel.close();
            } catch (IOException ioe) {}

    }

    private void onAccept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel)key.channel();
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);

        Socket socket = channel.socket();
        InetSocketAddress remoteAddress = (InetSocketAddress)socket.getRemoteSocketAddress();

        // rejected banned addresses
        synchronized (banned) {
            String addr = remoteAddress.getAddress().getHostAddress();
            for (Pattern p : banned) {
                if (p.matcher(addr).matches()) {
                    Utils.info("rejected connection from banned address '%s'", addr);
                    try {
                        socket.close();
                    } catch (IOException ioe) {}
                    return;
                }
            }
        }

        Connection conn = new Connection(this, channel);
        channels.put(channel, conn);
        channel.register(selector, SelectionKey.OP_READ);
        conn.onAccepted();
    }

    private void onConnect(SelectionKey key) {
        SocketChannel channel = (SocketChannel)key.channel();
        Connection conn = channels.get(channel);
        if (conn == null) {
            key.cancel();
            try {
                channel.close();
            } catch (IOException e) {}
            return;
        }

        try {
            if (channel.isConnectionPending())
                channel.finishConnect();
        } catch (IOException e) {
            conn.onException(e);
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
        conn.onOpened();
    }

    private void onRead(SelectionKey key) {
        SocketChannel channel = (SocketChannel)key.channel();
        Connection conn = channels.get(channel);
        if (conn == null) {
            key.cancel();
            try {
                channel.close();
            } catch (IOException e) {}
            return;
        }

        ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
        int numRead = 0;
        while (true) {
            try {
                numRead = channel.read(buffer);
            } catch (IOException e) {
                conn.onException(e);
                return;
            }
            Utils.debug("read %d from %s", numRead, conn);
            if (numRead <= 0) break;
            conn.onReadData(Arrays.copyOfRange(buffer.array(), 0, numRead));
            if (numRead < READ_BUFFER_SIZE) break;
            buffer.clear();
        }
        if (numRead == -1) {
            kill(conn);
            conn.onClosed();
        }
    }

    private void onWrite(SelectionKey key) {
        SocketChannel channel = (SocketChannel)key.channel();
        Connection conn = channels.get(channel);
        if (conn == null) {
            key.cancel();
            try {
                channel.close();
            } catch (IOException e) {}
            return;
        }

        ByteBuffer buffer;
        byte[] data;
        int numWrote = 0;
        while (true) {
            data = conn.onGetWriteData();
            if (data == null) break;
            buffer = ByteBuffer.wrap(data);
            try {
                numWrote = channel.write(buffer);
            } catch (IOException e) {
                conn.onException(e);
                return;
            }
            Utils.debug("wrote %d to %s", numWrote, conn);
            if (numWrote == data.length) continue;
            conn.onPutWriteData(Arrays.copyOfRange(data, numWrote, data.length - 1));
            break;
        }
        if (! conn.onHasWriteData()) {
            key.interestOps(SelectionKey.OP_READ);
            synchronized (closing) {
                if (closing.contains(conn)) {
                    kill(conn);
                    conn.onClosed();
                    return;
                }
            }
            conn.onWriteCompleted();
        }
    }

    // can be called from any thread
    public void wantWrite(Connection conn) {
        SelectionKey key = conn.getChannel().keyFor(selector);
        if ((key == null) || (! key.isValid())) return;
        key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);

        try {
            Utils.debug("wantWrite to %s: %s %s %s %s", conn,
                conn.getChannel().isOpen(),
                conn.getChannel().isConnected(),
                conn.getChannel().socket().isClosed(),
                conn.getChannel().socket().isConnected());
        } catch (Throwable t) {
            Utils.debug("wantWrite to %s: got throwable: %s: %s", conn, t.getClass().getName(), t.getMessage());
        }

        selector.wakeup();
    }

    public void open(Connection conn) {
        synchronized (opening) {
            opening.add(conn);
        }
        if (selector != null)
            selector.wakeup();
    }

    public void close(Connection conn) {
        synchronized (closing) {
            closing.add(conn);
        }
        wantWrite(conn);
    }

    private enum State {
        STOPPED,
        RUNNING,
        STOP,
        STOPPING;
    }

}
