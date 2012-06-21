/*
 * Copyright 2012 frdfsnlght <frdfsnlght@gmail.com>.
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
package com.frdfsnlght.transporter;

import java.util.HashMap;
import java.util.Map;
import com.frdfsnlght.transporter.api.RemotePlayer;
import org.bukkit.entity.Player;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Players {

    public static Player findLocal(String name) {
        Player player = Global.plugin.getServer().getPlayer(name);
        if (player != null) return player;
        name = name.toLowerCase();
        for (Player p : Global.plugin.getServer().getOnlinePlayers())
            if (p.getName().toLowerCase().startsWith(name)) {
                if (player != null) return null;
                player = p;
            }
        return player;
    }

    public static RemotePlayerImpl findRemote(String name) {
        Map<String,RemotePlayerImpl> players = new HashMap<String,RemotePlayerImpl>();
        for (Server server : Servers.getAll())
            for (RemotePlayer p : server.getRemotePlayers())
                players.put(p.getName().toLowerCase(), (RemotePlayerImpl)p);
        name = name.toLowerCase();
        if (players.containsKey(name)) return players.get(name);
        RemotePlayerImpl player = null;
        for (String pname : players.keySet())
            if (pname.startsWith(name)) {
                if (player != null) return null;
                player = players.get(pname);
            }
        return player;
    }

}
