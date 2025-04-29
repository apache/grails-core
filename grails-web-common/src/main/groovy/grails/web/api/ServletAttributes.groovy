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

package grails.web.api

import groovy.transform.CompileStatic
import groovy.transform.Generated
import org.springframework.context.ApplicationContext
import org.springframework.web.context.support.WebApplicationContextUtils

import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession

/**
 * A trait that adds attributes specific to the Servlet API
 *
 * @author Graeme Rocher
 * @author Jeff Brown
 * 
 */
@CompileStatic
trait ServletAttributes implements WebAttributes {

    private ServletContext servletContext
    private ApplicationContext applicationContext

    @Generated
    HttpServletRequest getRequest() {
        currentRequestAttributes().getCurrentRequest()
    }

    @Generated
    HttpSession getSession() {
        return currentRequestAttributes().getSession()
    }

    /**
     * Obtains the ApplicationContext instance
     * @return The ApplicationContext instance
     */
    @Generated
    ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            this.applicationContext = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
        }
        this.applicationContext
    }

    /**
     * Obtains the HttpServletResponse instance
     *
     * @return The HttpServletResponse instance
     */
    @Generated
    HttpServletResponse getResponse() {
        currentRequestAttributes().getCurrentResponse()
    }

    /**
     * Obtains the ServletContext instance
     *
     * @return The ServletContext instance
     */
    @Generated
    ServletContext getServletContext() {
        if (servletContext == null) {
            servletContext = currentRequestAttributes().getServletContext()
        }
        servletContext
    }


}
