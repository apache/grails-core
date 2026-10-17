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

package grails.encoders;

import java.lang.reflect.Constructor;

import org.springframework.beans.BeanUtils;
import org.springframework.util.ClassUtils;

import org.grails.encoder.impl.BasicXMLEncoder;

/**
 * Escapes text and primitive values for inclusion in XML documents. Any other object is rendered by the XML converter of
 * the {@code grails-xml} module when the application includes it, and is otherwise escaped as its string.
 */
public class XMLEncoder extends BasicXMLEncoder {

    private static final String XML_CONVERTER = "grails.converters.XML";

    private static final Constructor<?> CONVERTER = converter();

    @Override
    protected Object encodeAsXmlObject(Object o) {
        if (CONVERTER == null) {
            return doCharReplacementEncoding(o);
        }
        return CONVERTER.getDeclaringClass().isInstance(o) ? o : BeanUtils.instantiateClass(CONVERTER, o);
    }

    private static Constructor<?> converter() {
        ClassLoader classLoader = XMLEncoder.class.getClassLoader();
        if (!ClassUtils.isPresent(XML_CONVERTER, classLoader)) {
            return null;
        }
        return ClassUtils.getConstructorIfAvailable(ClassUtils.resolveClassName(XML_CONVERTER, classLoader), Object.class);
    }
}
