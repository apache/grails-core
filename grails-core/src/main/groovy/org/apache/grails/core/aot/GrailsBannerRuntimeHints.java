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
import org.springframework.aot.hint.TypeReference;

import grails.boot.GrailsBanner;

/**
 * Keeps the banner's option enums usable in an image, including the ones an application has not
 * turned on.
 *
 * <p>A Groovy enum builds its constants in a static initialiser that reaches its own constructor
 * through the metaclass rather than calling it. An image keeps a constructor only when something
 * has asked it to, so the enum initialises to
 * {@code Could not find matching constructor for: ...OptionalVersionOption(String, Integer)} --
 * thrown while printing the banner, before anything the application wrote has run.</p>
 *
 * <p>Which enums are reached depends on configuration: the optional ones are touched only where
 * {@code grails.banner.versions.include} names something. So an image traced with nothing included
 * carries no record of them, and an application that later asks for the Tomcat version gets a
 * start-up failure out of a banner setting. They are registered here rather than left to a trace
 * for that reason -- all three are small, and the alternative is metadata that depends on which
 * options happened to be on the day it was collected.</p>
 *
 * @since 8.0
 */
public class GrailsBannerRuntimeHints implements RuntimeHintsRegistrar {

    private static final Class<?>[] OPTION_TYPES = {
        GrailsBanner.VersionOption.class,
        GrailsBanner.DefaultVersionOption.class,
        GrailsBanner.OptionalVersionOption.class
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (Class<?> type : OPTION_TYPES) {
            hints.reflection().registerType(type,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS);
            // The name Groovy looks up before deciding an enum has no metaclass of its own. It is
            // not a class anyone writes, so it is named rather than referenced.
            hints.reflection().registerType(
                    TypeReference.of("groovy.runtime.metaclass." + type.getName() + "MetaClass"));
        }
    }
}
