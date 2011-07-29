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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bennedum.transporter.net.Message;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.PoweredMinecart;
import org.bukkit.entity.StorageMinecart;
import org.bukkit.entity.Vehicle;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.material.MaterialData;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Reservation {
    
    private static long nextId = 1;
    private static final Map<Object,Reservation> reservations = new HashMap<Object,Reservation>();
    
    public static Reservation remove(Player player) {
        synchronized (reservations) {
            return reservations.remove(player.getName());
        }
    }
    
    /*
    private static void add(Reservation res) {
        synchronized (reservations) {
            reservations.put(res.getKey(), res);
        }
    }
    */
    
    private long localId = nextId++;
    private long remoteId = 0;
    
    private boolean departed = false;
    private boolean arrived = false;
    private boolean cancelled = false;
    
    private EntityType entityType = null;
    private Entity entity = null;
    private int localEntityId = 0;
    private int remoteEntityId = 0;
    
    private Player player = null;
    private String playerName = null;
    private String playerPin = null;
    private String clientAddress = null;
    
    private Vector fromVelocity = null;
    private Vector toVelocity = null;
    private ItemStack[] inventory = null;
    private int health = 0;
    private int remainingAir = 0;
    private int fireTicks = 0;
    private int heldItemSlot = 0;
    private ItemStack[] armor = null;
    
    private Location fromLocation = null;
    private Gate fromGate = null;
    private String fromGateName = null;
    private BlockFace fromGateDirection = null;
    private LocalGate fromGateLocal = null; // only for local gates
    private World fromWorld = null;         // only for local gates
    private Server fromServer = null;       // only for remote gates
    
    private Location toLocation = null;
    private Gate toGate = null;
    private String toGateName = null;
    private BlockFace toGateDirection = null;
    private LocalGate toGateLocal = null;   // only for local gates
    private World toWorld = null;           // only for local gates
    private Server toServer = null;         // only for remote gates
    
    public Reservation(Player player, LocalGate fromGate) throws ReservationException {
        extractPlayer(player);
        extractFromGate(fromGate);
    }
    
    public Reservation(Vehicle vehicle, LocalGate fromGate) throws ReservationException {
        extractVehicle(vehicle);
        extractFromGate(fromGate);
    }
    
    public Reservation(Message in) throws ReservationException {
        remoteId = in.getInt("id");
        try {
            entityType = EntityType.valueOf(in.getString("entityType"));
        } catch (IllegalArgumentException e) {
            throw new ReservationException("unknown entityType '%s'", in.getString("entityType"));
        }
        remoteEntityId = in.getInt("entityId");
        playerName = in.getString("playerName");
        if (playerName != null)
            player = Global.plugin.getServer().getPlayer(playerName);
        playerPin = in.getString("playerPin");
        clientAddress = in.getString("clientAddress");
        fromVelocity = new Vector(in.getDouble("velocityX"), in.getDouble("velocityY"), in.getDouble("velocityZ"));
        fromLocation = new Location(Global.plugin.getServer().getWorlds().get(0), 0, 0, 0, in.getFloat("pitch"), in.getFloat("yaw"));
        inventory = decodeItemStackArray(in.getMessageList("inventory"));
        health = in.getInt("health");
        remainingAir = in.getInt("remainingAir");
        fireTicks = in.getInt("fireTicks");
        heldItemSlot = in.getInt("heldItemSlot");
        armor = decodeItemStackArray(in.getMessageList("armor"));
        
        fromGateName = in.getString("fromGate");
        if (fromGateName != null) {
            fromGate = Global.gates.get(fromGateName);
            if (fromGate == null)
                throw new ReservationException("unknown fromGate '%s'", fromGateName);
            if (fromGate.isSameServer())
                throw new ReservationException("toGate '%s' is not a remote gate", fromGateName);
            try {
                fromGateDirection = BlockFace.valueOf(in.getString("fromGateDirection"));
            } catch (IllegalArgumentException e) {
                throw new ReservationException("unknown fromGateDirection '%s'", in.getString("fromGateDirection"));
            }
            fromServer = Global.servers.get(fromGate.getServerName());
        }
        
        toGateName = in.getString("toGate");
        if (toGateName != null) {
            toGate = Global.gates.get(toGateName);
            if (toGate == null)
                throw new ReservationException("unknown toGate '%s'", toGateName);
            if (! toGate.isSameServer())
                throw new ReservationException("toGate '%s' is not a local gate", toGateName);
            toGateLocal = (LocalGate)toGate;
            toGateDirection = toGateLocal.getDirection();
            toWorld = toGateLocal.getWorld();
        }
    }
    
    private void extractPlayer(Player player) {
        entityType = EntityType.PLAYER;
        entity = player;
        localEntityId = player.getEntityId();
        this.player = player;
        playerName = player.getName();
        playerPin = Teleport.getPin(player);
        clientAddress = player.getAddress().getAddress().getHostAddress();
        health = player.getHealth();
        remainingAir = player.getRemainingAir();
        PlayerInventory inv = player.getInventory();
        inventory = Arrays.copyOf(inv.getContents(), inv.getSize());
        heldItemSlot = inv.getHeldItemSlot();
        armor = inv.getArmorContents();
        fromLocation = player.getLocation();
        fromVelocity = player.getVelocity();
    }
    
    private void extractVehicle(Vehicle vehicle) {
        if (vehicle.getPassenger() instanceof Player)
            extractPlayer((Player)vehicle.getPassenger());

        if (vehicle instanceof Minecart)
            entityType = EntityType.MINECART;
        else if (vehicle instanceof PoweredMinecart)
            entityType = EntityType.POWERED_MINECART;
        else if (vehicle instanceof StorageMinecart) {
            entityType = EntityType.STORAGE_MINECART;
            Inventory inv = ((StorageMinecart)vehicle).getInventory();
            inventory = Arrays.copyOf(inv.getContents(), inv.getSize());
        } else if (vehicle instanceof Boat)
            entityType = EntityType.BOAT;
        else
            throw new IllegalArgumentException("can't create state for " + vehicle.getClass().getName());
        entity = vehicle;
        localEntityId = vehicle.getEntityId();
        fromLocation = vehicle.getLocation();
        fromVelocity = vehicle.getVelocity();
    }

    private void extractFromGate(LocalGate fromGate) throws ReservationException {
        this.fromGate = fromGateLocal = fromGate;
        fromGateName = fromGate.getFullName();
        fromGateDirection = fromGate.getDirection();
        fromWorld = fromGate.getWorld();
        
        try {
            toGate = fromGateLocal.getDestinationGate();
        } catch (GateException e) {
            throw new ReservationException(e.getMessage());
        }
        
        toGateName = toGate.getFullName();
        if (toGate.isSameServer()) {
            toGateLocal = (LocalGate)toGate;
            toWorld = toGateLocal.getWorld();
        } else
            toServer = Global.servers.get(toGate.getServerName());
    }

    public Message encode() {
        Message out = new Message();
        out.put("id", localId);
        out.put("entityType", entityType.toString());
        out.put("entityId", localEntityId);
        out.put("playerName", playerName);
        out.put("playerPin", playerPin);
        out.put("clientAddress", clientAddress);
        out.put("velocityX", fromVelocity.getX());
        out.put("velocityY", fromVelocity.getY());
        out.put("velocityZ", fromVelocity.getZ());
        out.put("pitch", fromLocation.getPitch());
        out.put("yaw", fromLocation.getYaw());
        out.put("inventory", encodeItemStackArray(inventory));
        out.put("health", health);
        out.put("remainingAir", remainingAir);
        out.put("fireTicks", fireTicks);
        out.put("heldItemSlot", heldItemSlot);
        out.put("armor", encodeItemStackArray(armor));
        out.put("fromGate", fromGateName);
        out.put("fromGateDirection", fromGateDirection.toString());
        out.put("toGate", toGateName);
        return out;
    }

    private List<Message> encodeItemStackArray(ItemStack[] isa) {
        if (isa == null) return null;
        List<Message> inv = new ArrayList<Message>();
        for (int slot = 0; slot < isa.length; slot++)
                inv.add(encodeItemStack(isa[slot]));
        return inv;
    }

    private ItemStack[] decodeItemStackArray(List<Message> inv) {
        if (inv == null) return null;
        inventory = new ItemStack[inv.size()];
        for (int slot = 0; slot < inv.size(); slot++)
            inventory[slot] = decodeItemStack(inv.get(slot));
        return inventory;
    }
    
    private Message encodeItemStack(ItemStack stack) {
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
    
    private ItemStack decodeItemStack(Message s) {
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
    
    // called to handle departure on the sending side
    public void depart() throws ReservationException {
        if (departed)
            throw new ReservationException("reservation for %s has already departed", getTraveler());
        departed = true;
        
        Teleport.addGateLock(entity);
        Teleport.addGateLock(player);
        
        checkLocalDepartureGate();
            
        if (toServer == null) {
            // staying on this server
            
            checkLocalArrivalGate();

            prepareDestination();
            if (entity.teleport(toLocation))
                throw new ReservationException("teleport %s to %s failed", getTraveler(), getDestination());
            adjustTraveler();
            
            completeLocalDepartureGate();
            completeLocalArrivalGate();
            
        } else {
            // going to remote server
            
            if (! toServer.isConnected())
                throw new ReservationException("server '%s' is offline", toServer.getName());

            save(this);
            
            try {
                Utils.info("teleporting %s to %s...", getTraveler(), getDestination());
                toServer.doSendReservation(this);
            } catch (ServerException e) {
                Utils.severe(e, "teleport %s to %s failed:", getTraveler(), getDestination());
                cancel(this);
                throw new ReservationException("teleport %s to %s failed", getTraveler(), getDestination());
            }
        }
    }
    
    // called on the receiving side to indicate this reservation has been sent from the sender
    public void receive() {
        // TODO: do local checks and send either a cancel, a confirm (for an simple entity), or just wait for a player join, then subsequent arrive
        
    }
    
    // called on the receiving side to handle a player join
    public void arrive() throws ReservationException {
        if (arrived)
            throw new ReservationException("reservation for %s has already arrived", getTraveler());
        if (cancelled)
            throw new ReservationException("reservation for %s has been cancelled", getTraveler());
        arrived = true;
        
    }
    
    // called on the sending side to indicate an expected arrival never happened on the receiving side
    public void cancel() {
        if (cancelled) return;
        cancelled = true;

        
    }
    
    // called on the sending side to indicate an expeceted arrival arrived on the receiving side
    public void confirm() {
        
    }

    
    
    // called after arrival to get the destination on the local server where the entity arrived
    public Location getToLocation() {
        return toLocation;
    }
    
    

    private void checkLocalDepartureGate() throws ReservationException {
        if (fromGateLocal == null) return;
        
        if (player != null) {
            // player permission
            try {
                Permissions.requirePermission(player, "trp.use." + fromGateLocal.getName());
            } catch (PermissionsException e) {
                throw new ReservationException("not permitted to use this gate");
            }
            // player PIN
            if (fromGateLocal.getRequirePin()) {
                if (playerPin == null)
                    throw new ReservationException("this gate requires a pin");
                if (! fromGateLocal.hasPin(playerPin))
                    throw new ReservationException("this gate rejected your pin");
            }
            // player cost
            double cost = fromGateLocal.getSendCost(toGate);
            if (cost > 0)
                try {
                    Economy.requireFunds(cost);
                } catch (EconomyException e) {
                    throw new ReservationException("this gate requires %s", Economy.format(cost));
                }
            if (toGate != null) {
                cost += toGate.getReceiveCost(fromGateLocal);
                if (cost > 0)
                    try {
                        Economy.requireFunds(cost);
                    } catch (EconomyException e) {
                        throw new ReservationException("total travel cost requires %s", Economy.format(cost));
                    }
            }
        }
        
        // check gate permission
        if ((toGate != null) && Global.config.getBoolean("useGatePermissions", false)) {
            try {
                Permissions.requirePermission(fromGateLocal.getWorldName(), fromGateLocal.getName(), "trp.send." + toGate.getGlobalName());
            } catch (PermissionsException e) {
                throw new ReservationException("this gate is not permitted to send to the remote gate");
            }
        }
    }
    
    private void checkLocalArrivalGate() throws ReservationException {
        if (toGateLocal == null) return;
            
        if (player != null) {
            // player permission
            try {
                Permissions.requirePermission(player, "trp.use." + toGateLocal.getName());
            } catch (PermissionsException e) {
                throw new ReservationException("not permitted to use the remote gate");
            }
            // player PIN
            if (toGateLocal.getRequirePin()) {
                if (playerPin == null)
                    throw new ReservationException("remote gate requires a pin");
                if ((! toGateLocal.hasPin(playerPin)) && toGateLocal.getRequireValidPin())
                    throw new ReservationException("remote gate rejected your pin");
            }
            // player cost
            if (fromServer != null) {
                // only check this side since the departure side already checked itself
                double cost = toGateLocal.getReceiveCost(fromGate);
                if (cost > 0)
                    try {
                        Economy.requireFunds(cost);
                    } catch (EconomyException e) {
                        throw new ReservationException("remote gate requires %s", Economy.format(cost));
                    }
            }
        }

        // check inventory
        // this is only checked on the arrival side
        if ((! toGateLocal.isAcceptableInventory(inventory)) ||
            (! toGateLocal.isAcceptableInventory(armor)))
            throw new ReservationException("remote gate won't allow some inventory items");

        // check gate permission
        if ((fromGate != null) && Global.config.getBoolean("useGatePermissions", false)) {
            try {
                Permissions.requirePermission(toGateLocal.getWorldName(), toGateLocal.getName(), "trp.receive." + fromGateLocal.getGlobalName());
            } catch (PermissionsException e) {
                throw new ReservationException("the remote gate is not permitted to receive from this gate");
            }
        }
            
    }
    
    private void completeLocalDepartureGate() {
        if (fromGateLocal == null) return;

        // TODO: trigger lightning
        
        if (player != null) {
            
            Context ctx = new Context(player);
            
            // player cost
            double cost = fromGateLocal.getSendCost(toGate);
            if (toGate != null)
                cost += toGate.getReceiveCost(fromGateLocal);
            if (cost > 0)
                try {
                    ctx.infoLog("debited %s for travel costs", Economy.deductFunds(cost));
                } catch (EconomyException e) {
                    // too late to do anything useful
                    Utils.warn("unable to debit travel costs for %s: %s", getTraveler(), e.getMessage());
                }
        }
        
    }
            
    private void completeLocalArrivalGate() {
        if (toGateLocal == null) return;

        // TODO: trigger lightning
        
        if (player != null) {
            
            Context ctx = new Context(player);
            
            ctx.sendLog(ChatColor.GOLD + "teleported to '%s'", toGateLocal.getName(ctx));
            
            // player PIN
            if (toGateLocal.getRequirePin() &&
                (! toGateLocal.hasPin(playerPin)) &&
                (! toGateLocal.getRequireValidPin()) &&
                (toGateLocal.getInvalidPinDamage() > 0)) {
                ctx.sendLog("invalid pin");
                player.damage(toGateLocal.getInvalidPinDamage());
            }
            
            // player cost
            if (fromServer != null) {
                // only deduct this side since the departure side already deducted itself
                double cost = toGateLocal.getReceiveCost(fromGate);
                if (cost > 0)
                    try {
                        ctx.sendLog("debited %s for travel costs", Economy.deductFunds(cost));
                    } catch (EconomyException e) {
                        // too late to do anything useful
                        Utils.warn("unable to debit travel costs for %s: %s", getTraveler(), e.getMessage());
                    }
            }
        } else {
            Utils.info("teleported %s to '%s'", getTraveler(), toGateLocal.getFullName());
        }

        // filter inventory
        boolean invFiltered = toGateLocal.filterInventory(inventory);
        boolean armorFiltered = toGateLocal.filterInventory(armor);
        if (invFiltered || armorFiltered) {
            if (player == null)
                Utils.info("some inventory items where filtered by the remote gate");
            else
                (new Context(player)).sendLog("some inventory items where filtered by the remote gate");
        }
    }
    
    
    
    
    
    
    private enum EntityType {
        PLAYER,
        MINECART,
        POWERED_MINECART,
        STORAGE_MINECART,
        BOAT
    }
    
}
