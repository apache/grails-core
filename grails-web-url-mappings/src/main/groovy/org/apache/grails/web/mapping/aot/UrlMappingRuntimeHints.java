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
package org.apache.grails.web.mapping.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers the URL mapping API a request is dispatched through.
 *
 * <p>Resolving a request reads the matched mapping reflectively through Groovy, so the
 * declared methods of these types have to survive into the image. The parameter accessor in
 * particular is reached only once a mapping carries parameters, which a walk of an
 * application's pages need not do.</p>
 *
 * @since 8.0
 */
public class UrlMappingRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Types Groovy dispatches on. Named as strings, and registered only when present, so this stays
     * correct for an application that does not use every plugin.
     */
    private static final String[] DISPATCHED_TYPES = {
        "grails.web.mapping.UrlMappingInfo",
        "grails.web.mapping.UrlMapping",
        "grails.web.mapping.UrlMappings",
        "grails.web.mapping.UrlCreator",
        "grails.web.mapping.LinkGenerator",
        "grails.web.mapping.UrlMappingData"
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (String type : DISPATCHED_TYPES) {
            hints.reflection().registerTypeIfPresent(classLoader, type,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }
}
