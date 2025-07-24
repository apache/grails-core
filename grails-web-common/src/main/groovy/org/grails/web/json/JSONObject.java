/*
Public Domain.
*/

package org.grails.web.json;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import groovy.lang.Writable;

import org.springframework.util.ClassUtils;

import org.grails.encoder.EncodesToWriter;
import org.grails.encoder.StreamingEncoder;
import org.grails.encoder.StreamingEncoderWritable;
import org.grails.encoder.StreamingEncoderWriter;

/**
 * A JSONObject is an unordered collection of name/value pairs. Its
 * external form is a string wrapped in curly braces with colons between the
 * names and values, and commas between the values  and names. The internal form
 * is an object having <code>get</code> and <code>opt</code> methods for
 * accessing the values by name, and <code>put</code> methods for adding or
 * replacing values by name. The values can be any of these types:
 * <code>Boolean</code>, <code>JSONArray</code>, <code>JSONObject</code>,
 * <code>Number</code>, or <code>String</code>. A JSONObject constructor can be used to convert an external form
 * JSON text into an internal form whose values can be retrieved with the
 * <code>get</code> and <code>opt</code> methods, or to convert values into a
 * JSON text using the <code>put</code> and <code>toString</code> methods.
 * A <code>get</code> method returns a value if one can be found, and throws an
 * exception if one cannot be found. An <code>opt</code> method returns a
 * default value instead of throwing an exception, and so is useful for
 * obtaining optional values.
 * <p>
 * The generic <code>get()</code> and <code>opt()</code> methods return an
 * object, which you can cast or query for type. There are also typed
 * <code>get</code> and <code>opt</code> methods that do type checking and type
 * coersion for you.
 * <p>
 * The <code>put</code> methods adds values to an object. For example, <pre>
 *     myString = new JSONObject().put("JSON", "Hello, World!").toString();</pre>
 * produces the string <code>{"JSON": "Hello, World"}</code>.
 * <p>
 * The texts produced by the <code>toString</code> methods strictly conform to
 * the JSON sysntax rules.
 * The constructors are more forgiving in the texts they will accept:
 * <ul>
 * <li>An extra <code>,</code>&nbsp;<small>(comma)</small> may appear just
 * before the closing brace.</li>
 * <li>Strings may be quoted with <code>'</code>&nbsp;<small>(single
 * quote)</small>.</li>
 * <li>Strings do not need to be quoted at all if they do not begin with a quote
 * or single quote, and if they do not contain leading or trailing spaces,
 * and if they do not contain any of these characters:
 * <code>{ } [ ] / \ : , = ; #</code> and if they do not look like numbers
 * and if they are not the reserved words <code>true</code>,
 * <code>false</code>, or <code>null</code>.</li>
 * <li>Keys can be followed by <code>=</code> or <code>=$gt;</code> as well as
 * by <code>:</code>.</li>
 * <li>Values can be followed by <code>;</code> <small>(semicolon)</small> as
 * well as by <code>,</code> <small>(comma)</small>.</li>
 * <li>Numbers may have the <code>0-</code> <small>(octal)</small> or
 * <code>0x-</code> <small>(hex)</small> prefix.</li>
 * <li>Comments written in the slashshlash, slashstar, and hash conventions
 * will be ignored.</li>
 * </ul>
 *
 * @author JSON.org
 * @version 2
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class JSONObject implements JSONElement, Map {
    private static EncodesToWriter javascriptEncoderStateless;
    private static StreamingEncoder javascriptEncoder;
    private static boolean useStreamingJavascriptEncoder=false;
    static {
        try {
            javascriptEncoder = (StreamingEncoder)ClassUtils.forName("grails.encoders.JSONEncoder", JSONObject.class.getClassLoader()).newInstance();
            javascriptEncoderStateless = (EncodesToWriter)javascriptEncoder;
            useStreamingJavascriptEncoder = true;
        }
        catch (Exception e) {
            // ignore
        }
    }

    /**
     * The hash map where the JSONObject's properties are kept.
     */
    private HashMap myHashMap;

    /**
     * Construct an empty JSONObject.
     */
    public JSONObject() {
        this.myHashMap = new HashMap();
    }


    /**
     * Construct a JSONObject from a subset of another JSONObject.
     * An array of strings is used to identify the keys that should be copied.
     * Missing keys are ignored.
     *
     * @param jo A JSONObject.
     * @param sa An array of strings.
     * @throws JSONException If a value is a non-finite number.
     */
    public JSONObject(JSONObject jo, String[] sa) throws JSONException {
        this();
        for (int i = 0; i < sa.length; i += 1) {
            putOpt(sa[i], jo.opt(sa[i]));
        }
    }


    /**
     * Construct a JSONObject from a JSONTokener.
     *
     * @param x A JSONTokener object containing the source string.
     * @throws JSONException If there is a syntax error in the source string.
     */
    public JSONObject(JSONTokener x) throws JSONException {
        this();
        char c;
        String key;

        if (x.nextClean() != '{') {
            throw x.syntaxError("A JSONObject text must begin with '{'");
        }
        for (; ;) {
            c = x.nextClean();
            switch (c) {
                case 0:
                    throw x.syntaxError("A JSONObject text must end with '}'");
                case '}':
                    return;
                default:
                    x.back();
                    key = x.nextValue().toString();
            }

            /*
             * The key is followed by ':'. We will also tolerate '=' or '=>'.
             */

            c = x.nextClean();
            if (c == '=') {
                if (x.next() != '>') {
                    x.back();
                }
            } else if (c != ':') {
                throw x.syntaxError("Expected a ':' after a key");
            }
            this.myHashMap.put(key, x.nextValue());

            /*
             * Pairs are separated by ','. We will also tolerate ';'.
             */

            switch (x.nextClean()) {
                case ';':
                case ',':
                    if (x.nextClean() == '}') {
                        return;
                    }
                    x.back();
                    break;
                case '}':
                    return;
                default:
                    throw x.syntaxError("Expected a ',' or '}'");
            }
        }
    }


    /**
     * Construct a JSONObject from a Map.
     *
     * @param map A map object that can be used to initialize the contents of
     *            the JSONObject.
     */
    public JSONObject(Map map) {
        this.myHashMap = new HashMap(map);
    }


    /**
     * Construct a JSONObject from a string.
     * This is the most commonly used JSONObject constructor.
     *
     * @param string A string beginning
     *               with <code>{</code>&nbsp;<small>(left brace)</small> and ending
     *               with <code>}</code>&nbsp;<small>(right brace)</small>.
     * @throws JSONException If there is a syntax error in the source string.
     */
    public JSONObject(String string) throws JSONException {
        this(new JSONTokener(string));
    }


    /**
     * Accumulate values under a key. It is similar to the put method except
     * that if there is already an object stored under the key then a
     * JSONArray is stored under the key to hold all of the accumulated values.
     * If there is already a JSONArray, then the new value is appended to it.
     * In contrast, the put method replaces the previous value.
     *
     * @param key   A key string.
     * @param value An object to be accumulated under the key.
     * @return this
     * @throws JSONException If the value is an invalid number
     *                       or if the key is null.
     */
    public JSONObject accumulate(String key, Object value)
            throws JSONException {
        testValidity(value);
        Object o = opt(key);
        if (o == null) {
            put(key, value);
        } else if (o instanceof JSONArray) {
            ((JSONArray) o).put(value);
        } else {
            put(key, new JSONArray().put(o).put(value));
        }
        return this;
    }


    /**
     * Get the value object associated with a key.
     *
     * @param key A key string.
     * @return The object associated with the key.
     * @throws JSONException if the key is not found.
     */
    public Object get(String key) throws JSONException {
        if(!myHashMap.containsKey(key)) {
            throw new JSONException("JSONObject[" + quote(key) +
                    "] not found.");
        }
        return opt(key);
    }


    /**
     * Get the boolean value associated with a key.
     *
     * @param key A key string.
     * @return The truth.
     * @throws JSONException if the value is not a Boolean or the String "true" or "false".
     */
    public boolean getBoolean(String key) throws JSONException {
        Object o = get(key);
        if (o.equals(Boolean.FALSE) ||
                (o instanceof String &&
                        ((String) o).equalsIgnoreCase("false"))) {
            return false;
        } else if (o.equals(Boolean.TRUE) ||
                (o instanceof String &&
                        ((String) o).equalsIgnoreCase("true"))) {
            return true;
        }
        throw new JSONException("JSONObject[" + quote(key) +
                "] is not a Boolean.");
    }


    /**
     * Get the double value associated with a key.
     *
     * @param key A key string.
     * @return The numeric value.
     * @throws JSONException if the key is not found or
     *                       if the value is not a Number object and cannot be converted to a number.
     */
    public double getDouble(String key) throws JSONException {
        Object o = get(key);
        try {
            return o instanceof Number ?
                    ((Number) o).doubleValue() : Double.parseDouble((String) o);
        } catch (Exception e) {
            throw new JSONException("JSONObject[" + quote(key) +
                    "] is not a number.");
        }
    }


    /**
     * Get the int value associated with a key. If the number value is too
     * large for an int, it will be clipped.
     *
     * @param key A key string.
     * @return The integer value.
     * @throws JSONException if the key is not found or if the value cannot
     *                       be converted to an integer.
     */
    public int getInt(String key) throws JSONException {
        Object o = get(key);
        return o instanceof Number ?
                ((Number) o).intValue() : (int) getDouble(key);
    }


    /**
     * Get the JSONArray value associated with a key.
     *
     * @param key A key string.
     * @return A JSONArray which is the value.
     * @throws JSONException if the key is not found or
     *                       if the value is not a JSONArray.
     */
    public JSONArray getJSONArray(String key) throws JSONException {
        Object o = get(key);
        if (o instanceof JSONArray) {
            return (JSONArray) o;
        }
        throw new JSONException("JSONObject[" + quote(key) +
                "] is not a JSONArray.");
    }


    /**
     * Get the JSONObject value associated with a key.
     *
     * @param key A key string.
     * @return A JSONObject which is the value.
     * @throws JSONException if the key is not found or
     *                       if the value is not a JSONObject.
     */
    public JSONObject getJSONObject(String key) throws JSONException {
        Object o = get(key);
        if (o instanceof JSONObject) {
            return (JSONObject) o;
        }
        throw new JSONException("JSONObject[" + quote(key) +
                "] is not a JSONObject.");
    }


    /**
     * Get the long value associated with a key. If the number value is too
     * long for a long, it will be clipped.
     *
     * @param key A key string.
     * @return The long value.
     * @throws JSONException if the key is not found or if the value cannot
     *                       be converted to a long.
     */
    public long getLong(String key) throws JSONException {
        Object o = get(key);
        return o instanceof Number ?
                ((Number) o).longValue() : (long) getDouble(key);
    }


    /**
     * Get the string associated with a key.
     *
     * @param key A key string.
     * @return A string which is the value.
     * @throws JSONException if the key is not found.
     */
    public String getString(String key) throws JSONException {
        return get(key).toString();
    }


    /**
     * Determine if the JSONObject contains a specific key.
     *
     * @param key A key string.
     * @return true if the key exists in the JSONObject.
     */
    public boolean has(String key) {
        return myHashMap.containsKey(key);
    }


    /**
     * Determine if the value associated with the key is null or if there is
     * no value.
     *
     * @param key A key string.
     * @return true if there is no value associated with the key or if
     *         the value is the JSONObject.NULL object.
     */
    public boolean isNull(String key) {
        return opt(key) == null;
    }


    /**
     * Get an enumeration of the keys of the JSONObject.
     *
     * @return An iterator of the keys.
     */
    public Iterator keys() {
        return myHashMap.keySet().iterator();
    }


    /**
     * Get the number of keys stored in the JSONObject.
     *
     * @return The number of keys in the JSONObject.
     */
    public int length() {
        return myHashMap.size();
    }


    /**
     * Produce a JSONArray containing the names of the elements of this
     * JSONObject.
     *
     * @return A JSONArray containing the key strings, or null if the JSONObject
     *         is empty.
     */
    public JSONArray names() {
        JSONArray ja = new JSONArray();
        Iterator keys = keys();
        while (keys.hasNext()) {
            ja.put(keys.next());
        }
        return ja.length() == 0 ? null : ja;
    }

    /**
     * Produce a string from a number.
     *
     * @param n A Number
     * @return A String.
     * @throws JSONException If n is a non-finite number.
     */
    static public String numberToString(Number n)
            throws JSONException {
        if (n == null) {
            throw new JSONException("Null pointer");
        }
        testValidity(n);

// Shave off trailing zeros and decimal point, if possible.

        String s = n.toString();
        if (s.indexOf('.') > 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0) {
            while (s.endsWith("0")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }

    static public String dateToString(Date d) throws JSONException {
        return "new Date(" + d.getTime() + ")";
    }

    static public String collectionToString(Collection c) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        Iterator iterator = c.iterator();
        while(iterator.hasNext()) {
            if (!first) {
                sb.append(',');
            }
            sb.append(valueToString(iterator.next()));
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }


    /**
     * Get an optional value associated with a key.
     *
     * @param key A key string.
     * @return An object which is the value, or null if there is no value.
     */
    public Object opt(String key) {
        return key == null ? null : this.myHashMap.get(key);
    }


    /**
     * Get an optional boolean associated with a key.
     * It returns false if there is no such key, or if the value is not
     * Boolean.TRUE or the String "true".
     *
     * @param key A key string.
     * @return The truth.
     */
    public boolean optBoolean(String key) {
        return optBoolean(key, false);
    }


    /**
     * Get an optional boolean associated with a key.
     * It returns the defaultValue if there is no such key, or if it is not
     * a Boolean or the String "true" or "false" (case insensitive).
     *
     * @param key          A key string.
     * @param defaultValue The default.
     * @return The truth.
     */
    public boolean optBoolean(String key, boolean defaultValue) {
        try {
            return getBoolean(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }


    /**
     * Get an optional double associated with a key,
     * or NaN if there is no such key or if its value is not a number.
     * If the value is a string, an attempt will be made to evaluate it as
     * a number.
     *
     * @param key A string which is the key.
     * @return An object which is the value.
     */
    public double optDouble(String key) {
        return optDouble(key, Double.NaN);
    }


    /**
     * Get an optional double associated with a key, or the
     * defaultValue if there is no such key or if its value is not a number.
     * If the value is a string, an attempt will be made to evaluate it as
     * a number.
     *
     * @param key          A key string.
     * @param defaultValue The default.
     * @return An object which is the value.
     */
    public double optDouble(String key, double defaultValue) {
        try {
            Object o = opt(key);
            return o instanceof Number ? ((Number) o).doubleValue() :
                    Double.valueOf((String) o);
        } catch (Exception e) {
            return defaultValue;
        }
    }


    /**
     * Get an optional int value associated with a key,
     * or zero if there is no such key or if the value is not a number.
     * If the value is a string, an attempt will be made to evaluate it as
     * a number.
     *
     * @param key A key string.
     * @return An object which is the value.
     */
    public int optInt(String key) {
        return optInt(key, 0);
    }


    /**
     * Get an optional int value associated with a key,
     * or the default if there is no such key or if the value is not a number.
     * If the value is a string, an attempt will be made to evaluate it as
     * a number.
     *
     * @param key          A key string.
     * @param defaultValue The default.
     * @return An object which is the value.
     */
    public int optInt(String key, int defaultValue) {
        try {
            return getInt(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }


    /**
     * Get an optional JSONArray associated with a key.
     * It returns null if there is no such key, or if its value is not a
     * JSONArray.
     *
     * @param key A key string.
     * @return A JSONArray which is the value.
     */
    public JSONArray optJSONArray(String key) {
        Object o = opt(key);
        return o instanceof JSONArray ? (JSONArray) o : null;
    }


    /**
     * Get an optional JSONObject associated with a key.
     * It returns null if there is no such key, or if its value is not a
     * JSONObject.
     *
     * @param key A key string.
     * @return A JSONObject which is the value.
     */
    public JSONObject optJSONObject(String key) {
        Object o = opt(key);
        return o instanceof JSONObject ? (JSONObject) o : null;
    }


    /**
     * Get an optional long value associated with a key,
     * or zero if there is no such key or if the value is not a number.
     * If the value is a string, an attempt will be made to evaluate it as
     * a number.
     *
     * @param key A key string.
     * @return An object which is the value.
     */
    public long optLong(String key) {
        return optLong(key, 0);
    }


    /**
     * Get an optional long value associated with a key,
     * or the default if there is no such key or if the value is not a number.
     * If the value is a string, an attempt will be made to evaluate it as
     * a number.
     *
     * @param key          A key string.
     * @param defaultValue The default.
     * @return An object which is the value.
     */
    public long optLong(String key, long defaultValue) {
        try {
            return getLong(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }


    /**
     * Get an optional string associated with a key.
     * It returns an empty string if there is no such key. If the value is not
     * a string and is not null, then it is coverted to a string.
     *
     * @param key A key string.
     * @return A string which is the value.
     */
    public String optString(String key) {
        return optString(key, "");
    }


    /**
     * Get an optional string associated with a key.
     * It returns the defaultValue if there is no such key.
     *
     * @param key          A key string.
     * @param defaultValue The default.
     * @return A string which is the value.
     */
    public String optString(String key, String defaultValue) {
        Object o = opt(key);
        return o != null ? o.toString() : defaultValue;
    }


    /**
     * Put a key/boolean pair in the JSONObject.
     *
     * @param key   A key string.
     * @param value A boolean which is the value.
     * @return this
     * @throws JSONException If the key is null.
     */
    public JSONObject put(String key, boolean value) throws JSONException {
        put(key, value ? Boolean.TRUE : Boolean.FALSE);
        return this;
    }


    /**
     * Put a key/double pair in the JSONObject.
     *
     * @param key   A key string.
     * @param value A double which is the value.
     * @return this
     * @throws JSONException If the key is null or if the number is invalid.
     */
    public JSONObject put(String key, double value) throws JSONException {
        put(key, Double.valueOf(value));
        return this;
    }


    /**
     * Put a key/int pair in the JSONObject.
     *
     * @param key   A key string.
     * @param value An int which is the value.
     * @return this
     * @throws JSONException If the key is null.
     */
    public JSONObject put(String key, int value) throws JSONException {
        put(key, Integer.valueOf(value));
        return this;
    }


    /**
     * Put a key/long pair in the JSONObject.
     *
     * @param key   A key string.
     * @param value A long which is the value.
     * @return this
     * @throws JSONException If the key is null.
     */
    public JSONObject put(String key, long value) throws JSONException {
        put(key, Long.valueOf(value));
        return this;
    }


    /**
     * Put a key/value pair in the JSONObject. If the value is null,
     * then the key will be removed from the JSONObject if it is present.
     *
     * @param key   A key string.
     * @param value An object which is the value. It should be of one of these
     *              types: Boolean, Double, Integer, JSONArray, JSONObject, Long, String,
     *              or the JSONObject.NULL object.
     * @return this
     * @throws JSONException If the value is non-finite number
     *                       or if the key is null.
     */
    public JSONObject put(String key, Object value) throws JSONException {
        if (key == null) {
            throw new JSONException("Null key.");
        }
        if (value != null) {
            testValidity(value);
            this.myHashMap.put(key, value);
        } else {
            remove(key);
        }
        return this;
    }


    /**
     * Put a key/value pair in the JSONObject, but only if the
     * key and the value are both non-null.
     *
     * @param key   A key string.
     * @param value An object which is the value. It should be of one of these
     *              types: Boolean, Double, Integer, JSONArray, JSONObject, Long, String,
     *              or the JSONObject.NULL object.
     * @return this
     * @throws JSONException If the value is a non-finite number.
     */
    public JSONObject putOpt(String key, Object value) throws JSONException {
        if (key != null && value != null) {
            put(key, value);
        }
        return this;
    }


    /**
     * Produce a string in double quotes with backslash sequences in all the
     * right places. A backslash will be inserted within &lt;/, allowing JSON
     * text to be delivered in HTML. In JSON text, a string cannot contain a
     * control character or an unescaped quote or backslash.
     *
     * @param string A String
     * @return A String correctly formatted for insertion in a JSON text.
     */
    public static String quote(String string) {
        if (string == null || string.length() == 0) {
            return "\"\"";
        }

        char b;
        char c = 0;
        int i;
        int len = string.length();
        StringBuilder sb = new StringBuilder(len + 4);
        String t;

        sb.append('"');
        for (i = 0; i < len; i += 1) {
            b = c;
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '/':
                    if (b == '<') {
                        sb.append('\\');
                    }
                    sb.append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ') {
                        t = "000" + Integer.toHexString(c);
                        sb.append("\\u" + t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Remove a name and its value, if present.
     *
     * @param key The name to be removed.
     * @return The value that was associated with the name,
     *         or null if there was no value.
     */
    public Object remove(String key) {
        return myHashMap.remove(key);
    }

    /**
     * Throw an exception if the object is an NaN or infinite number.
     *
     * @param o The object to test.
     * @throws JSONException If o is a non-finite number.
     */
    static void testValidity(Object o) throws JSONException {
        if (o != null) {
            if (o instanceof Double) {
                if (((Double) o).isInfinite() || ((Double) o).isNaN()) {
                    throw new JSONException(
                            "JSON does not allow non-finite numbers");
                }
            } else if (o instanceof Float) {
                if (((Float) o).isInfinite() || ((Float) o).isNaN()) {
                    throw new JSONException(
                            "JSON does not allow non-finite numbers.");
                }
            }
        }
    }


    /**
     * Produce a JSONArray containing the values of the members of this
     * JSONObject.
     *
     * @param names A JSONArray containing a list of key strings. This
     *              determines the sequence of the values in the result.
     * @return A JSONArray of values.
     * @throws JSONException If any of the values are non-finite numbers.
     */
    public JSONArray toJSONArray(JSONArray names) throws JSONException {
        if (names == null || names.length() == 0) {
            return null;
        }
        JSONArray ja = new JSONArray();
        for (int i = 0; i < names.length(); i += 1) {
            ja.put(this.opt(names.getString(i)));
        }
        return ja;
    }

    /**
     * Make an JSON text of this JSONObject. For compactness, no whitespace
     * is added. If this would not result in a syntactically correct JSON text,
     * then null will be returned instead.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return a printable, displayable, portable, transmittable
     *         representation of the object, beginning
     *         with <code>{</code>&nbsp;<small>(left brace)</small> and ending
     *         with <code>}</code>&nbsp;<small>(right brace)</small>.
     */
    @Override
    public String toString() {
        try {
            Iterator keys = keys();
            StringBuilder sb = new StringBuilder("{");

            while (keys.hasNext()) {
                if (sb.length() > 1) {
                    sb.append(',');
                }
                Object o = keys.next();
                sb.append(quote(o.toString()));
                sb.append(':');
                sb.append(valueToString(this.myHashMap.get(o)));
            }
            sb.append('}');
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Make a prettyprinted JSON text of this JSONObject.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param indentFactor The number of spaces to add to each level of
     *                     indentation.
     * @return a printable, displayable, portable, transmittable
     *         representation of the object, beginning
     *         with <code>{</code>&nbsp;<small>(left brace)</small> and ending
     *         with <code>}</code>&nbsp;<small>(right brace)</small>.
     * @throws JSONException If the object contains an invalid number.
     */
    public String toString(int indentFactor) throws JSONException {
        return toString(indentFactor, 0);
    }


    /**
     * Make a prettyprinted JSON text of this JSONObject.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param indentFactor The number of spaces to add to each level of
     *                     indentation.
     * @param indent       The indentation of the top level.
     * @return a printable, displayable, transmittable
     *         representation of the object, beginning
     *         with <code>{</code>&nbsp;<small>(left brace)</small> and ending
     *         with <code>}</code>&nbsp;<small>(right brace)</small>.
     * @throws JSONException If the object contains an invalid number.
     */
    String toString(int indentFactor, int indent) throws JSONException {
        int i;
        int n = length();
        if (n == 0) {
            return "{}";
        }
        Iterator keys = keys();
        StringBuilder sb = new StringBuilder("{");
        int newindent = indent + indentFactor;
        Object o;
        if (n == 1) {
            o = keys.next();
            sb.append(quote(o.toString()));
            sb.append(": ");
            sb.append(valueToString(this.myHashMap.get(o), indentFactor,
                    indent));
        } else {
            while (keys.hasNext()) {
                o = keys.next();
                if (sb.length() > 1) {
                    sb.append(",\n");
                } else {
                    sb.append('\n');
                }
                for (i = 0; i < newindent; i += 1) {
                    sb.append(' ');
                }
                sb.append(quote(o.toString()));
                sb.append(": ");
                sb.append(valueToString(this.myHashMap.get(o), indentFactor,
                        newindent));
            }
            if (sb.length() > 1) {
                sb.append('\n');
                for (i = 0; i < indent; i += 1) {
                    sb.append(' ');
                }
            }
        }
        sb.append('}');
        return sb.toString();
    }


    /**
     * Make a JSON text of an object value.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param value The value to be serialized.
     * @return a printable, displayable, transmittable
     *         representation of the object, beginning
     *         with <code>{</code>&nbsp;<small>(left brace)</small> and ending
     *         with <code>}</code>&nbsp;<small>(right brace)</small>.
     * @throws JSONException If the value is or contains an invalid number.
     */
    static String valueToString(Object value) throws JSONException {
        if (value == null || value.equals(null)) {
            return "null";
        }
        if (value instanceof Number) {
            return numberToString((Number) value);
        }
        if (value instanceof Date) {
            return dateToString((Date) value);
        }
        if (value instanceof Boolean || value instanceof JSONObject) {
            return value.toString();
        }
        if (value instanceof Collection) {
            return collectionToString((Collection) value);
        }
        return quote(value.toString());
    }

    static void writeValue(Writer writer, Object value) throws IOException {
        if (value == null || value.equals(null)) {
            writer.write("null");
        } else if (value instanceof Number) {
            writeNumber(writer, (Number) value);
        } else if (value instanceof Date) {
            writeDate(writer, (Date) value);
        } else if (value instanceof Boolean) {
            writer.write(value.toString());
        } else if (value instanceof JSONElement) {
            ((JSONElement)value).writeTo(writer);
        } else {
            writeQuoted(writer, value);
        }
    }

    static void writeQuoted(Writer writer, Object value) throws IOException {
        if (useStreamingJavascriptEncoder) {
            writer.write("\"");
            if (value.getClass() == String.class || value.getClass() == StringBuilder.class || value.getClass() == StringBuffer.class) {
                encodeToWriter((CharSequence)value, writer);
            } else if(value instanceof StreamingEncoderWritable) {
                ((StreamingEncoderWritable)value).encodeTo(writer, javascriptEncoderStateless);
            } else if (value instanceof Writable) {
                ((Writable)value).writeTo(new StreamingEncoderWriter(writer, javascriptEncoder, null));
            }
            else{
                encodeToWriter(value.toString(), writer);
            }
            writer.write("\"");
        }
        else {
            writer.write(valueToString(value));
        }
    }


    protected static void encodeToWriter(CharSequence str, Writer writer) throws IOException {
        javascriptEncoderStateless.encodeToWriter(str, 0, str.length(), writer, null);
    }

    static void writeDate(Writer writer, Date d) throws IOException {
        writer.write("new Date(");
        writer.write(String.valueOf(d.getTime()));
        writer.write(")");
    }

    static void writeNumber(Writer writer, Number value) throws IOException {
        writer.write(String.valueOf(value));
    }

    /**
     * Make a prettyprinted JSON text of an object value.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @param value        The value to be serialized.
     * @param indentFactor The number of spaces to add to each level of
     *                     indentation.
     * @param indent       The indentation of the top level.
     * @return a printable, displayable, transmittable
     *         representation of the object, beginning
     *         with <code>{</code>&nbsp;<small>(left brace)</small> and ending
     *         with <code>}</code>&nbsp;<small>(right brace)</small>.
     * @throws JSONException If the object contains an invalid number.
     */
    static String valueToString(Object value, int indentFactor, int indent)
            throws JSONException {
        if (value == null || value.equals(null)) {
            return "null";
        }
        if (value instanceof Number) {
            return numberToString((Number) value);
        }
        if (value instanceof Date) {
            return dateToString((Date) value);
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof JSONObject) {
            return ((JSONObject) value).toString(indentFactor, indent);
        }
        if (value instanceof JSONArray) {
            return ((JSONArray) value).toString(indentFactor, indent);
        }
        return quote(value.toString());
    }


    /**
     * Write the contents of the JSONObject as JSON text to a writer.
     * For compactness, no whitespace is added.
     * <p>
     * Warning: This method assumes that the data structure is acyclical.
     *
     * @return The writer.
     * @throws JSONException
     */
    public Writer write(Writer writer) throws JSONException {
        try {
            boolean notFirst = false;
            writer.write('{');
            for(Iterator it = myHashMap.entrySet().iterator(); it.hasNext();) {
                Map.Entry entry = (Entry)it.next();
                if (notFirst) {
                    writer.write(',');
                }
                writeQuoted(writer, entry.getKey());
                writer.write(':');
                writeValue(writer, entry.getValue());
                notFirst = true;
            }
            writer.write('}');
            return writer;
        } catch (IOException e) {
            throw new JSONException(e);
        }
    }

    public int size() {
        return myHashMap.size();
    }

    public boolean isEmpty() {
        return myHashMap.isEmpty();
    }

    public boolean containsKey(Object o) {
        return myHashMap.containsKey(o);
    }

    public boolean containsValue(Object o) {
        return myHashMap.containsValue(o);
    }

    public Object get(Object o) {
        return myHashMap.get(o);
    }

    public Object put(Object o, Object o1) {
        return myHashMap.put(o, o1);
    }

    public Object remove(Object o) {
        return myHashMap.remove(o);
    }

    public void putAll(Map map) {
        this.myHashMap.putAll(map);
    }

    public void clear() {
        this.myHashMap.clear();
    }

    public Set keySet() {
        return myHashMap.keySet();
    }

    public Collection values() {
        return myHashMap.values();
    }

    public Set entrySet() {
        return myHashMap.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        JSONObject that = (JSONObject) o;

        if (myHashMap != null ? !myHashMap.equals(that.myHashMap) : that.myHashMap != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return (myHashMap != null ? myHashMap.hashCode() : 0);
    }


    @Override
    public Writer writeTo(Writer out) throws IOException {
        return write(out);
    }
}
