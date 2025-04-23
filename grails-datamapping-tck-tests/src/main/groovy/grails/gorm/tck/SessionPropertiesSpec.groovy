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

package grails.gorm.tck

import grails.gorm.tests.GormDatastoreSpec

/**
 * Test session properties
 */
class SessionPropertiesSpec extends GormDatastoreSpec {

    void "test session properties"() {
        when:
        session.setSessionProperty('Hello', 'World')
        then:
        session.getSessionProperty('Hello') == 'World'
        session.getSessionProperty('World') == null

        when:
        session.setSessionProperty('One', 'Two')
        then:
        session.getSessionProperty('Hello') == 'World'
        session.getSessionProperty('One') == 'Two'

        when:
        def old = session.setSessionProperty('One', 'Three')
        then:
        session.getSessionProperty('Hello') == 'World'
        session.getSessionProperty('One') == 'Three'
        old == 'Two'

        when:"Clearing the session doesn't clear the properties"
        session.clear()
        then:
        session.getSessionProperty('Hello') == 'World'
        session.getSessionProperty('One') == 'Three'

        when:
        old = session.clearSessionProperty('Hello')
        then:
        session.getSessionProperty('Hello') == null
        session.getSessionProperty('One') == 'Three'
        old == 'World'
    }
}
