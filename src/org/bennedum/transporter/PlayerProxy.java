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

import org.bennedum.transporter.net.Message;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class PlayerProxy {
    
    private String name;
    private String displayName;
    private String serverName;
    private String worldName;
    
    public PlayerProxy(String name, String displayName, String serverName, String worldName) {
        this.name = name;
        this.displayName = displayName;
        this.serverName = serverName;
        this.worldName = worldName;
    }
    
    public PlayerProxy(Server server, Message m) throws TransporterException {
        name = m.getString("name");
        if (name == null)
            throw new TransporterException("missing name");
        displayName = m.getString("displayName");
        if (displayName == null)
            throw new TransporterException("missing displayName");
        worldName = m.getString("world");
        if (worldName == null)
            throw new TransporterException("missing world");
        serverName = server.getName();
    }
    
    public Message encode() {
        Message m = new Message();
        m.put("name", name);
        m.put("displayName", displayName);
        m.put("world", worldName);
        return m;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getServerName() {
        return serverName;
    }
    
    public String getWorldName() {
        return worldName;
    }
    
    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }
    
}
