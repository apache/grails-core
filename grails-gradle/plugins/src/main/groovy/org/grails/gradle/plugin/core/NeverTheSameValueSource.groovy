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
package org.grails.gradle.plugin.core

import groovy.transform.CompileStatic

import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters

/**
 * A value that is another one every time it is read. Read while a build is configured, it is an input
 * of the configuration cache that never matches, so the entry of that build is not used again.
 *
 * <p>That is for a configuration that came out wrong for a reason outside the build and was kept
 * instead of failing it: a dependency a repository did not have, which the next build has to look for
 * again.</p>
 *
 * @since 9.0
 */
@CompileStatic
abstract class NeverTheSameValueSource implements ValueSource<String, ValueSourceParameters.None> {

    @Override
    String obtain() {
        return UUID.randomUUID().toString()
    }
}
