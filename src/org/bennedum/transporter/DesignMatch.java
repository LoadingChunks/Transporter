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
package org.bennedum.transporter;

import java.util.List;
import org.bukkit.World;
import org.bukkit.block.BlockFace;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class DesignMatch {
    
    public Design design;
    public List<GateBlock> gateBlocks;
    public World world;
    public BlockFace direction;
    
    public DesignMatch(Design design, List<GateBlock> gateBlocks, World world, BlockFace direction) {
        this.design = design;
        this.gateBlocks = gateBlocks;
        this.world = world;
        this.direction = direction;
    }
    
}
