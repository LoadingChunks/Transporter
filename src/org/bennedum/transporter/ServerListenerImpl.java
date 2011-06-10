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

import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.ServerListener;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class ServerListenerImpl extends ServerListener {

    @Override
    public void onServerCommand(ServerCommandEvent event) {
        // PENDING: bukkit doesn't supply this event yet
        Utils.debug("HOLY COW, I got server command, but which one?");

//        Transporter.instance.saveConfig();
//        ctx.info("saved configuration");
//        Transporter.instance.gates.saveAll(ctx);

    }

}
