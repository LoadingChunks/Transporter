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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bennedum.transporter.net.Message;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.PoweredMinecart;
import org.bukkit.entity.StorageMinecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class VehicleState extends EntityState {

    private Type type;
    private EntityState passengerState = null;
    private ItemStack[] inventory = null; // for storage minecarts

    public VehicleState(Vehicle vehicle) {
        super(vehicle);
        if (vehicle instanceof Minecart)
            type = Type.MINECART;
        else if (vehicle instanceof PoweredMinecart)
            type = Type.POWERED_MINECART;
        else if (vehicle instanceof StorageMinecart) {
            type = Type.STORAGE_MINECART;
            Inventory inv = ((StorageMinecart)vehicle).getInventory();
            inventory = Arrays.copyOf(inv.getContents(), inv.getSize());
        } else if (vehicle instanceof Boat)
            type = Type.BOAT;
        else
            throw new IllegalArgumentException("can't create state for " + vehicle.getClass().getName());

        // this would need to change if mobs are supported someday
        if (vehicle.getPassenger() instanceof Player)
            passengerState = EntityState.extractState(vehicle.getPassenger());
    }

    public VehicleState(Message in) {
        super(in);
        type = Type.valueOf(in.getString("vehicleType"));
        if (in.containsKey("inventory")) {
            List<Message> inv = in.getMessageList("inventory");
            inventory = new ItemStack[inv.size()];
            for (int slot = 0; slot < inv.size(); slot++)
                inventory[slot] = decodeItemStack(inv.get(slot));
        }
        if (in.containsKey("passenger"))
            passengerState = EntityState.extractState(in.getMessage("passenger"));
    }

    @Override
    public Message encode() {
        Message out = super.encode();
        out.put("entityType", "vehicle");
        out.put("vehicleType", type.toString());
        if (inventory != null) {
            List<Message> inv = new ArrayList<Message>();
            for (int slot = 0; slot < inventory.length; slot++)
                inv.add(encodeItemStack(inventory[slot]));
            out.put("inventory", inv);
        }
        if (passengerState != null)
            out.put("passenger", passengerState.encode());
        return out;
    }

    @Override
    public PlayerState getPlayerState() {
        if (passengerState == null) return null;
        return passengerState.getPlayerState();
    }

    @Override
    public String getName() {
        return type.toString() + "/" + entityId;
    }

    public ItemStack[] getInventory() {
        return inventory;
    }

    public EntityState getPassengerState() {
        return passengerState;
    }

    @Override
    public Entity restore(Location location, Vector velocity, Player player) {
        super.restore(location, velocity, player);
        Entity entity;
        switch (type) {
            case MINECART:
                entity = location.getWorld().spawn(location, Minecart.class);
                break;
            case POWERED_MINECART:
                entity = location.getWorld().spawn(location, PoweredMinecart.class);
                break;
            case STORAGE_MINECART:
                entity = location.getWorld().spawn(location, StorageMinecart.class);
                break;
            case BOAT:
                entity = location.getWorld().spawn(location, Boat.class);
                break;
            default:
                return null;
        }
        applyTo(entity);
        if (passengerState != null)
            entity.setPassenger(passengerState.restore(location, velocity, player));
        return entity;
    }

    @Override
    protected void applyTo(Entity entity) {
        super.applyTo(entity);
        if (type == Type.STORAGE_MINECART) {
            StorageMinecart mc = (StorageMinecart)entity;
            Inventory inv = mc.getInventory();
            for (int slot = 0; slot <  inventory.length; slot++)
                inv.setItem(slot, inventory[slot]);
        }
    }

    private enum Type {
        MINECART,
        POWERED_MINECART,
        STORAGE_MINECART,
        BOAT
    }

}
