/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Test session properties
 */
class SessionPropertiesSpec extends GrailsDataTckSpec {

    void "test session properties"() {
        when:
        manager.session.setSessionProperty('Hello', 'World')
        then:
        manager.session.getSessionProperty('Hello') == 'World'
        manager.session.getSessionProperty('World') == null

        when:
        manager.session.setSessionProperty('One', 'Two')
        then:
        manager.session.getSessionProperty('Hello') == 'World'
        manager.session.getSessionProperty('One') == 'Two'

        when:
        def old = manager.session.setSessionProperty('One', 'Three')
        then:
        manager.session.getSessionProperty('Hello') == 'World'
        manager.session.getSessionProperty('One') == 'Three'
        old == 'Two'

        when: "Clearing the session doesn't clear the properties"
        manager.session.clear()
        then:
        manager.session.getSessionProperty('Hello') == 'World'
        manager.session.getSessionProperty('One') == 'Three'

        when:
        old = manager.session.clearSessionProperty('Hello')
        then:
        manager.session.getSessionProperty('Hello') == null
        manager.session.getSessionProperty('One') == 'Three'
        old == 'World'
    }
}
