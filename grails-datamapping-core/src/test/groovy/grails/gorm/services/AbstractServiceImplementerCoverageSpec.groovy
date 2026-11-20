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
package grails.gorm.services

import spock.lang.Specification

/**
 * Covers {@code AbstractServiceImplementer#isValidParameter}'s {@code GormProperties.IDENTITY}
 * shortcut branch - a finder-style method parameter literally named {@code id} is always considered
 * valid without needing a matching declared property (the identity property is implicit, not
 * declared like other domain properties).
 */
class AbstractServiceImplementerCoverageSpec extends Specification {

    void "test a save method parameter named 'id' is bound as a valid identity parameter"() {
        when: "a save-style method binds an explicit id alongside a real domain property"
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.*
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {
    Foo saveFoo(String title, Serializable id)
}
@Entity
class Foo {
    String title
}
''')

        then: "the interface compiles - 'id' was accepted as a valid constructor-style parameter without a matching declared property"
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass('$MyServiceImplementation')

        then:
        impl.getMethod('saveFoo', String, Serializable)
                .getAnnotation(org.grails.datastore.gorm.services.Implemented) != null
    }
}
