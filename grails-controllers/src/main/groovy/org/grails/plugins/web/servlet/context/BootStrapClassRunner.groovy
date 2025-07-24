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
package org.grails.plugins.web.servlet.context

import groovy.transform.CompileStatic
import groovy.util.logging.Commons

import jakarta.servlet.ServletContext

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.web.context.ServletContextAware
import org.springframework.web.context.WebApplicationContext

import grails.core.GrailsApplication
import grails.core.GrailsApplicationLifeCycleAdapter
import grails.core.GrailsClass
import grails.core.support.GrailsApplicationAware
import grails.plugins.GrailsPluginManager
import grails.plugins.PluginManagerAware
import grails.web.servlet.bootstrap.GrailsBootstrapClass
import org.grails.web.servlet.boostrap.BootstrapArtefactHandler
import org.grails.web.servlet.context.GrailsConfigUtils

/**
 * Runs the BootStrap classes on startup
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@Commons
class BootStrapClassRunner extends GrailsApplicationLifeCycleAdapter implements GrailsApplicationAware, ServletContextAware, ApplicationContextAware, PluginManagerAware {

    GrailsApplication grailsApplication
    GrailsPluginManager pluginManager
    ApplicationContext applicationContext
    ServletContext servletContext

    @Override
    void onStartup(Map<String, Object> event) {
        if(grailsApplication && applicationContext && servletContext) {
            GrailsConfigUtils.executeGrailsBootstraps(grailsApplication, (WebApplicationContext)applicationContext, servletContext, pluginManager )
        }
    }

    @Override
    void onShutdown(Map<String, Object> event) {
        if(grailsApplication && applicationContext) {
            for(GrailsClass cls in grailsApplication.getArtefacts(BootstrapArtefactHandler.TYPE)) {
                try {
                    ((GrailsBootstrapClass)cls).callDestroy()
                } catch (Throwable e) {
                     log.error("Error occurred running Bootstrap destroy method: " + e.getMessage(), e)
                }
            }
        }
    }
}
