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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;

import grails.converters.JSON;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.json.JSONException;
import org.grails.web.json.JSONWriter;

/**
 * JSON ObjectMarshaller which renders a record as an object of its components, in declaration order, as Spring Boot's
 * JsonMapper does. Each component value is rendered by the converter, as any other value is.
 *
 * @since 9.0
 */
public class RecordMarshaller implements ObjectMarshaller<JSON> {

    public boolean supports(Object object) {
        return object.getClass().isRecord();
    }

    public void marshalObject(Object object, JSON converter) throws ConverterException {
        JSONWriter writer = converter.getWriter();
        try {
            writer.object();
            for (RecordComponent component : object.getClass().getRecordComponents()) {
                component.getAccessor().setAccessible(true);
                writer.key(component.getName());
                converter.convertAnother(component.getAccessor().invoke(object));
            }
            writer.endObject();
        }
        catch (IllegalAccessException | InvocationTargetException | JSONException e) {
            throw new ConverterException("Error converting record " + object.getClass().getName(), e);
        }
    }
}
