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

import java.sql.Time;

import grails.converters.JSON;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.json.JSONException;

/**
 * JSON ObjectMarshaller which converts a {@link java.sql.Time} to its wall-clock time in the
 * JVM default time zone ({@code HH:mm:ss}, from {@link Time#toString()}), the same as Spring Boot's
 * default Jackson rendering. It is registered ahead of {@link DateMarshaller}, which would
 * otherwise render the {@code Time} as a full date and time.
 *
 * @since 8.0
 */
public class SqlTimeMarshaller implements ObjectMarshaller<JSON> {

    public boolean supports(Object object) {
        return object instanceof Time;
    }

    public void marshalObject(Object object, JSON converter) throws ConverterException {
        try {
            converter.getWriter().value(object.toString());
        }
        catch (JSONException e) {
            throw new ConverterException(e);
        }
    }
}
