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
import org.bukkit.command.Command;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public abstract class CommandProcessor {

    public abstract boolean matches(Command cmd, List<String> args);
    public boolean requiresPlayer() { return false; }
    public boolean requiresOp() { return false; }
    public boolean requiresConsole() { return false; }
    public abstract String getUsage(Context ctx);
    public abstract void process(Context ctx, Command cmd, List<String> args) throws TransporterException;
    
}
