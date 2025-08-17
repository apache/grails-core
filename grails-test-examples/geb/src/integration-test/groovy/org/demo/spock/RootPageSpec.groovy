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

package org.demo.spock

import geb.report.CompositeReporter
import geb.report.PageSourceReporter
import geb.report.Reporter
import grails.plugin.geb.ContainerGebConfiguration
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 * See https://docs.grails.org/latest/guide/testing.html#functionalTesting and https://www.gebish.org/manual/current/
 * for more instructions on how to write functional tests with Grails and Geb.
 */
@Integration
@ContainerGebConfiguration(reporting = true)
class RootPageSpec extends ContainerGebSpec {

    @Override
    Reporter createReporter() {
        // Override the default reporter to demonstrate how this can be customized
        new CompositeReporter(new PageSourceReporter())
    }

    void 'should display the correct title on the home page'() {
        when: 'visiting the home page'
        go('/')

        then: 'the page title is correct'
        report('root page report')
        title == 'Welcome to Grails'
    }
}
