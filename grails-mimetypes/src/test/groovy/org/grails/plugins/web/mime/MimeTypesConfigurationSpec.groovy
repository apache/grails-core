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
package org.grails.plugins.web.mime

import grails.core.DefaultGrailsApplication
import grails.spring.BeanBuilder
import grails.web.mime.MimeType
import org.springframework.context.ApplicationContext
import spock.lang.Specification

class MimeTypesConfigurationSpec extends Specification {

    void "test when no mimeTypes configured then default should be used"() {
        setup:
        def application = new DefaultGrailsApplication()
        def bb = new BeanBuilder()
        bb.beans {
            grailsApplication = application
            mimeConfiguration(MimeTypesConfiguration, application, [])
        }
        ApplicationContext applicationContext = bb.createApplicationContext()

        when:
        MimeTypesConfiguration mimeTypesConfiguration = applicationContext.getBean(MimeTypesConfiguration)

        then:
        MimeType.createDefaults() == mimeTypesConfiguration.mimeTypes()

    }
}
