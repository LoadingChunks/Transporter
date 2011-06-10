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
package org.bennedum.transporter.net;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import org.bennedum.transporter.Utils;

/**
 *
 * @author frdfsnlght <frdfsnlght@gmail.com>
 */
public final class Message extends HashMap<String,Object> {

    public static Message decode(String encoded) {
        return (Message)decodeObject(new StringBuilder(encoded));
    }

    private static String encodeObject(Object v) {
        if (v == null) return "n:0:";
        if (v instanceof String) return encodeString((String)v);
        if (v instanceof Boolean) return encodeBoolean((Boolean)v);
        if (v instanceof Byte) return encodeLong(((Byte)v).longValue());
        if (v instanceof Short) return encodeLong(((Short)v).longValue());
        if (v instanceof Integer) return encodeLong(((Integer)v).longValue());
        if (v instanceof Long) return encodeLong((Long)v);
        if (v instanceof Float) return encodeDouble(((Float)v).doubleValue());
        if (v instanceof Double) return encodeDouble((Double)v);
        if (v instanceof Message) return encodeMessage((Message)v);
        if (v instanceof Collection) return encodeList((Collection)v);
        throw new IllegalArgumentException("unable to encode '" + v.getClass().getName() + "'");
    }

    private static Object decodeObject(StringBuilder b) {
        char type = b.charAt(0);
        b.delete(0, 2);
        int pos = b.indexOf(":");
        int len = Integer.parseInt(b.substring(0, pos));
        b.delete(0, pos + 1);
        switch (type) {
            case 'n': return null;
            case 's': return decodeString(b, len);
            case 'b': return decodeBoolean(b, len);
            case 'l': return decodeLong(b, len);
            case 'd': return decodeDouble(b, len);
            case 'm': return decodeMessage(b, len);
            case 'v': return decodeList(b, len);
            default:
                throw new IllegalArgumentException("unable to decode '" + type + "'");
        }
    }

    private static String stringifyObject(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return stringifyString((String)v);
        if (v instanceof Boolean) return stringifyBoolean((Boolean)v);
        if (v instanceof Byte) return stringifyLong(((Byte)v).longValue());
        if (v instanceof Short) return stringifyLong(((Short)v).longValue());
        if (v instanceof Integer) return stringifyLong(((Integer)v).longValue());
        if (v instanceof Long) return stringifyLong((Long)v);
        if (v instanceof Float) return stringifyDouble(((Float)v).doubleValue());
        if (v instanceof Double) return stringifyDouble((Double)v);
        if (v instanceof Message) return stringifyMessage((Message)v);
        if (v instanceof Collection) return stringifyList((Collection)v);
        throw new IllegalArgumentException("unable to stringify '" + v.getClass().getName() + "'");
    }

    private static String encodeString(String v) {
        try {
            v = URLEncoder.encode(v, "UTF-8");
        } catch (UnsupportedEncodingException e) {}
        return "s:" + v.length() + ":" + v;
    }

    private static String decodeString(StringBuilder b, int len) {
        String str = b.substring(0, len);
        b.delete(0, len);
        try {
            return URLDecoder.decode(str, "UTF-8");
        } catch (UnsupportedEncodingException e) { return null; }
    }

    private static String stringifyString(String v) {
        return "\"" + v + "\"";
    }

    private static String encodeBoolean(Boolean v) {
        String s = v.toString();
        return "b:" + s.length() + ":" + s;
    }

    private static Boolean decodeBoolean(StringBuilder b, int len) {
        String str = b.substring(0, len);
        b.delete(0, len);
        return Boolean.parseBoolean(str);
    }

    private static String stringifyBoolean(Boolean v) {
        return v.toString();
    }

    private static String encodeLong(Long v) {
        String s = v.toString();
        return "l:" + s.length() + ":" + s;
    }

    private static Long decodeLong(StringBuilder b, int len) {
        String str = b.substring(0, len);
        b.delete(0, len);
        return Long.parseLong(str);
    }

