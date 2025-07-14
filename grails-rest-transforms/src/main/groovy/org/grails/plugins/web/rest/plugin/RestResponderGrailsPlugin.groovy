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
package org.grails.plugins.web.rest.plugin

import grails.config.Settings
import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.plugins.Plugin
import grails.rest.Resource
import grails.util.GrailsUtil
import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.grails.core.artefact.ControllerArtefactHandler
import org.grails.core.artefact.DomainClassArtefactHandler
import org.grails.plugins.web.rest.render.DefaultRendererRegistry

/**
 * @since 2.3
 * @author Graeme Rocher
 */
class RestResponderGrailsPlugin extends Plugin {

    private static final Log LOG = LogFactory.getLog(RestResponderGrailsPlugin)

    def version = GrailsUtil.getGrailsVersion()
    def loadBefore = ['controllers']
    def observe = ['domainClass']

    GrailsApplication grailsApplication

    @Override
    Closure doWithSpring() {
        { ->

            def application = grailsApplication
            RestResponderGrailsPlugin.registryResourceControllers(application)

            rendererRegistry(DefaultRendererRegistry) { bean ->
                bean.lazyInit = true
                modelSuffix = application.config.getProperty(Settings.SCAFFOLDING_DOMAIN_SUFFIX, '')
            }
        }
    }

    @Override
    void onChange(Map<String, Object> event) {
        RestResponderGrailsPlugin.registryResourceControllers(grailsApplication)
    }

    @CompileStatic
    static void registryResourceControllers(GrailsApplication app) {
        for (GrailsClass grailsClass in app.getArtefacts(DomainClassArtefactHandler.TYPE)) {
            final clazz = grailsClass.clazz
            if (clazz.getAnnotation(Resource)) {
                String controllerClassName = "${clazz.name}Controller"
                if (!app.getArtefact(ControllerArtefactHandler.TYPE, controllerClassName)) {
                    try {
                        app.addArtefact(ControllerArtefactHandler.TYPE, app.classLoader.loadClass(controllerClassName))
                    } catch (ClassNotFoundException cnfe) {

                    }
                }
            }
        }
    }
}
