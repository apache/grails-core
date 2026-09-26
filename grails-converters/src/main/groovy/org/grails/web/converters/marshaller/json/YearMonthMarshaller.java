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

import java.time.YearMonth;

import grails.converters.JSON;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.json.JSONException;

/**
 * JSON ObjectMarshaller which converts a YearMonth to its ISO-8601 {@link YearMonth#toString()} form
 * (e.g. {@code 2026-09}), the same as Spring Boot's default Jackson rendering.
 *
 * @since 8.0
 */
public class YearMonthMarshaller implements ObjectMarshaller<JSON> {

    public boolean supports(Object object) {
        return object instanceof YearMonth;
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
