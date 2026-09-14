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
package org.grails.web.mapping

import groovy.transform.CompileStatic
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.util.UriUtils

import grails.util.GrailsStringUtils
import grails.web.mapping.UrlMappingInfo
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Abstract super class providing pass functionality for configuring a UrlMappingInfo.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
@SuppressWarnings(['rawtypes', 'unchecked'])
@CompileStatic
abstract class AbstractUrlMappingInfo implements UrlMappingInfo {

    private Map<String, Object> params = Collections.emptyMap()

    Map<String, Object> getParams() {
        return params
    }

    void setParams(final Map newParams) {
        Collection keys = newParams.keySet()
        keys = new ArrayList(keys)
        Collections.sort((List) keys, new Comparator() {
            int compare(Object leftKey, Object rightKey) {
                Object leftValue = newParams.get(leftKey)
                Object rightValue = newParams.get(rightKey)
                boolean leftIsClosure = leftValue instanceof Closure
                boolean rightIsClosure = rightValue instanceof Closure
                if (leftIsClosure && rightIsClosure) return 0
                if (leftIsClosure && !rightIsClosure) return 1
                if (rightIsClosure && !leftIsClosure) return -1
                return 0
            }
        })
        Map<String, Object> sortedParams = new LinkedHashMap<>()
        for (Object key in keys) {
            sortedParams.put(String.valueOf(key), newParams.get(key))
        }
        this.params = Collections.unmodifiableMap(sortedParams)
    }

    void configure(GrailsWebRequest webRequest) {
        populateParamsForMapping(webRequest)
    }

    /**
     * Populates request parameters for the given UrlMappingInfo instance using the GrailsWebRequest
     *
     * @param webRequest The Map instance
     * @see org.grails.web.servlet.mvc.GrailsWebRequest
     */
    protected void populateParamsForMapping(GrailsWebRequest webRequest) {
        Map dispatchParams = webRequest.getParams()
        String encoding = webRequest.getRequest().getCharacterEncoding()
        if (encoding == null) encoding = 'UTF-8'

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String name = entry.getKey()
            Object param = entry.getValue()
            if (param instanceof Closure) {
                param = evaluateNameForValue(param)
            }
            if (param instanceof CharSequence) {
                param = param.toString()
            }
            dispatchParams.put(name, param)
        }

        final String viewName = getViewName()
        if (viewName == null && getURI() == null) {
            webRequest.setControllerNamespace(getNamespace())
            webRequest.setControllerName(getControllerName())
            webRequest.setActionName(getActionName())
        }

        String id = getId()
        if (!GrailsStringUtils.isBlank(id)) {
            try {
                dispatchParams.put(GrailsWebRequest.ID_PARAMETER, UriUtils.decode(id, encoding))
            } catch (IllegalArgumentException e) {
                dispatchParams.put(GrailsWebRequest.ID_PARAMETER, id)
            }
        }
    }

    protected String evaluateNameForValue(Object value) {
        if (value instanceof CharSequence) {
            return value.toString().trim()
        }
        else {
            GrailsWebRequest webRequest = (GrailsWebRequest) RequestContextHolder.getRequestAttributes()
            return evaluateNameForValue(value, webRequest)
        }
    }

    protected String evaluateNameForValue(Object value, GrailsWebRequest webRequest) {
        if (value == null) {
            return null
        }

        String name
        if (value instanceof Closure) {
            Closure callable = (Closure) value
            final Closure cloned = (Closure) callable.clone()
            cloned.setDelegate(webRequest)
            cloned.setResolveStrategy(Closure.DELEGATE_FIRST)
            Object result = cloned.call()
            name = result != null ? result.toString() : null
        }
        else if (value instanceof Map) {
            Map httpMethods = (Map) value
            name = (String) httpMethods.get(webRequest.getCurrentRequest().getMethod())
        }
        else {
            name = value.toString()
        }
        return name != null ? name.trim() : null
    }

    /**
     * The redirect information should be a String or a Map.  If it
     * is a String that string is the URI to redirect to.  If it is
     * a Map, that Map may contain any entries supported as arguments
     * to the dynamic redirect(Map) method on a controller.
     *
     * @return redirect information for this url mapping, null if no redirect is specified
     */
    Object getRedirectInfo() {
        return null
    }

}
