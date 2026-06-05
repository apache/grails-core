/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.util;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.util.HashCodeHelper;

import org.grails.util.TypeConverters;

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
    protected Map wrappedMap;

    public AbstractTypeConvertingMap() {
        this(new LinkedHashMap());
    }

    public AbstractTypeConvertingMap(Map map) {
        if (map == null) map = new LinkedHashMap();
        wrappedMap = map;
    }

    public boolean equals(Map that) {
        return equals((Object) that);
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

        AbstractTypeConvertingMap thatMap = (AbstractTypeConvertingMap) that;

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
        return TypeConverters.toByte(get(name));
    }

    public Byte getByte(String name, Integer defaultValue) {
        return TypeConverters.toByte(get(name), defaultValue);
    }

    /**
     * Helper method for obtaining Character value from parameter
     * @param name The name of the parameter
     * @return The Character value or null if there isn't one
     */
    public Character getChar(String name) {
        return TypeConverters.toCharacter(get(name));
    }

    public Character getChar(String name, Integer defaultValue) {
        return TypeConverters.toCharacter(get(name), defaultValue);
    }

    /**
     * Helper method for obtaining integer value from parameter
     * @param name The name of the parameter
     * @return The integer value or null if there isn't one
     */
    public Integer getInt(String name) {
        return TypeConverters.toInteger(get(name));
    }

    public Integer getInt(String name, Integer defaultValue) {
        return TypeConverters.toInteger(get(name), defaultValue);
    }

    /**
     * Helper method for obtaining long value from parameter
     * @param name The name of the parameter
     * @return The long value or null if there isn't one
     */
    public Long getLong(String name) {
        return TypeConverters.toLong(get(name));
    }

    public Long getLong(String name, Long defaultValue) {
        return TypeConverters.toLong(get(name), defaultValue);
    }

    /**
    * Helper method for obtaining short value from parameter
    * @param name The name of the parameter
    * @return The short value or null if there isn't one
    */
    public Short getShort(String name) {
        return TypeConverters.toShort(get(name));
    }

    public Short getShort(String name, Integer defaultValue) {
        return TypeConverters.toShort(get(name), defaultValue);
    }

    /**
    * Helper method for obtaining double value from parameter
    * @param name The name of the parameter
    * @return The double value or null if there isn't one
    */
    public Double getDouble(String name) {
        return TypeConverters.toDouble(get(name));
    }

    public Double getDouble(String name, Double defaultValue) {
        return TypeConverters.toDouble(get(name), defaultValue);
    }

    /**
     * Helper method for obtaining float value from parameter
     * @param name The name of the parameter
     * @return The double value or null if there isn't one
     */
    public Float getFloat(String name) {
        return TypeConverters.toFloat(get(name));
    }

    public Float getFloat(String name, Float defaultValue) {
        return TypeConverters.toFloat(get(name), defaultValue);
    }

    /**
     * Helper method for obtaining boolean value from parameter
     * @param name The name of the parameter
     * @return The boolean value or null if there isn't one
     */
    public Boolean getBoolean(String name) {
        return TypeConverters.toBoolean(get(name));
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
     * Helper method for obtaining a String value from a parameter
     * @param name The name of the parameter
     * @return The String value or null if there isn't one
     */
    public String getString(String name) {
        return TypeConverters.toStringValue(get(name));
    }

    public String getString(String name, String defaultValue) {
        return TypeConverters.toStringValue(get(name), defaultValue);
    }

    /**
     * Obtains a date for the parameter name using the default format
     * @param name
     * @return The date or null
     */
    public Date getDate(String name) {
        return TypeConverters.toDate(get(name));
    }

    /**
     * Obtains a date from the parameter using the given format
     * @param name The name
     * @param format The format
     * @return The date or null
     */
    public Date getDate(String name, String format) {
        return TypeConverters.toDate(get(name), format);
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
        return TypeConverters.toDate(get(name), formats);
    }

    /**
     * Helper method for obtaining a list of values from parameter
     * @param name The name of the parameter
     * @return A list of values
     */
    public List getList(String name) {
        return TypeConverters.toList(get(name));
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
