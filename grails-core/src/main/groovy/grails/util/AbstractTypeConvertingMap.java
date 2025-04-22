/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.util;

import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.util.HashCodeHelper;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AbstractTypeConvertingMap is a Map with type conversion capabilities.
 *
 * Type converting maps have no inherent ordering. Two maps with identical entries
 * but arranged in a different order internally are considered equal.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @since 1.2
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
public abstract class AbstractTypeConvertingMap extends GroovyObjectSupport implements Map, Cloneable {
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.S";
    protected Map wrappedMap;

    public AbstractTypeConvertingMap() {
        this(new LinkedHashMap());
    }

    public AbstractTypeConvertingMap(Map map) {
        if (map == null) map = new LinkedHashMap();
        wrappedMap = map;
    }
    
    public boolean equals(Map that) {
        return equals((Object)that);
    }    

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }

        if (that == null) {
            return false;
        }

        if (getClass() != that.getClass()) {
            return false;
        }

        AbstractTypeConvertingMap thatMap = (AbstractTypeConvertingMap)that;

        if (wrappedMap == thatMap.wrappedMap) {
            return true;
        }

        if (wrappedMap.size() != thatMap.wrappedMap.size()) {
            return false;
        }

        if (!wrappedMap.keySet().equals(thatMap.wrappedMap.keySet())) {
            return false;
        }

        final Iterator it = wrappedMap.keySet().iterator();
        while (it.hasNext()) {
            final Object key = it.next();
            Object thisValue = wrappedMap.get(key);
            Object thatValue = thatMap.wrappedMap.get(key);
            if (thisValue == null && thatValue != null || 
                thisValue != null && thatValue == null || 
                thisValue != thatValue && !thisValue.equals(thatValue)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hashCode = HashCodeHelper.initHash();
        for (Object entry : wrappedMap.entrySet()) {
            hashCode = HashCodeHelper.updateHash(hashCode, entry);
        }
        return hashCode;
    }

    /**
     * Helper method for obtaining integer value from parameter
     * @param name The name of the parameter
     * @return The integer value or null if there isn't one
     */
    public Byte getByte(String name) {
        Object o = get(name);
        if (o instanceof Number) {
            return ((Number)o).byteValue();
        }

        if (o != null) {
            try {
                String string = o.toString();
                if (string != null && string.length() > 0) {
                    return Byte.parseByte(string);
                }
            }
            catch (NumberFormatException e) {}
        }
        return null;
    }

    public Byte getByte(String name, Integer defaultValue) {
        Byte value = getByte(name);
        if (value == null && defaultValue != null) {
            value = (byte)defaultValue.intValue();
        }
        return value;
    }

    /**
     * Helper method for obtaining Character value from parameter
     * @param name The name of the parameter
     * @return The Character value or null if there isn't one
     */
    public Character getChar(String name) {
        Object o = get(name);
        if (o instanceof Character) {
            return (Character)o;
        }

        if (o != null) {
            String string = o.toString();
            if (string != null && string.length() == 1) {
                return string.charAt(0);
            }
        }
        return null;
    }

    public Character getChar(String name, Integer defaultValue) {
        Character value=getChar(name);
        if (value == null && defaultValue != null) {
            value = (char)defaultValue.intValue();
        }
        return value;
    }

    /**
     * Helper method for obtaining integer value from parameter
     * @param name The name of the parameter
     * @return The integer value or null if there isn't one
     */
    public Integer getInt(String name) {
        Object o = get(name);
        if (o instanceof Number) {
            return ((Number)o).intValue();
        }

        if (o != null) {
            try {
                String string = o.toString();
                if (string != null) {
                    return Integer.parseInt(string);
                }
            }
            catch (NumberFormatException e) {}
        }
        return null;
    }

    public Integer getInt(String name, Integer defaultValue) {
        Integer value=getInt(name);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Helper method for obtaining long value from parameter
     * @param name The name of the parameter
     * @return The long value or null if there isn't one
     */
    public Long getLong(String name) {
        Object o = get(name);
        if (o instanceof Number) {
            return ((Number)o).longValue();
        }

        if (o != null) {
            try {
                return Long.parseLong(o.toString());
            }
            catch (NumberFormatException e) {}
        }
        return null;
    }

    public Long getLong(String name, Long defaultValue) {
        Long value=getLong(name);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    /**
    * Helper method for obtaining short value from parameter
    * @param name The name of the parameter
    * @return The short value or null if there isn't one
    */
    public Short getShort(String name) {
        Object o = get(name);
        if (o instanceof Number) {
            return ((Number)o).shortValue();
        }

        if (o != null) {
            try {
                String string = o.toString();
                if (string != null) {
                    return Short.parseShort(string);
                }
            }
            catch (NumberFormatException e) {}
        }
        return null;
    }

    public Short getShort(String name, Integer defaultValue) {
        Short value=getShort(name);
        if (value == null && defaultValue != null) {
            value = defaultValue.shortValue();
        }
        return value;
    }

    /**
    * Helper method for obtaining double value from parameter
    * @param name The name of the parameter
    * @return The double value or null if there isn't one
    */
    public Double getDouble(String name) {
        Object o = get(name);
        if (o instanceof Number) {
            return ((Number)o).doubleValue();
        }

        if (o != null) {
            try {
                String string = o.toString();
                if (string != null) {
                    return Double.parseDouble(string);
                }
            }
            catch (NumberFormatException e) {}
        }
        return null;
    }

    public Double getDouble(String name, Double defaultValue) {
        Double value=getDouble(name);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Helper method for obtaining float value from parameter
     * @param name The name of the parameter
     * @return The double value or null if there isn't one
     */
    public Float getFloat(String name) {
        Object o = get(name);
        if (o instanceof Number) {
            return ((Number)o).floatValue();
        }

        if (o != null) {
            try {
                String string = o.toString();
                if (string != null) {
                    return Float.parseFloat(string);
                }
            }
            catch (NumberFormatException e) {}
        }
        return null;
    }

    public Float getFloat(String name, Float defaultValue) {
        Float value=getFloat(name);
        if (value == null) {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Helper method for obtaining boolean value from parameter
     * @param name The name of the parameter
     * @return The boolean value or null if there isn't one
     */
    public Boolean getBoolean(String name) {
        Object o = get(name);
        if (o instanceof Boolean) {
            return (Boolean)o;
        }

        if (o != null) {
            try {
                String string = o.toString();
                if (string != null) {
                    return GrailsStringUtils.toBoolean(string);
                }
            }
            catch (Exception e) {}
        }
        return null;
    }

    public Boolean getBoolean(String name, Boolean defaultValue) {
        Boolean value;
        if (containsKey(name)) {
            value = getBoolean(name);
        } else {
            value = defaultValue;
        }
        return value;
    }

    /**
     * Obtains a date for the parameter name using the default format
     * @param name
     * @return The date (in the {@link DEFAULT_DATE_FORMAT}) or null
     */
    public Date getDate(String name) {
        return getDate(name, DEFAULT_DATE_FORMAT);
    }

    /**
     * Obtains a date from the parameter using the given format
     * @param name The name
     * @param format The format
     * @return The date or null
     */
    public Date getDate(String name, String format) {
        Object value = get(name);
        if (value instanceof Date) {
            return (Date)value;
        }
        
        if (value != null) {
            try {
                return new SimpleDateFormat(format).parse(value.toString());
            } catch (ParseException e) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Obtains a date for the given parameter name
     *
     * @param name The name of the parameter
     * @return The date object or null if it cannot be parsed
     */
    public Date date(String name) {
        return getDate(name);
    }

    /**
     * Obtains a date for the given parameter name and format
     *
     * @param name The name of the parameter
     * @param format The format
     * @return The date object or null if it cannot be parsed
     */
    public Date date(String name, String format) {
        return getDate(name, format);
    }

    /**
     * Obtains a date for the given parameter name and format
     *
     * @param name The name of the parameter
     * @param formats The formats
     * @return The date object or null if it cannot be parsed
     */
    public Date date(String name, Collection<String> formats) {
        return getDate(name, formats);
    }

    private Date getDate(String name, Collection<String> formats) {
        for (String format : formats) {
            Date date = getDate(name,format);
            if (date != null) return date;
        }
        return null;
    }

    /**
     * Helper method for obtaining a list of values from parameter
     * @param name The name of the parameter
     * @return A list of values
     */
    public List getList(String name) {
        Object paramValues = get(name);
        if (paramValues == null) {
            return Collections.emptyList();
        }
        if (paramValues.getClass().isArray()) {
            return Arrays.asList((Object[])paramValues);
        }
        if (paramValues instanceof Collection) {
            return new ArrayList((Collection)paramValues);
        }
        return Collections.singletonList(paramValues);
    }

    public List list(String name) {
        return getList(name);
    }

    public Object put(Object k, Object v) {
        return wrappedMap.put(k, v);
    }

    public Object remove(Object o) {
        return wrappedMap.remove(o);
    }

    public int size() {
        return wrappedMap.size();
    }

    public boolean isEmpty() {
        return wrappedMap.isEmpty();
    }

    public boolean containsKey(Object k) {
        return wrappedMap.containsKey(k);
    }

    public boolean containsValue(Object v) {
        return wrappedMap.containsValue(v);
    }

    public Object get(Object k) {
        return wrappedMap.get(k);
    }

    public void putAll(Map m) {
        wrappedMap.putAll(m);
    }

    public void clear() {
        wrappedMap.clear();
    }

    public Set keySet() {
        return wrappedMap.keySet();
    }

    public Collection values() {
        return wrappedMap.values();
    }

    public Set entrySet() {
        return wrappedMap.entrySet();
    }

    @Override
    public String toString() {
        return DefaultGroovyMethods.toMapString(this);
    }

    public boolean asBoolean() {
        return !isEmpty();
    }
}
