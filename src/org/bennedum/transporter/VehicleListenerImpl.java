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

import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.vehicle.VehicleListener;
import org.bukkit.event.vehicle.VehicleMoveEvent;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class VehicleListenerImpl extends VehicleListener {

    @Override
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        LocalGate fromGate = Global.gates.findGateForPortal(event.getTo());
        if (fromGate == null) {
            Teleport.removeGateLock(vehicle);
            return;
        }
        if (Teleport.isGateLocked(vehicle)) return;
        try {
            Teleport.send(vehicle, fromGate);
        } catch (TeleportException te) {
            if (vehicle.getPassenger() instanceof Player) {
                Context ctx = new Context((Player)vehicle.getPassenger());
                ctx.warnLog(te.getMessage());
            } else
                Utils.warning(te.getMessage());
        }
    }

}
