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
package org.bennedum.transporter.config;

import java.io.*;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class Configuration extends ConfigurationNode {
    
    private File file = null;
    
    public Configuration(File file) {
        if (file == null)
            throw new IllegalArgumentException("file is required");
        this.file = file;
    }
    
    public File getFile() {
        return file;
    }
    
    public void load() {
        clear();
        try {
            InputStream input = new FileInputStream(file);
            Yaml yaml = new Yaml();
            Object o = yaml.load(input);
            if (! (o instanceof Map)) return;
            for (Object k : ((Map)o).keySet())
                setProperty(k.toString(), ((Map)o).get(k));
        } catch (IOException e) {}
    }        

    public void save() {
        DumperOptions options = new DumperOptions();
        //options.setAllowUnicode(true);
        options.setIndent(4);
        Yaml yaml = new Yaml(options);
        try {
            OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
            yaml.dump(this, writer);
            writer.close();
        } catch (IOException e) {}
    }
    
}
