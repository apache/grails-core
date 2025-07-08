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

import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * @author graemerocher
 */
class AttachMethodSpec extends GrailsDataTckSpec {
    void setupSpec() {
        manager.addAllDomainClasses([Person])
    }

    void "Test attach method"() {
        given:
        def test = new Person(firstName: "Bob", lastName: "Builder").save()

        when:
        manager.session.flush()

        then:
        manager.session.contains(test) == true
        test.isAttached()
        test.attached

        when:
        test.discard()

        then:
        !manager.session.contains(test)
        !test.isAttached()
        !test.attached

        when:
        test.attach()

        then:
        manager.session.contains(test)
        test.isAttached()
        test.attached

        when:
        test.discard()

        then:
        test == test.attach()
    }
}
