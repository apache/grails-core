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
package grails.plugin.springsecurity.aot

import groovy.transform.CompileStatic

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar

/**
 * Registers what the plugin reaches reflectively before any bean exists.
 *
 * <p>{@code ConfigSlurper} creates the plugin's default configuration through Groovy's cached constructors, which
 * {@code traceNativeMetadata} does not see, though it records everything the script goes on to call.</p>
 *
 * @since 9.1
 */
@CompileStatic
class SpringSecurityRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection().registerTypeIfPresent(classLoader, 'DefaultSecurityConfig',
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
    }
}
