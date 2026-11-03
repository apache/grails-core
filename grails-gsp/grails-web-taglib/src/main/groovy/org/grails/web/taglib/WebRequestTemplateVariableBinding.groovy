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
package org.grails.web.taglib

import groovy.transform.CompileStatic

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import grails.util.Environment
import org.grails.taglib.AbstractTemplateVariableBinding
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Script binding to be used as the top-level binding in GSP evaluation.
 *
 * @author Lari Hotari
 */
@CompileStatic
class WebRequestTemplateVariableBinding extends AbstractTemplateVariableBinding {

    private static Log log = LogFactory.getLog(WebRequestTemplateVariableBinding)
    private GrailsWebRequest webRequest
    private boolean developmentMode = Environment.isDevelopmentMode()
    private Set<String> requestAttributeVariables = [] as Set

    private static Map<String, LazyRequestBasedValue> lazyRequestBasedValuesMap = [:]

    static {
        Map<String, LazyRequestBasedValue> m = lazyRequestBasedValuesMap
        m['webRequest'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest
            }
        }
        m['request'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getRequest()
            }
        }
        m['response'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getCurrentResponse()
            }
        }
        m['flash'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getFlashScope()
            }
        }
        m['application'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getServletContext()
            }
        }
        // The same object as `application`, under the name the servlet API calls it. A page reading
        // servletContext got nothing at all before, since nothing bound the name.
        m['servletContext'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getServletContext()
            }
        }
        m['applicationContext'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getAttributes().getApplicationContext()
            }
        }
        m['grailsApplication'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getAttributes().getGrailsApplication()
            }
        }
        m['session'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getSession()
            }
        }
        m['params'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getParams()
            }
        }
        m['actionName'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getActionName()
            }
        }
        m['namespace'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getControllerNamespace()
            }
        }
        m['controllerName'] = new LazyRequestBasedValue() {
            Object evaluate(GrailsWebRequest webRequest) {
                return webRequest.getControllerName()
            }
        }
    }

    WebRequestTemplateVariableBinding(GrailsWebRequest webRequest) {
        this.webRequest = webRequest
    }

    Binding findBindingForVariable(String name) {
        Binding binding = super.findBindingForVariable(name)
        if (binding == null) {
            if (webRequest.getRequest().getAttribute(name) != null) {
                requestAttributeVariables << name
                binding = this
            }
        }
        if (binding == null && name in lazyRequestBasedValuesMap) {
            binding = this
        }
        return binding
    }

    boolean isRequestAttributeVariable(String name) {
        return name in requestAttributeVariables
    }

    boolean isVariableCachingAllowed(String name) {
        return !isRequestAttributeVariable(name)
    }

    @Override
    Object getVariable(String name) {
        Object val = getVariablesMap()[name]
        if (val == null && !(name in getVariablesMap()) && webRequest != null) {
            val = webRequest.getRequest().getAttribute(name)
            if (val != null) {
                requestAttributeVariables << name
            }
            else {
                LazyRequestBasedValue lazyValue = lazyRequestBasedValuesMap[name]
                if (lazyValue != null) {
                    val = lazyValue.evaluate(webRequest)
                }
                else {
                    val = resolveMissingVariable(name)
                }

                // warn about missing variables in development mode
                if (val == null && developmentMode) {
                    if (log.isDebugEnabled()) {
                        log.debug("Variable '$name' not found in binding or the value is null.")
                    }
                }
            }
        }
        return val
    }

    protected Object resolveMissingVariable(String name) {
        return null
    }

    private static interface LazyRequestBasedValue {
        Object evaluate(GrailsWebRequest webRequest)
    }

    @SuppressWarnings('unchecked')
    @Override
    Set<String> getVariableNames() {
        if (getVariablesMap().isEmpty()) {
            return lazyRequestBasedValuesMap.keySet()
        }

        Set<String> variableNames = new HashSet<>(lazyRequestBasedValuesMap.keySet())
        variableNames.addAll((Set<String>) getVariablesMap().keySet())
        return variableNames
    }
}
