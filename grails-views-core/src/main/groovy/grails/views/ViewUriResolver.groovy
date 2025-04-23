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

import groovy.transform.CompileStatic

/**
 * A ViewUriResolver is response for response template and view URIs using Grails' conventions.
 *
 *
 * @author Graeme Rocher
 */
@CompileStatic
interface ViewUriResolver {
    /**
     * Resolves a template URI for the given path
     *
     * Example: assert resolveTemplateUri('foo', 'bar') == /foo/_bar.gson
     *
     * @param controllerName The controller name
     * @param path The path to the template
     * @return The template URI
     */
    String resolveTemplateUri(String controllerName, String templateName)

    /**
     * Resolves a template URI for the given path
     *
     * Example: assert resolveTemplateUri('api', 'foo', 'bar') == /api/foo/_bar.gson
     *
     * @param controllerNamespace The controller controllerNamespace
     * @param controllerName The controller name
     * @param path The path to the template
     * @return The template URI
     */
    String resolveTemplateUri(String controllerNamespace, String controllerName, String templateName)
}