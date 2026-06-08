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

package grails.plugin.springsecurity.web.filter

import groovy.transform.CompileStatic
import org.springframework.util.Assert

import jakarta.servlet.http.HttpServletRequest

@CompileStatic
class HttpMethodOverrideDetector {

    /** Default method parameter: <code>_method</code> */
    public static final String DEFAULT_METHOD_PARAM = "_method"

    private String methodParam = DEFAULT_METHOD_PARAM
    public static final String HEADER_X_HTTP_METHOD_OVERRIDE = "X-HTTP-Method-Override"

    /**
     * Set the parameter name to look for HTTP methods.
     * @see #DEFAULT_METHOD_PARAM
     */
    void setMethodParam(String methodParam) {
        Assert.hasText(methodParam, "'methodParam' must not be empty")
        this.methodParam = methodParam
    }

    String getHttpMethodOverride(HttpServletRequest request) {
        String httpMethod = request.getParameter(methodParam)

        if (httpMethod == null) {
            httpMethod = request.getHeader(HEADER_X_HTTP_METHOD_OVERRIDE)
        }
        return httpMethod == null ? null : httpMethod.toUpperCase()
    }

}
