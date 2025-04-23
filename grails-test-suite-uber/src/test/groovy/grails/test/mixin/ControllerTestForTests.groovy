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

package grails.test.mixin

import grails.artefact.Artefact
import grails.persistence.Entity
import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import org.springframework.web.servlet.support.RequestContextUtils
import spock.lang.Specification

class ControllerTestForTests extends Specification implements ControllerUnitTest<SimpleController>, DomainUnitTest<Simple> {

    void testIndex() {
        when:
        controller.index()

        then:
        response.text == 'Hello'
    }

    void testTotal() {
        when:
        controller.total()

        then:
        response.text == "Total = 0"
    }

    void testLocaleResolver() {
        when:
        def localeResolver = applicationContext.localeResolver
        request.addPreferredLocale(Locale.FRANCE)

        then:
        localeResolver.resolveLocale(request) == Locale.FRANCE
    }
    
    void testLocaleResolverAttribute() {
        expect:
        RequestContextUtils.getLocaleResolver(request) == applicationContext.localeResolver
    }

}
@Artefact('Controller')
class SimpleController {
    def index = {
        render "Hello"
    }

    def total = {
        render "Total = ${Simple.count()}"
    }
}
@Entity
class Simple {
    Long id
    Long version
    String name
}
