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

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 *
 * @author Thomas Bennedum <frdfsnlght@gmail.com>
 */
public final class EntityListenerImpl implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDamage(EntityDamageEvent event) {
        if (ReservationImpl.isGateLocked(event.getEntity()))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity deadEnt = event.getEntity();
        if (! (deadEnt.getLastDamageCause() instanceof EntityDamageByEntityEvent)) return;
        
        EntityDamageByEntityEvent killEvent = (EntityDamageByEntityEvent) deadEnt.getLastDamageCause();
        Entity killerEnt = killEvent.getDamager();
        if (killEvent.getCause() == DamageCause.PROJECTILE)
            killerEnt = ((Projectile)killerEnt).getShooter();

        if (! (killerEnt instanceof Player)) return;
        
        if (deadEnt instanceof Player)
            Realm.onPlayerKill((Player)killerEnt, (Player)deadEnt);
        else
            Realm.onMobKill((Player)killerEnt, deadEnt);
    }
    
}
