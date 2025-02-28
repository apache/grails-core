/*
 * Copyright 2004-2008 the original author or authors.
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
package org.grails.web.converters.marshaller;

import org.grails.web.converters.Converter;
import org.grails.web.converters.exceptions.ConverterException;

/**
 * An ObjectMarshaller is responsible for converting a Java/Groovy Object graph to a serialized form (JSON,XML).
 *
 * The ObjectMarshaller implementation must use a a type parameter - either grails.convereters.JSON or
 * grails.converters.XML and it should to be <strong>thread-safe</strong>.
 *
 * @author Siegfried Puchbauer
 * @since 1.1
 */
@SuppressWarnings("rawtypes")
public interface ObjectMarshaller<T extends Converter> {

    /**
     * Checks wheter the ObjectMarshaller is able/intended to support the given Object
     *
     * @param object the object which is about getting converted
     * @return <code>true</code> if the marshaller can/should perform the marshalling, <code>false</code> otherwise
     */
    boolean supports(Object object);

    /**
     * Performs the conversion
     * @param object the object which is about getting converted
     * @param converter the Converter to use
     * @throws ConverterException on failure
     */
    void marshalObject(Object object, T converter) throws ConverterException;
}
