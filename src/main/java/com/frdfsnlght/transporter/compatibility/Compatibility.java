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
package com.frdfsnlght.transporter.compatibility;

import com.frdfsnlght.transporter.Global;
import com.frdfsnlght.transporter.TypeMap;
import com.frdfsnlght.transporter.Utils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public class Compatibility {

    public void sendAllPacket201PlayerInfo(String playerName, boolean b, int i) {
        Object pl = Reflect.invoke(Global.plugin.getServer(), "getHandle");
        Object pk = Reflect.create(Reflect.nmsname("Packet201PlayerInfo"),
                                   playerName, b, i);

        Reflect.getMethod(pl, "sendAll", Reflect.nmsname("Packet")).invoke(pk);
    }

    public void sendPlayerPacket201PlayerInfo(Player player, String playerName, boolean b, int i) {
        Object pc = Reflect.invoke(player, "getHandle");
        pc = Reflect.getField(pc, "playerConnection");

        if (pc == null) return;

        Object pk = Reflect.create(Reflect.nmsname("Packet201PlayerInfo"),
                                   playerName, true, 9999);
        Reflect.getMethod(pc, "sendAll", Reflect.nmsname("Packet")).invoke(pk);
    }

    public ItemStack createItemStack(int type, int amount, short durability) {
        return new ItemStack(type, amount, durability);
    }

    public TypeMap getItemStackTag(ItemStack stack) {
        Object nmss = Reflect.invoke(Reflect.obcname("inventory.CraftItemStack"),
                                     "asNMSCopy", stack);

        if (nmss == null) return null;
        nmss = Reflect.invoke(nmss, "getTag");
        return (TypeMap) encodeNBT(nmss);
    }

    public ItemStack setItemStackTag(ItemStack stack, TypeMap tag) {
        Object nmss = Reflect.invoke(Reflect.obcname("inventory.CraftItemStack"),
                                     "asNMSCopy", stack);

        if (nmss == null) return stack;
        Reflect.invoke(nmss, "setTag", decodeNBT(tag));

        return (ItemStack) Reflect.invoke(Reflect.obcname("inventory.CraftItemStack"),
                                          "asCraftMirror", nmss);
    }

    private TypeMap encodeNBT(Object tag) {
        if (tag == null) return null;

        TypeMap map = new TypeMap();
        Collection col = (Collection) Reflect.invoke(tag, "c");

        for (Object o : col) {
            if (!Reflect.isInstance(o, Reflect.nmsname("NBTBase"))) continue;
            String n = (String) Reflect.invoke(o, "getName");

            if (Reflect.isInstance(o, Reflect.nmsname("NBTTagCompound")))
                map.set(n, encodeNBT(o));
            else
                map.set(n, encodeNBTValue(o));
        }

        return map;
    }

    private Object encodeNBTValue(Object tag) {
        if (Reflect.isInstance(tag, Reflect.nmsname("NBTTagCompound")))
            return encodeNBT(tag);
        if (Reflect.isInstance(tag, Reflect.nmsname("NBTTagString")))
            return Reflect.getField(tag, "data");
        if (Reflect.isInstance(tag, Reflect.nmsname("NBTTagList"))) {
            List<Object> list = new ArrayList<Object>();
            try {
                Field f = tag.getClass().getDeclaredField("list");
                f.setAccessible(true);
                List<?> vs = (ArrayList<?>) f.get(tag);
                for (Object o : vs) {
                    if (Reflect.isInstance(tag, Reflect.nmsname("NBTBase")))
                        list.add(encodeNBTValue(o));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return list;
        }

        if (Reflect.isInstance(tag, Reflect.nmsname("NBTTagLong"))) {
            TypeMap map = new TypeMap();
            map.set("_type_", "long");
            map.set("value", Reflect.getField(tag, "data"));
            return map;
        }
        if (Reflect.isInstance(tag, Reflect.nmsname("NBTTagInt"))) {
            TypeMap map = new TypeMap();
            map.set("_type_", "int");
            map.set("value", Reflect.getField(tag, "data"));
            return map;
        }
        if (Reflect.isInstance(tag, Reflect.nmsname("NBTTagShort"))) {
            TypeMap map = new TypeMap();
            map.set("_type_", "short");
            map.put("value", Reflect.getField(tag, "data"));
            return map;
        }
        if (Reflect.isInstance(tag, Reflect.nmsname("NBTTagByte"))) {
            TypeMap map = new TypeMap();
            map.set("_type_", "byte");
            map.put("value", Reflect.getField(tag, "data"));
            return map;
        }
        if (Reflect.isInstance(tag, Reflect.nmsname("NBTTagDouble"))) {
            TypeMap map = new TypeMap();
            map.set("_type_", "double");
            map.put("value", Reflect.getField(tag, "data"));
            return map;
        }
        if (Reflect.isInstance(tag, Reflect.nmsname("NBTTagFloat"))) {
            TypeMap map = new TypeMap();
            map.set("_type_", "float");
            map.put("value", Reflect.getField(tag, "data"));
            return map;
        }

        return null;
    }

    private Object decodeNBT(TypeMap map) {
        if (map == null) return null;
		Object tag = Reflect.create(Reflect.nmsname("NBTTagCompound"));
        for (String k : map.getKeys()) {
            Object v = map.get(k);
            Reflect.invoke(tag, "set", k, decodeNBTValue(v));
        }
        return tag;
    }

    private Object decodeNBTValue(Object value) {
        if (value instanceof String)
			return Reflect.create(Reflect.nmsname("NBTTagString"), null,
                                  (String) value);

        if (value instanceof Collection) {
            Collection<?> list = (Collection<?>)value;
            Object tlist = Reflect.create(Reflect.nmsname("NBTTagList"));
            Class<?> type = null;
            for (Object o : list) {
                Object tv = decodeNBTValue(o);
				if (type == null)
                    type = tv.getClass();
				else if (type != tv.getClass())
                    continue;
				Reflect.invoke(tlist, "add", tv);
			}
			return tlist;
        }

        if (value instanceof TypeMap) {
            TypeMap map = (TypeMap)value;
            String type = map.getString("_type_");
            if (type == null) {
                Object tag = Reflect.create(Reflect.nmsname("NBTTagCompound"));
                for (String k : map.getKeys())
                    Reflect.invoke(tag, "set", k, decodeNBTValue(map.get(k)));
                return tag;
            }

            if (type.equals("long"))
                return Reflect.create(Reflect.nmsname("NBTTagLong"), null,
                                      map.getLong("value"));
            if (type.equals("int"))
                return Reflect.create(Reflect.nmsname("NBTTagInt"), null,
                                      map.getInt("value"));
            if (type.equals("short"))
                return Reflect.create(Reflect.nmsname("NBTTagShort"), null,
                                      map.getShort("value"));
            if (type.equals("byte"))
                return Reflect.create(Reflect.nmsname("NBTTagByte"), null,
                                      map.getByte("value"));
            if (type.equals("double"))
                return Reflect.create(Reflect.nmsname("NBTTagDouble"), null,
                                      map.getDouble("value"));
            if (type.equals("float"))
                return Reflect.create(Reflect.nmsname("NBTTagFloat"), null,
                                      map.getFloat("value"));
        }

        return null;
    }
}
