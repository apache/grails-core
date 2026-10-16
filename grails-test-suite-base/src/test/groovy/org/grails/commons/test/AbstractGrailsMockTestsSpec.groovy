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
package org.grails.commons.test

import spock.lang.Specification

class AbstractGrailsMockTestsSpec extends Specification {

    // The harness must not be a compiled top-level (or nested) class on the test classpath: any
    // subclass of the JUnit3-style AbstractGrailsMockTests (via GroovyTestCase/TestCase) is picked
    // up by this module's JUnit4 vintage runner as its own (empty) test case. Parsing it at runtime
    // keeps it out of the compiled test tree entirely.
    private static Class<?> harnessClass() {
        new GroovyClassLoader(Thread.currentThread().contextClassLoader).parseClass('''
package org.grails.commons.test

import org.grails.commons.test.AbstractGrailsMockTests

class AgmtHarness extends AbstractGrailsMockTests {

    List<String> events = []

    @Override
    protected void onSetUp() {
        events << 'onSetUp'
    }

    @Override
    protected void postSetUp() {
        events << 'postSetUp'
    }

    @Override
    protected void onTearDown() {
        events << 'onTearDown'
    }

    org.springframework.mock.web.MockServletContext publicCreateMockServletContext() {
        createMockServletContext()
    }

    org.grails.support.MockApplicationContext publicCreateMockApplicationContext() {
        createMockApplicationContext()
    }

    org.springframework.context.MessageSource publicCreateMessageSource() {
        createMessageSource()
    }

}
''')
    }

    void 'setUp parses classes, builds the application and calls the setup hooks in order'() {
        given:
        def harness = harnessClass().getDeclaredConstructor().newInstance()

        when:
        harness.setUp()

        then:
        harness.events == ['onSetUp', 'postSetUp']
        harness.ga instanceof grails.core.DefaultGrailsApplication
        harness.ctx instanceof org.grails.support.MockApplicationContext
        harness.ctx.getBean(grails.core.GrailsApplication.APPLICATION_ID).is(harness.ga)
        harness.ctx.getBean(grails.core.GrailsApplication.CLASS_LOADER_BEAN).is(harness.gcl)

        when:
        harness.tearDown()

        then:
        harness.events == ['onSetUp', 'postSetUp', 'onTearDown']
    }

    void 'the protected helper factories return fresh mock instances'() {
        given:
        def harness = harnessClass().getDeclaredConstructor().newInstance()

        expect:
        harness.publicCreateMockServletContext() instanceof org.springframework.mock.web.MockServletContext
        harness.publicCreateMockApplicationContext() instanceof org.grails.support.MockApplicationContext
        harness.publicCreateMessageSource() instanceof org.springframework.context.support.StaticMessageSource
    }

}
