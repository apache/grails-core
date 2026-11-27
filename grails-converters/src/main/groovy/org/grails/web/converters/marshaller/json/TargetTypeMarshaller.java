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
package org.grails.web.converters.marshaller.json;

import grails.converters.JSON;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.jackson.DomainClassRenderer;
import org.grails.web.converters.jackson.DomainClassRendering;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * Marshals the instances of a type, and of its subclasses, with another marshaller, as a closure registered for the type
 * with {@code JSON.registerObjectMarshaller(Class, Closure)} would, while the converter still sees how the other renders
 * a domain class instance and whether it writes it with the converter's {@code JsonMapper}.
 *
 * @since 9.0
 */
public class TargetTypeMarshaller implements ObjectMarshaller<JSON>, DomainClassRenderer {

    private final Class<?> targetType;

    private final ObjectMarshaller<JSON> marshaller;

    /**
     * @param targetType the type whose instances are marshalled
     * @param marshaller the marshaller that marshals them
     */
    public TargetTypeMarshaller(Class<?> targetType, ObjectMarshaller<JSON> marshaller) {
        this.targetType = targetType;
        this.marshaller = marshaller;
    }

    /**
     * @return the marshaller that marshals the instances
     */
    public ObjectMarshaller<JSON> getMarshaller() {
        return marshaller;
    }

    @Override
    public boolean supports(Object object) {
        return targetType.isAssignableFrom(object.getClass());
    }

    /**
     * Marshals the instance with the other marshaller, reporting any failure as a {@link ConverterException}, as the
     * converter reports the failure of a registered closure.
     */
    @Override
    public void marshalObject(Object object, JSON converter) throws ConverterException {
        try {
            marshaller.marshalObject(object, converter);
        }
        catch (ConverterException e) {
            throw e;
        }
        catch (Exception e) {
            // also a checked exception, which a Groovy marshaller may throw without declaring it
            throw new ConverterException(e);
        }
    }

    /**
     * @return how the marshaller renders the instances of a domain class, or {@code null} where it renders none
     */
    @Override
    public DomainClassRendering getRendering(JSON json, Class<?> type) {
        return marshaller instanceof DomainClassRenderer renderer ? renderer.getRendering(json, type) : null;
    }
}
