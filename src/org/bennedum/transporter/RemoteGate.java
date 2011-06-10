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
public final class RemoteGate extends Gate {

    private Server server = null;
    private String worldName;
    private String designName;

    public RemoteGate(Server server, Message message) throws GateException {
        this.server = server;
        name = message.getString("name");
        worldName = message.getString("worldName");
        designName = message.getString("designName");

        if (name == null)
            throw new GateException("name is required");
        if (! isValidName(name))
            throw new GateException("name is not valid");
        if (worldName == null)
            throw new GateException("worldName is required");
        if (designName == null)
            throw new GateException("designName is required");
        if (! Design.isValidName(designName))
            throw new GateException("designName is not valid");
    }

    public Server getServer() {
        return server;
    }

    @Override
    public String getWorldName() {
        return worldName;
    }

    @Override
    public String getServerName() {
        return server.getName();
    }

    @Override
    public String getDesignName() {
        return designName;
    }

    @Override
    public void dump(Context ctx) {
        Utils.debug("RemoteGate:");
        Utils.debug("  name = %s", name);
        Utils.debug("  designName = %s", getDesignName());
        Utils.debug("  serverName = %s", getServerName());
        Utils.debug("  worldName = %s", getWorldName());
    }

    @Override
    public void onRenameComplete() {}

    @Override
    protected void attach(Gate fromGate) {
        if (! (fromGate instanceof LocalGate)) return;
        server.doGateAttach(this, (LocalGate)fromGate);
    }

    @Override
    protected void detach(Gate fromGate) {
        if (! (fromGate instanceof LocalGate)) return;
        server.doGateDetach(this, (LocalGate)fromGate);
    }

}
