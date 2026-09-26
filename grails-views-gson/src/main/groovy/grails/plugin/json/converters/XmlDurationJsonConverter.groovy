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

package grails.plugin.json.converters

import javax.xml.datatype.Duration

import groovy.json.JsonGenerator
import groovy.transform.CompileStatic

/**
 * A class to render a {@link javax.xml.datatype.Duration} as json: its lexical {@link Duration#toString()}
 * form (e.g. {@code P1DT2H}), the same as Spring Boot's default Jackson rendering.
 *
 * @since 8.0
 */
@CompileStatic
class XmlDurationJsonConverter implements JsonGenerator.Converter {

    @Override
    boolean handles(Class<?> type) {
        Duration.isAssignableFrom(type)
    }

    @Override
    Object convert(Object value, String key) {
        value.toString()
    }
}
