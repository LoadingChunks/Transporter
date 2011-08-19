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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Designs {

    private static final Map<String,Design> designs = new HashMap<String,Design>();

    public static void load(Context ctx) {
        designs.clear();
        File designsFolder = new File(Global.plugin.getDataFolder(), "designs");
        for (File designFile : Utils.listYAMLFiles(designsFolder)) {
            try {
                Design design = new Design(designFile);
                try {
                    add(design);
                    ctx.sendLog("loaded design '%s'", design.getName());
                } catch (DesignException de) {
                    ctx.warnLog("unable to load design '%s': %s", design.getName(), de.getMessage());
                }
            } catch (TransporterException de) {
                ctx.warnLog("'%s' contains an invalid design: %s", designFile.getPath(), de.getMessage());
            }
        }
        if (isEmpty())
            ctx.sendLog("no designs loaded");
    }

    private static void add(Design design) throws DesignException {
        if (designs.containsKey(design.getName()))
            throw new DesignException("a design with the same type already exists");
        designs.put(design.getName(), design);
    }

    public static Design get(String name) {
        if (designs.containsKey(name)) return designs.get(name);
        Design design = null;
        name = name.toLowerCase();
        for (String key : designs.keySet()) {
            if (key.toLowerCase().startsWith(name)) {
                if (design == null) design = designs.get(key);
                else return null;
            }
        }
        return design;
    }

    public static List<Design> getAll() {
        return new ArrayList<Design>(designs.values());
    }

    public static boolean isEmpty() {
        return size() == 0;
    }

    public static int size() {
        return designs.size();
    }

    public static LocalGate create(Context ctx, Location location, String gateName) throws TransporterException {
        for (Design design : designs.values()) {
            LocalGate gate = design.create(location, gateName, ctx.getPlayer().getName());
            if (gate == null) continue;

            Permissions.require(ctx.getPlayer(), "trp.create." + gate.getDesignName());
            Gates.add(gate);
            try {
                if (Economy.deductFunds(ctx.getPlayer(), design.getCreateCost()))
                    ctx.sendLog("debited %s for gate creation", Economy.format(design.getCreateCost()));
            } catch (EconomyException e) {
                Gates.destroy(gate, false);
                throw e;
            }
            gate.saveInBackground();
            return gate;
        }
        return null;
    }

}
