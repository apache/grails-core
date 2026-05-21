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

package grails.views.resolve

import groovy.transform.CompileStatic

import grails.util.GrailsNameUtils

/**
 * Utility methods for resolving template names
 *
 * @since 1.1
 * @author Graeme Rocher
 */
@CompileStatic
class TemplateResolverUtils {

    static String shortTemplateNameForClass(Class<?> cls) {
        def propertyName = GrailsNameUtils.getPropertyName(cls)
        return "/$propertyName/_$propertyName"
    }

    static String fullTemplateNameForClass(Class<?> cls) {
        def templateName = cls.name.replace('.', '/')
        def lastSlash = templateName.lastIndexOf('/')
        def stem = templateName.substring(0, lastSlash)
        return "/$stem/_${GrailsNameUtils.getPropertyName(cls.simpleName)}"
    }
}
