/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.core.util

import groovy.transform.CompileStatic

/**
 * Simple support class for simplifying include/exclude handling
 *
 * @since 2.3
 * @author Graeme Rocher
 */
@CompileStatic
class IncludeExcludeSupport<T> {

    static final String INCLUDES_PROPERTY = "includes"
    static final String EXCLUDES_PROPERTY = "excludes"

    List<T> defaultIncludes
    List<T> defaultExcludes

    IncludeExcludeSupport(List<T> defaultIncludes = null, List<T> defaultExcludes = []) {
        this.defaultIncludes = defaultIncludes
        this.defaultExcludes = defaultExcludes
    }

    boolean shouldInclude(List<T> incs, List excs, T object) {
        includes(defaultIncludes, object) && includes(incs, object) && !excludes(defaultExcludes, object) && !excludes(excs, object)
    }

    boolean includes(List<T> includes, T object) {
        includes == null || includes.contains(object)
    }

    boolean excludes(List<T> excludes, T object) {
        excludes != null && excludes.contains(object)
    }
}
