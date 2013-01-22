/*
 * Copyright 2013 James Geboski <jgeboski@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frdfsnlght.transporter.compatibility;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;

public class Reflect
{
    public static Object create(String name, Object ... args)
    {
        Class       c;
        Constructor s;

        try {
            c = Class.forName(name);
            s = c.getDeclaredConstructor(getTypes(args));
            return s.newInstance(args);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static Object getField(Object obj, String name)
    {
        Field f;

        try {
            f = field(obj.getClass(), name);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        try {
            return f.get(null);
        } catch (Exception e) { }

        return null;
    }

    public static RMethod getMethod(Object obj, String name, String ... types)
    {
        Method m;

        try {
            m = method(obj.getClass(), name, getTypes(types));
        } catch (Exception e) {
            e.printStackTrace();
            m = null;
        }

        return new RMethod(m, obj);
    }

    public static RMethod getMethod(String obj, String name, String ... types)
    {
        Class  c;
        Method m;

        try {
            c = Class.forName(obj);
            m = method(c, name, getTypes(types));
        } catch (Exception e) {
            e.printStackTrace();
            m = null;
        }

        return new RMethod(m, null);
    }

    public static Object invoke(Object obj, String name, Object ... args)
    {
        Method m;

        try {
            m = method(obj.getClass(), name, getTypes(args));
            return m.invoke(obj, args);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static Object invoke(String obj, String name, Object ... args)
    {
        Class  c;
        Method m;

        try {
            c = Class.forName(obj);
            m = method(c, name, getTypes(args));
            return m.invoke(null, args);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean isInstance(Object obj, String name)
    {
        Class  c;

        try {
            c = Class.forName(name);
            return c.isInstance(obj);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public static String obcname(String name)
    {
        String n;

        n = Bukkit.getServer().getClass().getPackage().getName();
        n = String.format("%s.%s", n, name);

        return n;
    }

    public static String nmsname(String name)
    {
        String n;

        n = Bukkit.getServer().getClass().getPackage().getName();
        n = n.substring(n.lastIndexOf('.') + 1);
        n = String.format("net.minecraft.server.%s.%s", n, name);

        return n;
    }

    private static Field field(Class klass, String name)
        throws NoSuchFieldException, SecurityException
    {
        Field f;

        while (true) {
            try {
                f = klass.getDeclaredField(name);
                break;
            } catch (NoSuchFieldException e) {
                klass = klass.getSuperclass();

                if (klass == null) {
                    throw e;
                }
            }
        }

        if (!f.isAccessible())
            f.setAccessible(true);

        return f;
    }

    private static Method method(Class klass, String name, Class ... types)
        throws NoSuchMethodException, SecurityException
    {
        Method m;

        while (true) {
            try {
                m = klass.getDeclaredMethod(name, types);
                break;
            } catch (NoSuchMethodException e) {
                klass = klass.getSuperclass();

                if (klass == null) {
                    throw e;
                }
            }
        }

        if (!m.isAccessible())
            m.setAccessible(true);

        return m;
    }

    private static Class[] getTypes(Object objs[])
    {
        Class t[];
        Class c;

        t = new Class[objs.length];

        for (int i = 0; i < objs.length; i++) {
            c = objs[i].getClass();

            try {
                t[i] = (Class) c.getField("TYPE").get(null);
            } catch (Exception e) {
                t[i] = c;
            }
        }

        return t;
    }

    private static Class[] getTypes(String objs[])
    {
        Class t[];

        t = new Class[objs.length];

        for (int i = 0; i < objs.length; i++) {
            try {
                t[i] = Class.forName(objs[i]);
            } catch (Exception e) {
                t[i] = null;
            }
        }

        return t;
    }
}

class RMethod
{
    public Method method;
    public Object object;

    public RMethod(Method method, Object object)
    {
        this.method = method;
        this.object = object;
    }

    public Object invoke(Object ... args)
    {
        if (method == null)
            return null;

        try {
            return method.invoke(object, args);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
