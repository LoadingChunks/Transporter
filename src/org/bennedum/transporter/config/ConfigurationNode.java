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

import java.util.*;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class ConfigurationNode extends HashMap<String,Object> {

    public ConfigurationNode() {}

    @SuppressWarnings("unchecked")
    private static Object convertValue(Object val) {
        if (val == null) return null;
        if (val instanceof ConfigurationNode) {
            for (String k : ((ConfigurationNode)val).keySet())
                ((ConfigurationNode)val).put(k, convertValue(((ConfigurationNode)val).get(k)));
            return val;
        }
        if (val instanceof Map) {
            ConfigurationNode child = new ConfigurationNode();
            for (Object k : ((Map)val).keySet())
                child.put(k.toString(), convertValue(((Map)val).get(k)));
            return child;
        }
        if (val instanceof List) {
            for (int i = 0; i < ((List)val).size(); i++)
                ((List)val).set(i, convertValue(((List)val).get(i)));
            return val;
        }
        if (val instanceof Collection) {
            Object[] vals = ((Collection)val).toArray();
            ((Collection)val).clear();
            for (Object v : vals)
                ((Collection)val).add(convertValue(v));
            return val;
        }
        return val;
    }
    
    public void setProperty(String key, Object val) {
        String[] keyParts = splitKey(key);
        if (keyParts.length == 1) {
            put(key, convertValue(val));
            return;
        }
        ConfigurationNode child = getNode(keyParts[0]);
        if (child == null) {
            child = new ConfigurationNode();
            put(keyParts[0], child);
        }
        child.setProperty(keyParts[1], val);
    }

    public void removeProperty(String key) {
        String[] keyParts = splitKey(key);
        if (keyParts.length == 1) {
            this.remove(key);
            return;
        }
        ConfigurationNode child = getNode(keyParts[0]);
        if (child == null) return;
        child.removeProperty(keyParts[1]);
    }

    public List<String> getKeys() {
        return new ArrayList<String>(keySet());
    }
    
    public List<String> getKeys(String key) {
        String[] keyParts = splitKey(key);
        ConfigurationNode child = getNode(keyParts[0]);
        if (child == null) return new ArrayList<String>();
        if (keyParts.length == 1)
            return child.getKeys();
        return child.getKeys(keyParts[1]);
    }
    
    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        String[] keyParts = splitKey(key);
        Object child = get(keyParts[0]);
        if (child == null) return new ArrayList<Object>();
        if (keyParts.length == 1) {
            if (child instanceof Collection)
                return new ArrayList<Object>((Collection)child);
            return new ArrayList<Object>();
        }
        if (child instanceof ConfigurationNode)
            return ((ConfigurationNode)child).getList(keyParts[1]);
        return new ArrayList<Object>();
    }
    
    private String[] splitKey(String key) {
        int pos = key.indexOf(".");
        if (pos == -1) return new String[] { key };
        return new String[] { key.substring(0, pos), key.substring(pos + 1) };
    }
    
    public Object get(String key) {
        return get(key, null);
    }
    
    public Object get(String key, Object def) {
        String[] keyParts = splitKey(key);
        if (keyParts.length == 1) {
            if (containsKey(key))
                return super.get(key);
            else
                return def;
        }
        ConfigurationNode child = getNode(keyParts[0]);
        if (child == null) return def;
        return child.get(keyParts[1], def);
    }

    public String getString(String key) {
        return getString(key, null);
    }

    public String getString(String key, String def) {
        Object o = get(key, def);
        if (o == null) return null;
        return o.toString();
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public boolean getBoolean(String key, boolean def) {
        Object o = get(key);
        if (o == null) return def;
        try {
            return Boolean.parseBoolean(o.toString());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public int getInt(String key) {
        return (int)getLong(key, 0);
    }

    public int getInt(String key, int def) {
        return (int)getLong(key, def);
    }

    public long getLong(String key) {
        return getLong(key, 0);
    }

    public long getLong(String key, long def) {
        Object o = get(key);
        if (o == null) return def;
        try {
            return Long.parseLong(o.toString());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public float getFloat(String key) {
        return (float)getDouble(key, 0);
    }

    public float getFloat(String key, float def) {
        return (float)getDouble(key, def);
    }

    public double getDouble(String key) {
        return getDouble(key, 0);
    }

    public double getDouble(String key, double def) {
        Object o = get(key);
        if (o == null) return def;
        try {
            return Double.parseDouble(o.toString());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public ConfigurationNode getNode(String key) {
        return getNode(key, null);
    }

    public ConfigurationNode getNode(String key, ConfigurationNode def) {
        Object o = get(key);
        if (o == null) return def;
        if (o instanceof ConfigurationNode) return (ConfigurationNode)o;
        return def;
    }

    public List<String> getStringList(String key) {
        return getStringList(key, null);
    }
    
    public List<String> getStringList(String key, List<String> def) {
        Object o = get(key);
        if (o == null) return def;
        List<String> c = new ArrayList<String>();
        if (o instanceof Collection) {
            for (Object obj : (Collection)o) {
                if ((obj instanceof String) || (obj == null))
                    c.add((String)obj);
            }
        }
        return c;
    }

    public List<ConfigurationNode> getNodeList(String key) {
        return getNodeList(key, null);
    }
    
    public List<ConfigurationNode> getNodeList(String key, List<ConfigurationNode> def) {
        Object o = get(key);
        if (o == null) return def;
        List<ConfigurationNode> c = new ArrayList<ConfigurationNode>();
        if (o instanceof Collection) {
            for (Object obj : (Collection)o) {
                if ((obj instanceof ConfigurationNode) || (obj == null))
                    c.add((ConfigurationNode)obj);
            }
        }
        return c;
    }
    
}
