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
package org.apache.grails.core.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers the Caffeine classes the framework's own caches are built from.
 *
 * <p>Caffeine generates a class for every combination of the options a cache can be built with, and loads
 * the one a builder asks for by name: a cache class, and the class of the entries it holds. An image keeps
 * one only when something asked for it. The metadata GraalVM publishes for Caffeine registers the entry
 * class of a size-bounded cache only once a cache that also records statistics has been reached, and the
 * framework's caches do not record them, so an image built without these could not create the URL
 * mappings, failing with "Cannot reflectively invoke constructor 'PSMS()'".</p>
 *
 * <p>Named rather than derived, because the names are Caffeine's and follow from the options the framework
 * uses, which are few: a bound on the number of entries (most caches), a bound on their total weight (the
 * URL mappings and the URL creators), weak keys (the codecs' metadata), and a bound on the number of
 * entries with a removal listener (a datastore session's pending inserts, updates and deletes).</p>
 *
 * @since 9.0
 */
public class CaffeineRuntimeHints implements RuntimeHintsRegistrar {

    private static final String PACKAGE = "com.github.benmanes.caffeine.cache.";

    /**
     * The classes the framework's caches are built from: for each set of options, the cache class Caffeine
     * creates and the class of the entries it holds.
     */
    private static final String[] CLASSES = {
        // maximumSize
        "SSMS", "PSMS",
        // maximumWeight with a weigher
        "SSMW", "PSMW",
        // weakKeys
        "WS", "FS",
        // maximumSize with a removal listener
        "SSLMS"
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (String name : CLASSES) {
            hints.reflection().registerTypeIfPresent(classLoader, PACKAGE + name,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.ACCESS_DECLARED_FIELDS);
        }
    }

}
