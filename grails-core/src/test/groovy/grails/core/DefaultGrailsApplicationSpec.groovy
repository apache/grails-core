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
package grails.core

import spock.lang.Specification

import org.grails.core.exceptions.GrailsConfigurationException
import org.grails.datastore.mapping.model.MappingContext

class DefaultGrailsApplicationSpec extends Specification {

    void 'setApplicationClass adopts the class once and rejects reassignment to a different class'() {
        given: 'an application constructed without an application class (as the early phase does)'
            def application = new DefaultGrailsApplication()
            GrailsApplicationClass first = Mock(GrailsApplicationClass)
            GrailsApplicationClass second = Mock(GrailsApplicationClass)

        when: 'the application class is adopted for the first time'
            application.applicationClass = first

        then: 'it is set'
            application.applicationClass.is(first)

        when: 'the same class is set again'
            application.applicationClass = first

        then: 'the set-once guard treats an identical value as idempotent'
            noExceptionThrown()
            application.applicationClass.is(first)

        when: 'a different application class is set'
            application.applicationClass = second

        then: 'reassignment is rejected and the original is retained'
            thrown(IllegalStateException)
            application.applicationClass.is(first)
    }

    void 'the mapping context fails fast until GORM has initialized it, then delegates to it'() {
        given: 'an application whose mapping context GORM has not set yet'
            def application = new DefaultGrailsApplication()
            MappingContext real = Mock(MappingContext)

        when: 'the placeholder context is used'
            application.mappingContext.getPersistentEntities()

        then: 'it refuses rather than recursing into itself'
            GrailsConfigurationException e = thrown()
            e.message.contains('cannot be accessed before GORM has initialized')

        when: 'GORM has set the real context'
            application.mappingContext = real
            application.mappingContext.getPersistentEntities()

        then: 'the real context is what the application answers with'
            1 * real.getPersistentEntities() >> []
            application.mappingContext.is(real)
    }
}
