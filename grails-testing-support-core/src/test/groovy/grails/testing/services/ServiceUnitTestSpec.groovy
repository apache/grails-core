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
package grails.testing.services

import spock.lang.Specification

import grails.compiler.GrailsCompileStatic

class ServiceUnitTestSpec extends Specification implements ServiceUnitTest<TypedLookupService> {

    void "service returns the registered bean of the concrete type under test"() {
        when:
        TypedLookupService instance = service

        then:
        instance.is(applicationContext.getBean('typedLookupService', TypedLookupService))
        service.is(instance)
    }

    void "service is autowired and retains its state across repeated lookups"() {
        given:
        defineBeans {
            lookupCollaborator(LookupCollaborator)
        }

        when:
        service.recordCall()
        service.recordCall()

        then:
        service.lookupCollaborator.is(applicationContext.getBean('lookupCollaborator'))
        service.calls == 2
        service.lookupCollaborator.calls == 2
    }
}

@GrailsCompileStatic
class TypedLookupService {

    LookupCollaborator lookupCollaborator
    int calls

    void recordCall() {
        calls++
        lookupCollaborator.calls++
    }
}

class LookupCollaborator {

    int calls
}
