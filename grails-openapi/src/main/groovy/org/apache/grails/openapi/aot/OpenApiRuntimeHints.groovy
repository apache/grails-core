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
package org.apache.grails.openapi.aot

import groovy.transform.CompileStatic

import org.jspecify.annotations.Nullable
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/**
 * Keeps, in an image compiled ahead of time, the optional types the description looks up by name:
 * the file types a body can carry, and the JSON views resolver the validation errors are described
 * from.
 *
 * @since 8.0
 */
@CompileStatic
class OpenApiRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Named as strings and registered only when present, since each comes from a module an
     * application need not use.
     */
    private static final List<String> OPTIONAL_TYPES = [
            'org.springframework.web.multipart.MultipartFile',
            'jakarta.servlet.http.Part',
            'grails.plugin.json.view.mvc.JsonViewResolver'
    ].asImmutable()

    @Override
    void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (String type : OPTIONAL_TYPES) {
            hints.reflection().registerTypeIfPresent(classLoader, type)
        }
    }
}
