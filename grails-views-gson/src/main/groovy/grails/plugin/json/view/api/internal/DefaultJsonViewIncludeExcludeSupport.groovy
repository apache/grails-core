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

package grails.plugin.json.view.api.internal

import groovy.transform.CompileStatic

import org.grails.core.util.IncludeExcludeSupport

@CompileStatic
class DefaultJsonViewIncludeExcludeSupport extends IncludeExcludeSupport<String> {

    DefaultJsonViewIncludeExcludeSupport(List<String> defaultIncludes, List<String> defaultExcludes) {
        super(defaultIncludes, defaultExcludes)
    }

    @Override
    boolean shouldInclude(List<String> incs, List excs, String object) {
        def i = object.lastIndexOf('.')
        String unqualified = i > -1 ? object.substring(i + 1) : null
        return super.shouldInclude(incs, excs, object) && (unqualified == null || (includes(defaultIncludes, unqualified) && !excludes(defaultExcludes, unqualified)))
    }

    @Override
    boolean includes(List<String> includes, String object) {
        includes == null ||
                includes.contains(object) ||
                includes.any { object.startsWith(it + ".") } ||
                includes.any { it.startsWith(object + ".") }
    }
}
