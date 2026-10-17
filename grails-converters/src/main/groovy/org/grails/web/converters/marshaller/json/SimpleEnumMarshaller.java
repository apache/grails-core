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

import java.lang.reflect.Method;

import org.springframework.beans.BeanUtils;

import grails.converters.JSON;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ObjectMarshaller;

/**
 * Marshals enums as simple string values (just the enum name) for symmetric serialization/deserialization.
 * This provides round-trip compatibility where POSTing JSON returns the same format when GETting.
 *
 * @since 7.0.2
 */
public class SimpleEnumMarshaller implements ObjectMarshaller<JSON> {

    public boolean supports(Object object) {
        return object.getClass().isEnum();
    }

    public void marshalObject(Object en, JSON json) throws ConverterException {
        try {
            Method nameMethod = BeanUtils.findDeclaredMethod(en.getClass(), "name");
            try {
                json.convertAnother(nameMethod.invoke(en));
            }
            catch (Exception e) {
                json.convertAnother("");
            }
        }
        catch (ConverterException ce) {
            throw ce;
        }
        catch (Exception e) {
            throw new ConverterException("Error converting Enum with class " + en.getClass().getName(), e);
        }
    }
}
