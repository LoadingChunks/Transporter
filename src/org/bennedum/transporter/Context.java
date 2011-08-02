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

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class Context {

    private static final ChatColor HL_ON = ChatColor.DARK_PURPLE;
    private static final ChatColor HL_OFF = ChatColor.WHITE;

    private CommandSender sender = null;

    public Context() {}

    public Context(CommandSender sender) {
        this.sender = sender;
    }

    public CommandSender getSender() {
        return sender;
    }

    public void send(String msg, Object ... args) {
        msg = String.format(msg, args);
        if (sender == null)
            Utils.info(msg);
        else if (isPlayer())
            sender.sendMessage(HL_ON + "[" + Global.pluginName + "] " + HL_OFF + msg);
        else {
            msg = ChatColor.stripColor(msg);
            sender.sendMessage("[" + Global.pluginName + "] " + msg);
        }
    }

    public void sendLog(String msg, Object ... args) {
        send(msg, args);
        if (! isPlayer()) return;
        msg = String.format(msg, args);
        Utils.info("->[%s] %s", ((Player)sender).getName(), msg);
    }

    public void warn(String msg, Object ... args) {
        msg = String.format(msg, args);
        if (sender == null)
            Utils.warning(msg);
        else
            sender.sendMessage(HL_ON + "[" + Global.pluginName + "] " + ChatColor.RED + msg);
    }

    public void warnLog(String msg, Object ... args) {
        warn(msg, args);
        if (! isPlayer()) return;
        msg = String.format(msg, args);
        Utils.warning("->[%s] %s", ((Player)sender).getName(), msg);
    }

    public boolean isPlayer() {
        return sender instanceof Player;
    }

    public boolean isConsole() {
        return (sender != null) && (! (sender instanceof Player));
    }

    public boolean isSystem() {
        return sender == null;
    }

    public boolean isHuman() {
        return sender != null;
    }

    public boolean isOp() {
        return isConsole() || (isPlayer() && ((Player)sender).isOp());
    }

    public Player getPlayer() {
        if (! isPlayer()) return null;
        return (Player)sender;
    }

    // TODO: remove
    /*
    public boolean hasAllPermissions(String ... perms) {
        try {
            requireAllPermissions(perms);
            return true;
        } catch (PermissionsException pe) {
            return false;
        }
    }

    public void requireAllPermissions(String ... perms) throws PermissionsException {
        requirePermissions(true, perms);
    }

    public void requireAnyPermissions(String ... perms) throws PermissionsException {
        requirePermissions(false, perms);
    }

    private void requirePermissions(boolean requireAll, String ... perms) throws PermissionsException {
        if (isOp() || isSystem() || (! isPlayer())) return;
        Player player = getPlayer();
        Permissions.requirePermissions(player, requireAll, perms);
    }
*/
    
    // TODO: remove
    /*
    public void requireFunds(double amount) throws FundsException {
        if (isOp() || isSystem() || (! isPlayer())) return;
        Player player = getPlayer();
        Utils.deductFunds(player.getName(), amount, true);
    }

    public void chargeFunds(double amount, String msg, Object ... args) throws FundsException {
        if (isOp() || isSystem() || (! isPlayer())) return;
        Player player = getPlayer();
        if (Utils.deductFunds(player.getName(), amount, false)) {
            msg = msg.replace("$$", iConomy.format(amount));
            sendLog(msg, args);
        }
    }
*/
    // TODO: remove
    /*
    public void requireInventory(Map<Material,Integer> blocks) throws InventoryException {
        if (isSystem() || (! isPlayer())) return;
        Player player = getPlayer();
        if (player == null) return;
        Utils.deductInventory(player, blocks, true);
    }

    public void chargeInventory(Map<Material,Integer> blocks) throws InventoryException {
        chargeInventory(blocks, null);
    }

    public void chargeInventory(Map<Material,Integer> blocks, String msg, Object ... args) throws InventoryException {
        if (isSystem() || (! isPlayer())) return;
        Player player = getPlayer();
        if (player == null) return;
        if (Utils.deductInventory(player, blocks, false) && (msg != null))
            sendLog(msg, args);
    }
*/
    
}
