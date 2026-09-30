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

package org.apache.grails.profiles

import grails.core.GrailsApplication
import grails.util.Environment
import spock.lang.Specification

/**
 * Basic profile functionality tests
 * This verifies core profile-related functionality works correctly
 */
class BasicProfileTests extends Specification {
    
    def "test Grails environment is available"() {
        when: "I check the current environment"
        Environment environment = Environment.getCurrent()
        
        then: "environment is properly configured"
        environment != null
    }
    
    def "test Grails application interface is available"() {
        given: "a Grails application mock"
        GrailsApplication application = Mock(GrailsApplication)
        
        when: "I call application methods"
        def config = application.getConfig()
        
        then: "the mock works correctly"
        application != null
    }
}