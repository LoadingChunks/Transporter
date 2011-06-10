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

import org.bukkit.block.BlockFace;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class ExpectedPlayer {

    private String fromGateName;
    private String toGateName;
    private BlockFace fromGateDirection;
    private EntityState entityState;
    private boolean arrived = false;
    private boolean cancelled = false;

    public ExpectedPlayer(EntityState entityState, String fromGateName, String toGateName, BlockFace fromGateDirection) {
        this.entityState = entityState;
        this.fromGateName = fromGateName;
        this.toGateName = toGateName;
        this.fromGateDirection = fromGateDirection;
    }

    public boolean hasArrived() {
        return arrived;
    }

    public void setArrived() {
        arrived = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled() {
        cancelled = true;
    }

    public String getFromGateName() {
        return fromGateName;
    }

    public String getToGateName() {
        return toGateName;
    }

    public EntityState getEntityState() {
        return entityState;
    }

    public PlayerState getPlayerState() {
        return entityState.getPlayerState();
    }

    public BlockFace getFromGateDirection() {
        return fromGateDirection;
    }

}
