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
package org.grails.web.converters

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import org.codehaus.groovy.runtime.InvokerHelper

/**
 * The methods Grails 8 compiled {@code request.JSON}, {@code request.XML} and {@code as JSON} into in classes compiled
 * with {@code @CompileStatic}, kept so that a library compiled against Grails 8 runs without being recompiled. It is no
 * longer an extension class: the extensions are {@link ConvertersModuleExtension}'s, and {@code request.XML} is the
 * {@code grails-xml} module's.
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @since 3.0
 * @deprecated Scheduled for removal in Grails 11. Recompile against Grails 9, which calls the extensions directly.
 */
@Deprecated(since = '9.0', forRemoval = true)
@CompileStatic
class ConvertersExtension {

    static getJSON(HttpServletRequest request) {
        ConvertersModuleExtension.getJSON(request)
    }

    /**
     * Parses the request body as XML, with the {@code grails-xml} module, as {@code request.XML} does.
     *
     * @throws IllegalStateException without the {@code grails-xml} module
     */
    static getXML(HttpServletRequest request) {
        try {
            // reaches the grails-xml module's extension, since this class is not one
            InvokerHelper.getProperty(request, 'XML')
        }
        catch (MissingPropertyException e) {
            if (e.property != 'XML') {
                throw e
            }
            throw new IllegalStateException('request.XML parses the request body as XML, which requires ' +
                    'org.apache.grails:grails-xml on the classpath', e)
        }
    }

    static <T> T asType(instance, Class<T> clazz) {
        ConvertersModuleExtension.asType(instance, clazz)
    }

    static <T> T  asType(Object[] array, Class<T> clazz) {
        ConvertersModuleExtension.asType(array, clazz)
    }
}
