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
package grails.util

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.util.HashCodeHelper

import org.apache.grails.core.internal.util.TypeConverters

/**
 * AbstractTypeConvertingMap is a Map with type conversion capabilities.
 *
 * Type converting maps have no inherent ordering. Two maps with identical entries
 * but arranged in a different order internally are considered equal.
 *
 * <h2>Subclasses must not declare JavaBean accessors</h2>
 *
 * A subclass must not declare a no-argument {@code getX()} or {@code isX()} method, because this
 * class implements {@link Map} and Groovy resolves a JavaBean accessor ahead of the map entry of
 * the same name. Such an accessor makes the entry {@code x} unreadable through
 * {@code map.x} and {@code map['x']}, and makes assignment to it fail with
 * {@code ReadOnlyPropertyException}. It applies to statically compiled callers too: the static
 * compiler binds to the declared accessor and never reaches {@code getProperty}/{@code setProperty},
 * so the collision cannot be worked around at runtime.
 *
 * Expose such a value under a name that is not a JavaBean accessor - for example
 * {@code GrailsParameterMap.request()} - or under a method that takes an argument.
 *
 * A setter is a weaker case and is allowed: it leaves reads addressing the map, but assignment to
 * that one name invokes the setter instead of storing an entry, so such an entry must be written
 * with {@link Map#put}. {@code GroovyPageAttributes.setGspTagSyntaxCall(boolean)} is the only one.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @since 1.2
 */
@SuppressWarnings([ 'rawtypes', 'unchecked' ])
@CompileStatic
abstract class AbstractTypeConvertingMap extends GroovyObjectSupport implements Map, Cloneable {

    protected Map wrappedMap

    AbstractTypeConvertingMap() {
        this(new LinkedHashMap())
    }

    AbstractTypeConvertingMap(Map map) {
        if (map == null) map = new LinkedHashMap()
        wrappedMap = map
    }

    boolean equals(Map that) {
        return equals((Object) that)
    }

    @Override
    boolean equals(Object that) {
        if (this.is(that)) {
            return true
        }

        if (that == null) {
            return false
        }

        if (getClass() != that.getClass()) {
            return false
        }

        AbstractTypeConvertingMap thatMap = (AbstractTypeConvertingMap) that
        Map thatWrappedMap = thatMap.@wrappedMap

        if (wrappedMap.is(thatWrappedMap)) {
            return true
        }

        if (wrappedMap.size() != thatWrappedMap.size()) {
            return false
        }

        if (!wrappedMap.keySet().equals(thatWrappedMap.keySet())) {
            return false
        }

        final Iterator it = wrappedMap.keySet().iterator()
        while (it.hasNext()) {
            final Object key = it.next()
            Object thisValue = wrappedMap.get(key)
            Object thatValue = thatWrappedMap.get(key)
            if (thisValue == null && thatValue != null ||
                thisValue != null && thatValue == null ||
                !thisValue.is(thatValue) && !thisValue.equals(thatValue)) {
                return false
            }
        }
        return true
    }

    @Override
    int hashCode() {
        int hashCode = HashCodeHelper.initHash()
        for (Object entry in wrappedMap.entrySet()) {
            hashCode = HashCodeHelper.updateHash(hashCode, entry)
        }
        return hashCode
    }

    /**
     * Helper method for obtaining integer value from parameter
     * @param name The name of the parameter
     * @return The integer value or null if there isn't one
     */
    Byte getByte(String name) {
        return TypeConverters.toByte(get(name))
    }

    Byte getByte(String name, Integer defaultValue) {
        return TypeConverters.toByte(get(name), defaultValue)
    }

    /**
     * Helper method for obtaining Character value from parameter
     * @param name The name of the parameter
     * @return The Character value or null if there isn't one
     */
    Character getChar(String name) {
        return TypeConverters.toCharacter(get(name))
    }

    Character getChar(String name, Integer defaultValue) {
        return TypeConverters.toCharacter(get(name), defaultValue)
    }

    /**
     * Helper method for obtaining integer value from parameter
     * @param name The name of the parameter
     * @return The integer value or null if there isn't one
     */
    Integer getInt(String name) {
        return TypeConverters.toInteger(get(name))
    }

    Integer getInt(String name, Integer defaultValue) {
        return TypeConverters.toInteger(get(name), defaultValue)
    }

    /**
     * Helper method for obtaining long value from parameter
     * @param name The name of the parameter
     * @return The long value or null if there isn't one
     */
    Long getLong(String name) {
        return TypeConverters.toLong(get(name))
    }