    private static String stringifyLong(Long v) {
        return v.toString();
    }

    private static String encodeDouble(Double v) {
        String s = v.toString();
        return "d:" + s.length() + ":" + s;
    }

    private static Double decodeDouble(StringBuilder b, int len) {
        String str = b.substring(0, len);
        b.delete(0, len);
        return Double.parseDouble(str);
    }

    private static String stringifyDouble(Double v) {
        return v.toString();
    }

    private static String encodeMessage(Message v) {
        StringBuilder buf = new StringBuilder("m:");
        buf.append(v.size()).append(":");
        for (String key : v.keySet()) {
            buf.append(encodeString(key));
            buf.append(encodeObject(v.get(key)));
        }
        return buf.toString();
    }

    private static Message decodeMessage(StringBuilder b, int len) {
        Message m = new Message();
        for (int i = 0; i < len; i++) {
            String key = (String)decodeObject(b);
            Object value = decodeObject(b);
            m.put(key, value);
        }
        return m;
    }

    private static String stringifyMessage(Message v) {
        StringBuilder buf = new StringBuilder();
        for (String key : v.keySet())
            buf.append(key).append(": ").append(stringifyObject(v.get(key))).append("\n");
        if (buf.length() > 0)
            buf.deleteCharAt(buf.length() - 1);
        return "{\n" + pad(buf.toString()) + "\n}";
    }

    private static String encodeList(Collection v) {
        StringBuilder buf = new StringBuilder("v:");
        buf.append(v.size()).append(":");
        for (Object o : v)
            buf.append(encodeObject(o));
        return buf.toString();
    }

    private static List<Object> decodeList(StringBuilder b, int len) {
        List<Object> l = new ArrayList<Object>();
        for (int i = 0; i < len; i++)
            l.add(decodeObject(b));
        return l;
    }

    private static String stringifyList(Collection v) {
        StringBuilder buf = new StringBuilder();
        for (Object o : v)
            buf.append(stringifyObject(o)).append("\n");
        if (buf.length() > 0)
            buf.deleteCharAt(buf.length() - 1);
        return "[\n" + pad(buf.toString()) + "\n]";
    }

    private static String pad(String str) {
        StringBuilder buf = new StringBuilder();
        for (String line : str.split("\n"))
            buf.append("  ").append(line).append("\n");
        if (buf.length() > 0)
            buf.deleteCharAt(buf.length() - 1);
        return buf.toString();
    }

    public Message() {}

    public String encode() {
        return encodeMessage(this);
    }

    public Object get(String key, Object def) {
        if (containsKey(key))
            return get(key);
        else
            return def;
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

    public Message getMessage(String key) {
        return getMessage(key, null);
    }

    public Message getMessage(String key, Message def) {
        Object o = get(key);
        if (o == null) return def;
        if (o instanceof Message) return (Message)o;
        return def;
    }

    public Collection getList(String key) {
        return getList(key, null);
    }

    public List getList(String key, List def) {
        Object o = get(key);
        if (o == null) return def;
        if (o instanceof List) return (List)o;
        return def;
    }

    public List<String> getStringList(String key) {
        Object o = get(key);
        if (o == null) return null;
        List<String> c = new ArrayList<String>();
        if (o instanceof Collection) {
            for (Object obj : (Collection)o) {
                if ((obj instanceof String) || (obj == null))
                    c.add((String)obj);
            }
        }
        return c;
    }

    public List<Message> getMessageList(String key) {
        Object o = get(key);
        if (o == null) return null;
        List<Message> c = new ArrayList<Message>();
        if (o instanceof Collection) {
            for (Object obj : (Collection)o) {
                if ((obj instanceof Message) || (obj == null))
                    c.add((Message)obj);
            }
        }
        return c;
    }

    @Override
    public String toString() {
        return stringifyMessage(this);
    }

}
