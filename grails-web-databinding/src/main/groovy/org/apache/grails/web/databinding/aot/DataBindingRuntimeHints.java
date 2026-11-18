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
package org.apache.grails.web.databinding.aot;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Registers the data binding API a request is bound through.
 *
 * <p>Binding is reached only by a request that carries a body or parameters to bind, so a
 * read-only walk of an application never records it and the absence shows up the first time
 * a form is submitted.</p>
 *
 * @since 8.0
 */
public class DataBindingRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Types Groovy dispatches on. Named as strings, and registered only when present, so this stays
     * correct for an application that does not use every plugin.
     */
    private static final String[] DISPATCHED_TYPES = {
        "grails.web.databinding.WebDataBinding",
        "grails.web.databinding.DataBindingUtils",
        "grails.databinding.DataBindingSource",
        "grails.databinding.BindingHelper",
        "grails.databinding.converters.ValueConverter",
        // binding asks a target type whether it is an array, and Groovy makes that call
        // reflectively on the Class object rather than directly
        "java.lang.Class"
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
