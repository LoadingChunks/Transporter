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
    private static final Map<Long,Reservation> reservations = new HashMap<Long,Reservation>();

    public static Reservation get(long id) {
        synchronized (reservations) {
            return reservations.get(id);
        }
    }
    
    private long localId = nextId++;
    private long remoteId = 0;
    
    private EntityType entityType = null;
    private Entity entity = null;
    private int localEntityId = 0;
    private int remoteEntityId = 0;
    
    private Player player = null;
    private String playerName = null;
    private String playerPin = null;
    private String clientAddress = null;
    
    private ItemStack[] inventory = null;
    private int health = 0;
    private int remainingAir = 0;
    private int fireTicks = 0;
    private int heldItemSlot = 0;
    private ItemStack[] armor = null;
    
    private Location fromLocation = null;
    private Vector fromVelocity = null;
    private Gate fromGate = null;
    private String fromGateName = null;
    private BlockFace fromGateDirection = null;
    private LocalGate fromGateLocal = null; // local gate
    private World fromWorld = null;         // local gate
    private Server fromServer = null;       // remote gate
    
    private Location toLocation = null;
    private Vector toVelocity = null;
    private Gate toGate = null;
    private String toGateName = null;
    private BlockFace toGateDirection = null;
    private LocalGate toGateLocal = null;   // local gate
    private String toWorldName = null;
    private World toWorld = null;           // local gate
    private Server toServer = null;         // remote gate
    
    private boolean createdEntity = false;
    
    // player stepping into gate
    public Reservation(Player player, LocalGate fromGate) throws ReservationException {
        extractPlayer(player);
        extractFromGate(fromGate);
    }
    
    // vehicle moving into gate
    public Reservation(Vehicle vehicle, LocalGate fromGate) throws ReservationException {
        extractVehicle(vehicle);
        extractFromGate(fromGate);
    }

    // player direct to location on this server
    public Reservation(Player player, Location location) throws ReservationException {
        extractPlayer(player);
        toLocation = location;
    }
    
    // player direct to remote server, default world, spawn location
    public Reservation(Player player, Server server) throws ReservationException {
        extractPlayer(player);
        toServer = server;
    }
    
    // player direct to remote server, specified world, spawn location
    public Reservation(Player player, Server server, String worldName) throws ReservationException {
        extractPlayer(player);
        toServer = server;
        toWorldName = worldName;
    }
    
    // player direct to remote server, specified world, specified location
    public Reservation(Player player, Server server, String worldName, double x, double y, double z) throws ReservationException {
        extractPlayer(player);
        toServer = server;
        toWorldName = worldName;
        toLocation = new Location(null, x, y, z);
    }
    
    // reception of reservation from sending server
    public Reservation(Message in, Server server) throws ReservationException {
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
        fromLocation = new Location(null, in.getDouble("fromX"), in.getDouble("fromY"), in.getDouble("fromZ"), in.getFloat("pitch"), in.getFloat("yaw"));
        fromVelocity = new Vector(in.getDouble("velX"), in.getDouble("velY"), in.getDouble("velZ"));
        inventory = decodeItemStackArray(in.getMessageList("inventory"));
        health = in.getInt("health");
        remainingAir = in.getInt("remainingAir");
        fireTicks = in.getInt("fireTicks");
        heldItemSlot = in.getInt("heldItemSlot");
        armor = decodeItemStackArray(in.getMessageList("armor"));
        
        fromServer = server;
        
        if (in.get("toX") != null)
            toLocation = new Location(null, in.getDouble("toX"), in.getDouble("toY"), in.getDouble("toZ"));
        
        toWorldName = in.getString("toWorldName");
        if (toWorldName != null) {
            toWorld = Global.plugin.getServer().getWorld(toWorldName);
            if (toWorld == null)
                throw new ReservationException("unknown world '%s'", toWorldName);
        }
        
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
            toWorldName = toWorld.getName();
            if (fromGateDirection == null)
                fromGateDirection = toGateDirection;
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
        fireTicks = player.getFireTicks();
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
        fireTicks = vehicle.getFireTicks();
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
        out.put("velX", fromVelocity.getX());
        out.put("velY", fromVelocity.getY());
        out.put("velZ", fromVelocity.getZ());
        out.put("fromX", fromLocation.getX());
        out.put("fromY", fromLocation.getY());
        out.put("fromZ", fromLocation.getZ());
        out.put("fromPitch", fromLocation.getPitch());
        out.put("fromYaw", fromLocation.getYaw());
        out.put("inventory", encodeItemStackArray(inventory));
        out.put("health", health);
        out.put("remainingAir", remainingAir);
        out.put("fireTicks", fireTicks);
        out.put("heldItemSlot", heldItemSlot);
        out.put("armor", encodeItemStackArray(armor));
        out.put("fromGate", fromGateName);
        out.put("fromGateDirection", fromGateDirection.toString());
        out.put("toGate", toGateName);
        out.put("toWorldName", toWorldName);
        if (toLocation != null) {
            out.put("toX", toLocation.getX());
            out.put("toY", toLocation.getY());
            out.put("toZ", toLocation.getZ());
        }
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
        synchronized (reservations) {
            reservations.put(localId, this);
        }
        
        Teleport.addGateLock(entity);
        Teleport.addGateLock(player);
        
        checkLocalDepartureGate();
            
        if (toServer == null) {
            // staying on this server
            checkLocalArrivalGate();
            arrive();
            completeLocalDepartureGate();
            
        } else {
            // going to remote server
            try {
                Utils.info("sending reservation for %s to %s...", getTraveler(), getDestination());
                toServer.doSendReservation(this);
            } catch (ServerException e) {
                Utils.severe(e, "reservation send for %s to %s failed:", getTraveler(), getDestination());
                synchronized (reservations) {
                    reservations.remove(localId);
                }
                throw new ReservationException("teleport %s to %s failed", getTraveler(), getDestination());
            }
        }
    }
    
    // called on the receiving side to indicate this reservation has been sent from the sender
    public void receive() {
        try {
            Utils.info("received reservation for %s to %s from %s...", getTraveler(), getDestination(), fromServer.getName());
            checkLocalArrivalGate();
            synchronized (reservations) {
                reservations.put(localId, this);
            }
            try {
                fromServer.doReservationApproved(remoteId);
            } catch (ServerException e) {
                Utils.severe(e, "send reservation approval for %s to %s to %s failed:", getTraveler(), getDestination(), fromServer.getName());
                synchronized (reservations) {
                    reservations.remove(localId);
                }
                return;
            }
            
            Utils.info("reservation for %s to %s approved", getTraveler(), getDestination());

            if (playerName == null) {
                // there's no player coming, so handle the "arrival" now
                Utils.fire(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            arrive();
                        } catch (ReservationException e) {
                            Utils.warning("reservation arrival for %s to %s to %s failed:", getTraveler(), getDestination(), fromServer.getName(), e.getMessage());
                        }
                        
                    }
                });
            }
                
        } catch (ReservationException e) {
            Utils.info("reservation for %s to %s denied: %s", getTraveler(), getDestination(), e.getMessage());
            synchronized (reservations) {
                reservations.remove(localId);
            }
            try {
                fromServer.doReservationDenied(remoteId, e.getMessage());
            } catch (ServerException e2) {
                Utils.severe(e, "send reservation denial for %s to %s to %s failed:", getTraveler(), getDestination(), fromServer.getName());
            }
        }
    }

    // called on the receiving side to handle arrival
    public void arrive() throws ReservationException {
        synchronized (reservations) {
            reservations.remove(localId);
        }
        prepareDestination();
        prepareTraveler();
        Teleport.addGateLock(entity);
        Teleport.addGateLock(player);
        if (! entity.teleport(toLocation)) {
            rollbackTraveler();
            throw new ReservationException("teleport %s to %s failed", getTraveler(), getDestination());
        }
        commitTraveler();

        Utils.info("teleported %s to %s", getTraveler(), getDestination());

        completeLocalArrivalGate();

        if (fromServer == null)
            arrived();
        else
            try {
                fromServer.doReservationArrived(remoteId);
            } catch (ServerException e) {
                Utils.severe(e, "send reservation arrival for %s to %s to %s failed:", getTraveler(), getDestination(), fromServer.getName());
            }
    }
    
    // called on the sending side to confirm reception of the valid reservation on the receiving side
    public void approved() {
        Utils.info("reservation to send %s to %s was approved", getTraveler(), getDestination());
        
        if (player != null) {
            Context ctx = new Context(player);
            ctx.send(ChatColor.GOLD + "teleporting to '%s'...", toGateName);

            completeLocalDepartureGate();
            
            String mcAddress = toServer.getMCAddressForClient(player.getAddress());
            if (mcAddress == null) {
                Utils.warning("minecraft address for '%s' is null?", toServer.getName());
                return;
            }
            String[] addrParts = mcAddress.split("/");
            if (addrParts.length == 1) {
                // this is a client based reconnect
                Utils.info("sending player '%s' to '%s' via client reconnect", player.getName(), addrParts[0]);
                player.kickPlayer("[Redirect] please reconnect to: " + addrParts[0]);
            } else {
                // this is a proxy based reconnect
                Utils.info("sending player '%s' to '%s,%s' via proxy reconnect", player.getName(), addrParts[0], addrParts[1]);
                player.kickPlayer("[Redirect] please reconnect to: " + addrParts[0] + "," + addrParts[1]);
            }
        }
        if ((entity != null) && (entity != player))
            entity.remove();
    }
    
    // called on the sending side to indicate a reservation was denies by the receiving side
    public void denied(final String reason) {
        if (player == null)
            Utils.warning("reservation to send %s to %s was denied: %s", getTraveler(), getDestination(), reason);
        else
            Utils.fire(new Runnable() {
                @Override
                public void run() {
                    Context ctx = new Context(player);
                    ctx.warn(reason);
                }
            });
    }
    
    // called on the sending side to indicate an expeceted arrival arrived on the receiving side
    public void arrived() {
        synchronized (reservations) {
            reservations.remove(localId);
        }
        Utils.info("reservation to send %s to %s was completed", getTraveler(), getDestination());
    }

    // called on the sending side to indicate an expected arrival never happened on the receiving side
    public void timeout() {
        synchronized (reservations) {
            reservations.remove(localId);
        }
        Utils.warning("reservation to send %s to %s timed out", getTraveler(), getDestination());
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
                    Economy.requireFunds(player, cost);
                } catch (EconomyException e) {
                    throw new ReservationException("this gate requires %s", Economy.format(cost));
                }
            if (toGate != null) {
                cost += toGate.getReceiveCost(fromGateLocal);
                if (cost > 0)
                    try {
                        Economy.requireFunds(player, cost);
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
                        Economy.requireFunds(player, cost);
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
                    Economy.deductFunds(player, cost);
                    ctx.send("debited %s for travel costs", Economy.format(cost));
                } catch (EconomyException e) {
                    // too late to do anything useful
                    Utils.warning("unable to debit travel costs for %s: %s", getTraveler(), e.getMessage());
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
                        Economy.deductFunds(player, cost);
                        ctx.sendLog("debited %s for travel costs", Economy.format(cost));
                    } catch (EconomyException e) {
                        // too late to do anything useful
                        Utils.warning("unable to debit travel costs for %s: %s", getTraveler(), e.getMessage());
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
    
    private void prepareDestination() {
        if (toGateLocal != null) {
            GateBlock block = toGateLocal.getSpawnBlocks().randomBlock();
            toLocation = block.getLocation().clone();
            toLocation.add(0.5, 0, 0.5);
            toLocation.setYaw(block.getDetail().getSpawn().calculateYaw(fromLocation.getYaw(), fromGateDirection, toGateLocal.getDirection()));
            toLocation.setPitch(fromLocation.getPitch());
            toVelocity = fromVelocity.clone();
            Utils.rotate(toVelocity, fromGateDirection, toGateLocal.getDirection());
            Utils.prepareChunk(toLocation);
            return;
        }
        if (toLocation == null) {
            if (toWorld == null)
                toWorld = Global.plugin.getServer().getWorlds().get(0);
            toLocation = toWorld.getSpawnLocation();
        } else if (toLocation.getWorld() == null)
            toLocation.setWorld(Global.plugin.getServer().getWorlds().get(0));
        toLocation.setYaw(fromLocation.getYaw());
        toLocation.setPitch(fromLocation.getPitch());
        toVelocity = fromVelocity.clone();
        Utils.prepareChunk(toLocation);
    }
    
    private void prepareTraveler() throws ReservationException {
        if ((player == null) && (playerName != null)) {
            player = Global.plugin.getServer().getPlayer(playerName);
            if (player == null)
                throw new ReservationException("player '%s' not found", playerName);
        }
        if (entity == null) {
            switch (entityType) {
                case PLAYER:
                    entity = player;
                    break;
                case MINECART:
                    entity = toLocation.getWorld().spawn(toLocation, Minecart.class);
                    createdEntity = true;
                    if (player != null)
                        ((Minecart)entity).setPassenger(player);
                    break;
                case POWERED_MINECART:
                    entity = toLocation.getWorld().spawn(toLocation, PoweredMinecart.class);
                    createdEntity = true;
                    break;
                case STORAGE_MINECART:
                    entity = toLocation.getWorld().spawn(toLocation, StorageMinecart.class);
                    createdEntity = true;
                    break;
                case BOAT:
                    entity = toLocation.getWorld().spawn(toLocation, Boat.class);
                    createdEntity = true;
                    break;
                default:
                    throw new ReservationException("unknown entity type '%s'", entityType);
            }
        }
        switch (entityType) {
            case PLAYER:
                player.setHealth(health);
                player.setFireTicks(fireTicks);
                player.setRemainingAir(remainingAir);
                player.setVelocity(toVelocity);
                if (inventory != null) {
                    PlayerInventory inv = player.getInventory();
                    for (int slot = 0; slot <  inventory.length; slot++)
                        inv.setItem(slot, inventory[slot]);
                    // PENDING: This doesn't work as expected. it replaces whatever's
                    // in slot 0 with whatever's in the held slot. There doesn't appear to
                    // be a way to change just the slot of the held item
                    //inv.setItemInHand(inv.getItem(heldItemSlot));
                }
                if (armor != null) {
                    PlayerInventory inv = player.getInventory();
                    inv.setArmorContents(armor);
                }
                break;
            case MINECART:
                player.setFireTicks(fireTicks);
                entity.setVelocity(toVelocity);
                if (player != null)
                    entity.setPassenger(player);
                break;
            case POWERED_MINECART:
                player.setFireTicks(fireTicks);
                entity.setVelocity(toVelocity);
                break;
            case STORAGE_MINECART:
                player.setFireTicks(fireTicks);
                entity.setVelocity(toVelocity);
                if (inventory != null) {
                    StorageMinecart mc = (StorageMinecart)entity;
                    Inventory inv = mc.getInventory();
                    for (int slot = 0; slot <  inventory.length; slot++)
                        inv.setItem(slot, inventory[slot]);
                }
                break;
            case BOAT:
                player.setFireTicks(fireTicks);
                entity.setVelocity(toVelocity);
                if (player != null)
                    entity.setPassenger(player);
                break;
        }
    }
    
    private void rollbackTraveler() {
        if (createdEntity)
            entity.remove();
    }
    
    private void commitTraveler() {
        // TODO: something?
    }
    
    public String getTraveler() {
        if (entityType == EntityType.PLAYER)
            return String.format("player '%s'", playerName);
        if (playerName == null)
            return entityType.toString();
        return String.format("player '%s' as a passenger on a %s", playerName, entityType);
    }
    
    public String getDestination() {
        if (toGateName != null)
            return "'" + toGateName + "'";
        String dst;
        if (toServer != null)
            dst = String.format("server '%s'", toServer.getName());
        else if (toWorld != null)
            dst = String.format("world '%s'", toWorld.getName());
        else
            dst = "unknown";
        if (toLocation != null)
            dst += String.format(" @ %s,%s,%s", toLocation.getBlockX(), toLocation.getBlockY(), toLocation.getBlockZ());
        return dst;
    }
    
    private enum EntityType {
        PLAYER,
        MINECART,
        POWERED_MINECART,
        STORAGE_MINECART,
        BOAT
    }
    
}
