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
package org.grails.web.context

import groovy.transform.CompileStatic

import jakarta.servlet.ServletContext

import org.springframework.context.ApplicationContext
import org.springframework.web.context.ContextLoader
import org.springframework.web.context.support.WebApplicationContextUtils

import grails.core.GrailsApplication
import org.grails.core.support.GrailsApplicationDiscoveryStrategy
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Strategy for discovering the GrailsApplication and ApplicationContext instances in the Servlet environment
 *
 * @author Graeme Rocher
 * @since 2.4
 */
@CompileStatic
class ServletEnvironmentGrailsApplicationDiscoveryStrategy implements GrailsApplicationDiscoveryStrategy{
    ServletContext servletContext
    ApplicationContext applicationContext

    ServletEnvironmentGrailsApplicationDiscoveryStrategy(ServletContext servletContext, ApplicationContext applicationContext = null) {
        this.servletContext = servletContext
        this.applicationContext = applicationContext
    }

    @Override
    public GrailsApplication findGrailsApplication() {
        def context = findApplicationContext()
        if(context) {
            return context.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication)
        }
        else {
            def webReq = GrailsWebRequest.lookup()
            if(webReq) {
                webReq.applicationContext?.getBean(GrailsApplication.APPLICATION_ID, GrailsApplication)
            }
        }
    }

    @Override
    public ApplicationContext findApplicationContext() {
        if (applicationContext != null) {
            return applicationContext
        }
        if(servletContext == null) {
            return ContextLoader.currentWebApplicationContext
        }
        def context = WebApplicationContextUtils.getWebApplicationContext(servletContext)
        if(context) {
            return context
        }
        return GrailsWebRequest.lookup()?.applicationContext
    }
}
