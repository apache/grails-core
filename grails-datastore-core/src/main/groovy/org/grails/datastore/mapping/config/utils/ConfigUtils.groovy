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
package org.grails.datastore.mapping.config.utils

import groovy.transform.CompileStatic

import org.springframework.core.convert.ConversionService

import org.grails.datastore.mapping.model.types.conversion.DefaultConversionService

/**
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@Deprecated
class ConfigUtils {
    private static ConversionService conversionService = new DefaultConversionService()

    static <T> T read(Class<T> type, String key, Map<String, String> config, T defaultValue) {
        Object value = config.get(key)
        return !value ? defaultValue : conversionService.convert(value, type)
    }
}
