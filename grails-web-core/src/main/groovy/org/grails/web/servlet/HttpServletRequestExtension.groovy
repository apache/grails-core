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
package org.grails.web.servlet

import groovy.transform.CompileStatic
import jakarta.servlet.http.HttpServletRequest
import org.grails.web.util.WebUtils

/**
 * An extension that adds methods to the {@link HttpServletRequest} object
 *
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 * @since 3.0
 *
 */
@CompileStatic
class HttpServletRequestExtension {

    static String getForwardURI(HttpServletRequest request) {
        WebUtils.getForwardURI request
    }

    static getProperty(HttpServletRequest request, String name) {
        def mp = request.getClass().metaClass.getMetaProperty(name)
        mp ? mp.getProperty(request) : request.getAttribute(name)
    }

    static void setProperty(HttpServletRequest request, String name, val) {
        def mp = request.getClass().metaClass.getMetaProperty(name)
        if (mp != null) {
            mp.setProperty(request, val)
        } else {
            request.setAttribute(name, val)
        }
    }

    static propertyMissing(HttpServletRequest request, String name) {
        getProperty request, name
    }

    static propertyMissing(HttpServletRequest request, String name, value) {
        def mp = request.getClass().metaClass.getMetaProperty(name)
        if (mp) {
            mp.setProperty request, value
        } else {
            request.setAttribute name, value
        }
    }

    static getAt(HttpServletRequest request, String name) {
        getProperty request, name
    }

    static putAt(HttpServletRequest request, String name, val) {
        setProperty request, name, val
    }

    static each(HttpServletRequest request, Closure c) {
        def attributeNames = request.getAttributeNames()
        while (attributeNames.hasMoreElements()) {
            String name = attributeNames.nextElement()
            switch (c.parameterTypes.length) {
                case 0:
                    c.call()
                    break
                case 1:
                    c.call([key: name, value: request.getAttribute(name)])
                    break
                default:
                    c.call(name, request.getAttribute(name))
            }
        }
    }

    static find(HttpServletRequest request, Closure<Boolean> c) {
        def result = [:]

        def attributeNames = request.getAttributeNames()
        while (attributeNames.hasMoreElements()) {
            String name = attributeNames.nextElement()
            boolean match = false
            switch (c.parameterTypes.length) {
                case 0:
                    match = c.call()
                    break
                case 1:
                    match = c.call([key: name, value: request.getAttribute(name)])
                    break
                default:
                    match = c.call(name, request.getAttribute(name))
            }
            if (match) {
                result[name] = request.getAttribute(name)
                break
            }
        }
        result
    }

    static findAll(HttpServletRequest request, Closure c) {
        def results = [:]
        def attributeNames = request.getAttributeNames()
        while (attributeNames.hasMoreElements()) {
            String name = attributeNames.nextElement()

            boolean match = false
            switch (c.parameterTypes.length) {
                case 0:
                    match = c.call()
                    break
                case 1:
                    match = c.call([key: name, value: request.getAttribute(name)])
                    break
                default:
                    match = c.call(name, request.getAttribute(name))
            }
            if (match) {
                results[name] = request.getAttribute(name)
            }
        }
        results
    }

    static boolean isXhr(HttpServletRequest instance) {
        // TODO grails.web.xhr.identifier support
        instance.getHeader('X-Requested-With') == "XMLHttpRequest"
    }

    static boolean isGet(HttpServletRequest request) {
        request.method == 'GET'
    }

    static boolean isPost(HttpServletRequest request) {
        request.method == 'POST'
    }
}