    Long getLong(String name, Long defaultValue) {
        return TypeConverters.toLong(get(name), defaultValue)
    }

    /**
    * Helper method for obtaining short value from parameter
    * @param name The name of the parameter
    * @return The short value or null if there isn't one
    */
    Short getShort(String name) {
        return TypeConverters.toShort(get(name))
    }

    Short getShort(String name, Integer defaultValue) {
        return TypeConverters.toShort(get(name), defaultValue)
    }

    /**
    * Helper method for obtaining double value from parameter
    * @param name The name of the parameter
    * @return The double value or null if there isn't one
    */
    Double getDouble(String name) {
        return TypeConverters.toDouble(get(name))
    }

    Double getDouble(String name, Double defaultValue) {
        return TypeConverters.toDouble(get(name), defaultValue)
    }

    /**
     * Helper method for obtaining float value from parameter
     * @param name The name of the parameter
     * @return The double value or null if there isn't one
     */
    Float getFloat(String name) {
        return TypeConverters.toFloat(get(name))
    }

    Float getFloat(String name, Float defaultValue) {
        return TypeConverters.toFloat(get(name), defaultValue)
    }

    /**
     * Helper method for obtaining boolean value from parameter
     * @param name The name of the parameter
     * @return The boolean value or null if there isn't one
     */
    Boolean getBoolean(String name) {
        return TypeConverters.toBoolean(get(name))
    }

    Boolean getBoolean(String name, Boolean defaultValue) {
        Boolean value
        if (containsKey(name)) {
            value = getBoolean(name)
        } else {
            value = defaultValue
        }
        return value
    }

    /**
     * Helper method for obtaining a String value from a parameter
     * @param name The name of the parameter
     * @return The String value or null if there isn't one
     */
    String getString(String name) {
        return TypeConverters.toStringValue(get(name))
    }

    String getString(String name, String defaultValue) {
        return TypeConverters.toStringValue(get(name), defaultValue)
    }

    /**
     * Obtains a date for the parameter name using the default format
     * @param name
     * @return The date or null
     */
    Date getDate(String name) {
        return TypeConverters.toDate(get(name))
    }

    /**
     * Obtains a date from the parameter using the given format
     * @param name The name
     * @param format The format
     * @return The date or null
     */
    Date getDate(String name, String format) {
        return TypeConverters.toDate(get(name), format)
    }

    /**
     * Obtains a date for the given parameter name
     *
     * @param name The name of the parameter
     * @return The date object or null if it cannot be parsed
     */
    Date date(String name) {
        return getDate(name)
    }

    /**
     * Obtains a date for the given parameter name and format
     *
     * @param name The name of the parameter
     * @param format The format
     * @return The date object or null if it cannot be parsed
     */
    Date date(String name, String format) {
        return getDate(name, format)
    }

    /**
     * Obtains a date for the given parameter name and format
     *
     * @param name The name of the parameter
     * @param formats The formats
     * @return The date object or null if it cannot be parsed
     */
    Date date(String name, Collection<String> formats) {
        return getDate(name, formats)
    }

    private Date getDate(String name, Collection<String> formats) {
        return TypeConverters.toDate(get(name), formats)
    }

    /**
     * Helper method for obtaining a list of values from parameter
     * @param name The name of the parameter
     * @return A list of values
     */
    List getList(String name) {
        return TypeConverters.toList(get(name))
    }

    List list(String name) {
        return getList(name)
    }

    Object put(Object k, Object v) {
        return wrappedMap.put(k, v)
    }

    Object remove(Object o) {
        return wrappedMap.remove(o)
    }

    int size() {
        return wrappedMap.size()
    }

    boolean isEmpty() {
        return wrappedMap.isEmpty()
    }

    boolean containsKey(Object k) {
        return wrappedMap.containsKey(k)
    }

    boolean containsValue(Object v) {
        return wrappedMap.containsValue(v)
    }

    Object get(Object k) {
        return wrappedMap.get(k)
    }

    void putAll(Map m) {
        wrappedMap.putAll(m)
    }

    void clear() {
        wrappedMap.clear()
    }

    Set keySet() {
        return wrappedMap.keySet()
    }

    Collection values() {
        return wrappedMap.values()
    }

    Set entrySet() {
        return wrappedMap.entrySet()
    }

    @Override
    String toString() {
        return DefaultGroovyMethods.toMapString(this)
    }

    boolean asBoolean() {
        return !isEmpty()
    }

}
