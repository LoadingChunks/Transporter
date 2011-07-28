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
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public abstract class EntityState {

    public static EntityState extractState(Message message) {
        String entityType = message.getString("entityType");
        if (entityType == null) return null;
        if (entityType.equals("player"))
            return new PlayerState(message);
        else if (entityType.equals("vehicle"))
            return new VehicleState(message);
        else
            return null;
    }

    public static EntityState extractState(Entity entity) {
        if (entity instanceof Vehicle)
            return new VehicleState((Vehicle)entity);
        if (entity instanceof Player)
            return new PlayerState((Player)entity);
        throw new IllegalArgumentException("unable to extract state from " + entity.getClass().getName());
    }

    protected int entityId;
    private float pitch;
    private float yaw;
    private int fireTicks;
    private double velX;
    private double velY;
    private double velZ;

    public EntityState(Entity entity) {
        entityId = entity.getEntityId();
        Location loc = entity.getLocation();
        pitch = loc.getPitch();
        yaw = loc.getYaw();
        fireTicks = entity.getFireTicks();
        Vector vel = entity.getVelocity();
        velX = vel.getX();
        velY = vel.getY();
        velZ = vel.getZ();
    }

    public EntityState(Message in) {
        entityId = in.getInt("entityId");
        pitch = in.getFloat("pitch");
        yaw = in.getFloat("yaw");
        fireTicks = in.getInt("fireTicks");
        velX = in.getDouble("velX");
        velY = in.getDouble("velX");
        velZ = in.getDouble("velX");
    }

    public Message encode() {
        Message out = new Message();
        out.put("entityId", entityId);
        out.put("pitch", pitch);
        out.put("yaw", yaw);
        out.put("fireTicks", fireTicks);
        out.put("velX", velX);
        out.put("velY", velY);
        out.put("velZ", velZ);
        return out;
    }

    public int getEntityId() {
        return entityId;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public Vector getVelocity() {
        return new Vector(velX, velY, velZ);
    }

    public PlayerState getPlayerState() {
        return null;
    }

    public String getName() {
        return "unknown";
    }

    public boolean isPlayer() {
        return false;
    }

    public Entity restore(Location location, Vector velocity, Player player) {
        velX = velocity.getX();
        velY = velocity.getY();
        velZ = velocity.getZ();
        return null;
    }

    protected void applyTo(Entity entity) {
        entity.setFireTicks(fireTicks);
        entity.setVelocity(new Vector(velX, velY, velZ));
    }

    protected Message encodeItemStack(ItemStack stack) {
        if (stack == null) return null;
        Message s = new Message();
        s.put("type", stack.getTypeId());
        s.put("amount", stack.getAmount());
        s.put("durability", stack.getDurability());
        MaterialData data = stack.getData();
        if (data != null)
            s.put("data", (int)data.getData());
        return s;
    }

    protected ItemStack decodeItemStack(Message s) {
        if (s == null) return null;
        ItemStack stack = new ItemStack(
            s.getInt("type"),
            s.getInt("amount"),
            (short)s.getInt("durability"));
        if (s.containsKey("data")) {
            MaterialData data = stack.getData();
            if (data != null)
                data.setData((byte)s.getInt("data"));
        }
        return stack;
    }

}
