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
package org.grails.plugins.domain

import grails.plugins.Plugin
import grails.util.Environment
import grails.util.GrailsUtil
import org.grails.datastore.mapping.config.Settings as DatastoreSettings

/**
 * Configures the domain classes in the spring context.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
class DomainClassGrailsPlugin extends Plugin {

    def watchedResources = ['file:./grails-app/domain/**/*.groovy',
                            'file:./plugins/*/grails-app/domain/**/*.groovy']

    def version = GrailsUtil.getGrailsVersion()
    def dependsOn = [i18n: version]
    def loadAfter = ['controllers', 'dataSource']

    @Override
    Closure doWithSpring() {
        { ->
            // Set default for auto-timestamp annotation caching based on environment if not explicitly configured
            def config = grailsApplication.config
            if (!config.containsProperty(DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS)) {
                // Not configured - disable caching in development mode to support class reloading
                config.put(DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS, !Environment.isDevelopmentMode())
            }
        }
    }
}
