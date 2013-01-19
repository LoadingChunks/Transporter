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

import org.bukkit.World;
import org.bukkit.block.BlockFace;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class DesignMatch {

    public Design design;
    public TransformedDesign tDesign;
    public World world;
    public BlockFace direction;

    public DesignMatch(Design design, TransformedDesign tDesign, World world, BlockFace direction) {
        this.design = design;
        this.tDesign = tDesign;
        this.world = world;
        this.direction = direction;
    }

}
