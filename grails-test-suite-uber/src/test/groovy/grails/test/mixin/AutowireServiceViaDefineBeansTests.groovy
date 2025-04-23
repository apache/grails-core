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
import grails.testing.web.controllers.ControllerUnitTest;
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import spock.lang.Specification

/**
 * Tests that services can be autowired into controllers via defineBeans
 */
class AutowireServiceViaDefineBeansTests extends Specification implements ControllerUnitTest<SpringController> {

    void testThatBeansAreWired() {
        given:
        defineBeans {
            springService(SpringService)
        }

        expect:
        applicationContext.getBean("springService") instanceof SpringService

        when:
        controller.index()
        controller.index()

        then:
        noExceptionThrown()
    }
}

@Artefact("Controller")
class SpringController implements ApplicationContextAware {
    ApplicationContext applicationContext
    SpringService springService
    def index() {
        applicationContext.getBean("springService") instanceof SpringService
        assert springService
    }
}

class SpringService {}
