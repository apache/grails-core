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

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import grails.converters.JSON;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.json.JSONException;

/**
 * JSON ObjectMarshaller which converts a ZonedDateTime to ISO-8601 format with timezone offset.
 *
 * @since 7.0
 */
public class ZonedDateTimeMarshaller implements ObjectMarshaller<JSON> {

    public boolean supports(Object object) {
        return object instanceof ZonedDateTime;
    }

    public void marshalObject(Object object, JSON converter) throws ConverterException {
        try {
            ZonedDateTime zonedDateTime = (ZonedDateTime) object;
            converter.getWriter().value(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zonedDateTime));
        }
        catch (JSONException e) {
            throw new ConverterException(e);
        }
    }
}
