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
package grails.artefact.controller.support

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import org.grails.web.util.HiddenHttpMethod

/**
 * A helper class for interrogating the allowedMethods property.
 *
 * @author Jeff Brown
 * @since 3.0
 *
 */
@CompileStatic
class AllowedMethodsHelper {

    static boolean isAllowed(final String actionName, final HttpServletRequest request, final Map allowedMethods) {
        boolean isAllowed = true
        if (allowedMethods?.containsKey(actionName)) {
            // The method the handler was selected for: the overridden one where the dispatcher resolved a
            // _method, and the request's own where a servlet filter already rewrote it.
            def method = HiddenHttpMethod.effectiveMethod(request)
            def value = allowedMethods[actionName]
            if (value instanceof String) {
                isAllowed = method.equalsIgnoreCase(value)
            } else if (value instanceof List) {
                isAllowed = value.find { s -> method.equalsIgnoreCase((String) s) }
            } else {
                isAllowed = false
            }
        }
        isAllowed
    }
}
