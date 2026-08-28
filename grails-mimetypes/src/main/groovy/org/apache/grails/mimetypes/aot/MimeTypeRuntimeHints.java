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
package org.apache.grails.mimetypes.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers the content negotiation API.
 *
 * <p>Negotiation runs only for a request that states what it accepts. A browser always does;
 * a bare command-line request does not, which is why the absence of these hints can pass an
 * automated check and still fail for every real visitor.</p>
 *
 * @since 8.0
 */
public class MimeTypeRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Types Groovy dispatches on. Named as strings, and registered only when present, so this stays
     * correct for an application that does not use every plugin.
     */
    private static final String[] DISPATCHED_TYPES = {
        "grails.web.mime.MimeType",
        "org.grails.web.mime.DefaultAcceptHeaderParser",
        "org.grails.web.mime.DefaultMimeUtility"
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
