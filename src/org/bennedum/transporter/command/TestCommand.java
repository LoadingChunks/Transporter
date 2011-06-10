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
package org.bennedum.transporter.command;

import java.util.List;
import org.bennedum.transporter.Context;
import org.bennedum.transporter.TransporterException;
import org.bennedum.transporter.net.Cipher;
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class TestCommand extends TrpCommandProcessor {


//    @Override
//    public boolean requiresPlayer() { return true; }

    @Override
    protected String[] getSubCommands() { return new String[] {"test"}; }

    @Override
    public String getUsage(Context ctx) {
        return super.getUsage(ctx);
    }

    @Override
    public void process(Context ctx, Command cmd, List<String> args) throws TransporterException {
        super.process(ctx, cmd, args);

        /*
        Cipher cipher = new Cipher(1024);
        byte[] key = "foo".getBytes();
        
        cipher.initEncrypt(key);
        byte[] enc = cipher.doFinal(args.get(0).getBytes());
        cipher.initDecrypt(key);
        byte[] dec = cipher.doFinal(enc);
        
        String out = new String(dec);
        System.out.println("decrypted: " + out);
        */
        
        /*
        String msg = args.get(0);
        if (conn == null) {
            conn = new OutgoingConnection(new InetSocketAddress("localhost", 25556));
            conn.connect();
            System.out.println("connect submitted");
        }
        conn.send(msg);
         */

        /*
        String player = args.get(0);
        String perm = args.get(1);
        if (Permissions.has(player, perm))
            System.out.println("permitted");
        else
            System.out.println("denied");
          */

        /*
        Location cLoc = ctx.getPlayer().getLocation();
        Location loc = new Location(cLoc.getWorld(), cLoc.getBlockX(), cLoc.getBlockY(), cLoc.getBlockZ());
        //loc.setX(loc.getX() + 0.5);
        //loc.setZ(loc.getZ() + 0.5);
        loc.setPitch(cLoc.getPitch());
        loc.setYaw(cLoc.getYaw());
Utils.info("From:");
Utils.dumpBlockLocation(cLoc);
Utils.info("To:");
Utils.dumpBlockLocation(loc);
        if (! ctx.getPlayer().teleport(loc))
            throw new CommandException("teleport failed");
        else
            ctx.send(ChatColor.GOLD + "whoosh");
         */
    }

}
