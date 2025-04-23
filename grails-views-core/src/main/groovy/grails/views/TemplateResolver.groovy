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

package grails.views

import groovy.text.Template
import groovy.transform.CompileStatic

/**
 * Interface for resolving templates
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
interface TemplateResolver {

    /**
     * Resolves the URL to a template using the given path
     *
     * @param path The path
     * @return The URL or null if it cannot be found
     */
    URL resolveTemplate(String path)

    /**
     * Resolves a template class for the path
     *
     * @param path The path
     * @return The Class or null if it cannot be found
     */
    Class<? extends Template> resolveTemplateClass(String path)

    /**
     * Resolves a template class for the path
     *
     * @param packageName the scope to search in (application or plugin name for example)
     * @param path The path
     * @return The Class or null if it cannot be found
     */
    Class<? extends Template> resolveTemplateClass(String packageName, String path)
}