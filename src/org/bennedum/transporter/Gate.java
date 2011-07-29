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

import org.bukkit.World;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public abstract class Gate {

    public static boolean isValidName(String name) {
        if ((name.length() == 0) || (name.length() > 15)) return false;
        return ! (name.contains(".") || name.contains("*"));
    }

    public static String makeFullName(Server server, String fullName) {
        StringBuilder buf = new StringBuilder();
        buf.append(server.getName()).append(".");
        buf.append(fullName);
        return buf.toString();
    }

    public static String makeLocalName(String globalName) {
        String parts[] = globalName.split("\\.");
        if (parts.length == 2) return globalName;
        if (parts.length == 3) return parts[1] + "." + parts[2];
        throw new IllegalArgumentException("expected global name");
    }

    public abstract String getWorldName();
    public abstract String getServerName();
    public abstract String getDesignName();
    public abstract double getSendCost(Gate toGate);
    public abstract double getReceiveCost(Gate fromGate);

    public abstract void onRenameComplete();

    protected abstract void attach(Gate fromGate);
    protected abstract void detach(Gate fromGate);

    public abstract void dump(Context ctx);


    protected String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSameWorld(World world) {
        return isSameServer() &&
               getWorldName().equals(world.getName());
    }

    public boolean isSameWorld(Gate gate) {
        return gate.isSameServer() &&
               getWorldName().equals(gate.getWorldName());
    }

    public boolean isSameServer() {
        return getServerName() == null;
    }

    public String getFullName() {
        StringBuilder buf = new StringBuilder();
        if (getServerName() != null) {
            buf.append(getServerName());
            buf.append(".");
        }
        buf.append(getWorldName());
        buf.append(".");
        buf.append(getName());
        return buf.toString();
    }

    public String getGlobalName() {
        StringBuilder buf = new StringBuilder();
        if (getServerName() == null)
            buf.append("local");
        else
            buf.append(getServerName());
        buf.append(".");
        buf.append(getWorldName());
        buf.append(".");
        buf.append(getName());
        return buf.toString();
    }

    public String getName(Context ctx) {
        if (ctx == null) return getFullName();
        if (! ctx.isPlayer()) return getFullName();
        World world = ctx.getPlayer().getWorld();
        if (isSameWorld(world)) return getName();
        return getFullName();
    }

    @Override
    public String toString() {
        return "Gate[" + getFullName() + "]";
    }

}
