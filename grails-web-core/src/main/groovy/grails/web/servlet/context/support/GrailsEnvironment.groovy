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
package grails.web.servlet.context.support

import groovy.transform.CompileStatic
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.core.env.PropertySource
import org.springframework.util.StringUtils
import org.springframework.web.context.support.StandardServletEnvironment

import grails.core.GrailsApplication
import grails.util.Environment

/**
 * Bridges Grails' existing environment API with the new Spring 3.1 environment profiles API.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class GrailsEnvironment extends StandardServletEnvironment {

    GrailsApplication grailsApplication

    GrailsEnvironment(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
        getPropertySources().addFirst(new GrailsConfigPropertySource())
        getPropertySources().addFirst(new PropertiesPropertySource('systemProperties', System.getProperties()))
    }

    @Override
    protected Set<String> doGetActiveProfiles() {
        Set<String> activeProfiles = super.doGetActiveProfiles()
        activeProfiles.add(Environment.getCurrent().getName())
        return activeProfiles
    }

    private class GrailsConfigPropertySource extends PropertySource<GrailsApplication> {

        GrailsConfigPropertySource() {
            super(StringUtils.hasText(GrailsEnvironment.this.grailsApplication.getMetadata().getApplicationName()) ?
                    GrailsEnvironment.this.grailsApplication.getMetadata().getApplicationName() : 'grailsApplication',
                    GrailsEnvironment.this.grailsApplication)
        }

        @Override
        Object getProperty(String key) {
            return GrailsEnvironment.this.grailsApplication.getConfig().getProperty(key, Object)
        }
    }

}
