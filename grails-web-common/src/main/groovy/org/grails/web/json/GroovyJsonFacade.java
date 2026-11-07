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
package org.grails.web.json;

import java.util.Collection;
import java.util.Map;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

/**
 * Facade for moving JSON parsing and rendering toward groovy-json while preserving Grails JSONElement types.
 *
 * @since 8.1
 */
public final class GroovyJsonFacade {

    private GroovyJsonFacade() {
    }

    public static JSONElement parse(String json) {
        try {
            Object value = new JsonSlurper().parseText(json);
            Object converted = toGrailsJson(value);
            if (converted instanceof JSONElement) {
                return (JSONElement) converted;
            }
            throw new JSONException("JSON text must describe an object or array");
        }
        catch (groovy.json.JsonException | IllegalArgumentException e) {
            throw new JSONException(e);
        }
    }

    public static String toJson(Object value) {
        return JsonOutput.toJson(fromGrailsJson(value));
    }

    private static Object toGrailsJson(Object value) {
        if (value instanceof Map) {
            JSONObject object = new JSONObject();
            ((Map<?, ?>) value).forEach((key, mapValue) -> object.put(String.valueOf(key), toGrailsJson(mapValue)));
            return object;
        }
        if (value instanceof Collection) {
            JSONArray array = new JSONArray();
            for (Object element : (Collection<?>) value) {
                array.put(toGrailsJson(element));
            }
            return array;
        }
        return value;
    }

    private static Object fromGrailsJson(Object value) {
        if (value instanceof JSONObject) {
            return value;
        }
        if (value instanceof JSONArray) {
            return value;
        }
        return value;
    }
}
