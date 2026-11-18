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
package org.apache.grails.gradle.plugin.aot

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Covers what the extraction is fingerprinted by, which decides whether it can ever be cached.
 */
class ExtractApplicationTaskSpec extends Specification {

    void 'the extraction is cacheable'() {
        expect:
            ExtractApplicationTask.isAnnotationPresent(CacheableTask)
    }

    void 'the JDK is fingerprinted by what it is rather than by where it lives'() {
        expect: 'the launcher, whose metadata is the part that changes the result'
            ExtractApplicationTask.getMethod('getJavaLauncher').isAnnotationPresent(Nested)
    }

    void 'nothing declares a plain value as an input'() {
        given: 'a path is where a JDK happens to live on the machine that ran the build, so an ' +
                'input carrying one puts that machine in the cache key -- and a cacheable task ' +
                'that can only be hit on the machine that filled it is not one'
            List<String> declared = ExtractApplicationTask.declaredMethods
                    .findAll { Method method -> method.isAnnotationPresent(Input) }
                    .collect { Method method -> method.name }

        expect:
            declared.isEmpty()
    }
}
