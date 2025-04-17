/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.taglib;

import grails.util.TypeConvertingMap;

import java.util.LinkedHashMap;
import java.util.Map;

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
        this.gspTagSyntaxCall=gspTagSyntaxCall;
    }

    public boolean isGspTagSyntaxCall() {
        return gspTagSyntaxCall;
    }

    public void setGspTagSyntaxCall(boolean gspTagSyntaxCall) {
        this.gspTagSyntaxCall=gspTagSyntaxCall;
    }

    @Override
    public Object clone() {
        return new GroovyPageAttributes(new LinkedHashMap(wrappedMap));
    }
}
