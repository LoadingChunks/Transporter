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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class PlayerState extends EntityState {

    private String name;
    private String ipAddress;
    private String pin;
    private int health;
    private int remainingAir;
    private ItemStack[] inventory;
    private int heldItemSlot;
    private ItemStack[] armor;

    public PlayerState(Player player) {
        super(player);
        name = player.getName();
        ipAddress = player.getAddress().getAddress().toString();
        pin = Teleport.getPin(player);
        health = player.getHealth();
        remainingAir = player.getRemainingAir();
        PlayerInventory inv = player.getInventory();
        inventory = Arrays.copyOf(inv.getContents(), inv.getSize());
        heldItemSlot = inv.getHeldItemSlot();
        armor = inv.getArmorContents();
    }

    public PlayerState(Message in) {
        super(in);
        name = in.getString("name");
        ipAddress = in.getString("ipAddress");
        pin = in.getString("pin");
        health = in.getInt("health");
        remainingAir = in.getInt("remainingAir");
        List<Message> inv = in.getMessageList("inventory");
        inventory = new ItemStack[inv.size()];
        for (int slot = 0; slot < inv.size(); slot++)
            inventory[slot] = decodeItemStack(inv.get(slot));
        heldItemSlot = in.getInt("heldItemSlot");
        List<Message> armr = in.getMessageList("armor");
        armor = new ItemStack[armr.size()];
        for (int slot = 0; slot < armr.size(); slot++)
            armor[slot] = decodeItemStack(armr.get(slot));
    }

    @Override
    public Message encode() {
        Message out = super.encode();
        out.put("entityType", "player");
        out.put("name", name);
        out.put("ipAddress", ipAddress);
        out.put("pin", pin);
        out.put("health", health);
        out.put("remainingAir", remainingAir);
        List<Message> inv = new ArrayList<Message>();
        for (int slot = 0; slot < inventory.length; slot++)
            inv.add(encodeItemStack(inventory[slot]));
        out.put("inventory", inv);
        out.put("heldItemSlot", heldItemSlot);
        List<Message> armr = new ArrayList<Message>();
        for (int slot = 0; slot < armor.length; slot++)
            armr.add(encodeItemStack(armor[slot]));
        out.put("armor", armr);
        return out;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getPin() {
        return pin;
    }

    public String getIPAddress() {
        return ipAddress;
    }

    public ItemStack[] getInventory() {
        return inventory;
    }

    public ItemStack[] getArmor() {
        return armor;
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public PlayerState getPlayerState() {
        return this;
    }

    @Override
    public Entity restore(Location location, Vector velocity, Player player) {
        super.restore(location, velocity, player);
        applyTo(player);
        return player;
    }

    @Override
    protected void applyTo(Entity entity) {
        Player player = (Player)entity;
        super.applyTo(player);
        player.setVelocity(new Vector(0, 0, 0));
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot <  inventory.length; slot++)
            inv.setItem(slot, inventory[slot]);
        // PENDING: This doesn't work as expected. it replaces whatever's
        // in slot 0 with whatever's in the held slot. There doesn't appear to
        // be a way to change just the slot of the held item

        //inv.setItemInHand(inv.getItem(heldItemSlot));

         inv.setArmorContents(armor);
    }

}
