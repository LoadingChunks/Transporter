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
public enum SpawnDirection {

    NORTH,
    SOUTH,
    EAST,
    WEST,
    NORTH_EAST,
    NORTH_WEST,
    SOUTH_EAST,
    SOUTH_WEST,
    RANDOM,
    PLAYER,
    FORWARD,
    REVERSE;

    public SpawnDirection rotate(BlockFace direction) {
        switch (this) {
            case RANDOM:
            case PLAYER:
            case FORWARD:
            case REVERSE:
                return this;
            default:
                return SpawnDirection.fromFacing(Utils.rotate(toFacing(), direction));
        }
    }

    public float calculateYaw(float playerYaw, BlockFace fromGateDirection, BlockFace toGateDirection) {
        switch (this) {
            case RANDOM:
                return (float)((Math.random() * 360) - 180);
            case PLAYER:
                return playerYaw;
            case FORWARD:
            case REVERSE:
                float yawDiff = playerYaw - Utils.directionToYaw(fromGateDirection);
                float newYaw = yawDiff + Utils.directionToYaw(toGateDirection);
                newYaw += (this == FORWARD) ? 0 : 180;
                while (newYaw > 180) newYaw -= 360;
                while (newYaw <= -180) newYaw += 360;
                return newYaw;
            default:
                return Utils.directionToYaw(toFacing());
        }
    }

    public BlockFace toFacing() {
        try {
            return Utils.valueOf(BlockFace.class, toString());
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    public static SpawnDirection fromFacing(BlockFace facing) {
        if (facing == null) return null;
        return Utils.valueOf(SpawnDirection.class, facing.toString());
    }

}
