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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.util.config.Configuration;
import org.bukkit.util.config.ConfigurationNode;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class ServerCollection {

    private static final int CONNECT_DELAY = 4000;

    private Map<String,Server> servers = new HashMap<String,Server>();

    public void loadAll(Context ctx) {
        removeAll();
        servers = new HashMap<String,Server>();
        File file = new File(Global.plugin.getDataFolder(), "servers.yml");
        if (file.exists()) {
            try {
                if (! file.isFile())
                    throw new ServerException("not a file");
                if (! file.canRead())
                    throw new ServerException("unable to read file");
                Configuration conf = new Configuration(file);
                conf.load();

                List<ConfigurationNode> serverNodes = conf.getNodeList("servers", null);
                if (serverNodes != null) {
                    for (ConfigurationNode node : serverNodes) {
                        try {
                            Server server = new Server(node);
                            add(server);
                            ctx.sendLog("loaded server '%s'", server.getName());
                        } catch (ServerException se) {
                            ctx.warnLog("unable to load server: %s", se.getMessage());
                        }

                    }
                }
            } catch (ServerException se) {
                ctx.warnLog("unable to load servers: %s", se.getMessage());
            }
        }
        if (isEmpty())
            ctx.sendLog("no servers loaded");
    }

    public void saveAll(Context ctx) {
        File file = new File(Global.plugin.getDataFolder(), "servers.yml");
        Configuration conf = new Configuration(file);
        List<Map<String,Object>> serverNodes = new ArrayList<Map<String,Object>>();
        for (Server server : servers.values())
            serverNodes.add(server.encode());
        conf.setProperty("servers", serverNodes);
        conf.save();
        ctx.sendLog("servers saved");
    }

    public void add(final Server server) throws ServerException {
        String name = server.getName();
        if (servers.containsKey(name))
            throw new ServerException("a server with the same name already exists");
        servers.put(server.getName(), server);
        if (server.isEnabled())
            Utils.fireDelayed(new Runnable() {
                @Override
                public void run() {
                    server.connect();
                }
            }, CONNECT_DELAY);
    }

    public void remove(Server server) {
        String name = server.getName();
        if (! servers.containsKey(name)) return;
        servers.remove(name);
        server.disconnect(false);
    }

    public void removeAll() {
        for (Server server : new ArrayList<Server>(servers.values()))
            remove(server);
    }

    public Server get(String name) {
        if (servers.containsKey(name)) return servers.get(name);
        Server server = null;
        name = name.toLowerCase();
        for (String key : servers.keySet()) {
            if (key.toLowerCase().startsWith(name)) {
                if (server == null) server = servers.get(key);
                else return null;
            }
        }
        return server;
    }

    public List<Server> getAll() {
        return new ArrayList<Server>(servers.values());
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public int size() {
        return servers.size();
    }


}
