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
package org.grails.taglib;

import java.util.LinkedHashMap;
import java.util.Map;

import grails.util.TypeConvertingMap;

/**
 * Defines attributes passed to a GSP tag. Mixes in TypeConvertingMap for ease of type conversion.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @since 1.2
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class GroovyPageAttributes extends TypeConvertingMap implements Cloneable {
    boolean gspTagSyntaxCall = true;

    public GroovyPageAttributes() {
        super();
    }

    public GroovyPageAttributes(Map map) {
        this(map, true);
    }

    public GroovyPageAttributes(Map map, boolean gspTagSyntaxCall) {
        super(map);
        this.gspTagSyntaxCall = gspTagSyntaxCall;
    }

    /**
     * Whether the tag was invoked with GSP tag syntax rather than as a method call.
     *
     * <p>Deliberately not named {@code isGspTagSyntaxCall()}. This class implements {@link Map},
     * and a JavaBean accessor on a map shadows the map entry of the same name, which made an
     * attribute named {@code gspTagSyntaxCall} unreadable. See
     * {@link grails.util.AbstractTypeConvertingMap} for the rule.
     *
     * @return {@code true} when invoked with GSP tag syntax
     * @since 8.0
     */
    public boolean gspTagSyntaxCall() {
        return gspTagSyntaxCall;
    }

    public void setGspTagSyntaxCall(boolean gspTagSyntaxCall) {
        this.gspTagSyntaxCall = gspTagSyntaxCall;
    }

    /**
     * Stores an attribute, so that {@code attrs['name'] = value} always writes a map entry.
     *
     * <p>Without it, Groovy 5 resolves subscript assignment of {@code gspTagSyntaxCall} to
     * {@link #setGspTagSyntaxCall(boolean)}, while Groovy 6 routes it through {@link Map#put}.
     * Declaring it gives both the Groovy 6 behaviour. Dotted assignment,
     * {@code attrs.gspTagSyntaxCall = value}, still invokes the setter.
     *
     * @param key the attribute name
     * @param value the attribute value
     * @since 8.0
     */
    public void putAt(String key, Object value) {
        put(key, value);
    }

    @Override
    public Object clone() {
        return new GroovyPageAttributes(new LinkedHashMap(wrappedMap));
    }
}
