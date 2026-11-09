/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.gradle.plugin.core

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty

/**
 * Message-bundle options, configured through the nested {@code grails { i18n { } }} block:
 *
 * <pre><code>grails {
 *     i18n {
 *         basenames = ['api', 'api_errors']
 *     }
 * }</code></pre>
 *
 * @since 8.0
 */
@CompileStatic
class GrailsI18nOptions implements Serializable {

    private static final long serialVersionUID = 1L

    /**
     * Base names to treat as declared rather than inferred from file names under
     * {@code grails-app/i18n}.
     *
     * <p>Only needed where the file names are ambiguous. A trailing valid locale identifier is read
     * as a locale suffix, so {@code api_fr.properties} is taken to be base name {@code api} in
     * French; and a file that looks like a mistyped locale variant of an existing base name — such as
     * {@code api_errors.properties} alongside {@code api.properties} — fails the build rather than
     * being silently accepted as a separate bundle. Declaring the base name resolves either case:</p>
     *
     * <pre><code>grails { i18n { basenames = ['api', 'api_errors'] } }</code></pre>
     *
     * <p>Bundles outside {@code grails-app/i18n} are not indexed at all; configure those through
     * {@code spring.messages.basename} in the application's configuration.</p>
     */
    final ListProperty<String> basenames

    @Inject
    GrailsI18nOptions(ObjectFactory objects) {
        this.basenames = objects.listProperty(String)
        this.basenames.convention([])
    }
}
