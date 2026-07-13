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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Specification

/**
 * Covers the new type-compatibility check added to {@code FindAllByImplementer#doImplement}'s
 * property-validation loop: previously only the property's *existence* was checked
 * ({@code hasProperty}); now each matched query parameter's type is also checked against the
 * corresponding property's declared type via {@code isValidParameter}, producing a clear compile
 * error rather than silently generating a query that would fail (or misbehave) at runtime.
 */
class FindAllByImplementerCoverageSpec extends Specification {

    void "test dynamic finder with a parameter type incompatible with the matched property fails to compile with a clear message"() {
        when: "age is declared as int but the finder parameter is a String"
        new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {
    List<Foo> findAllByAge(String age)
}
@Entity
class Foo {
    String title
    int age
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('Cannot implement dynamic finder [findAllByAge]')
        e.message.contains('The property [age] has type [int]')
        e.message.contains('which is not compatible with the argument type [java.lang.String]')
    }

    void "test dynamic finder with a parameter type compatible with the matched property compiles successfully"() {
        when:
        Class service = new GroovyClassLoader().parseClass('''
import grails.gorm.services.Service
import grails.gorm.annotation.Entity

@Service(Foo)
interface MyService {
    List<Foo> findAllByAge(int age)
}
@Entity
class Foo {
    String title
    int age
}
''')

        then:
        service.isInterface()

        when:
        Class impl = service.classLoader.loadClass('$MyServiceImplementation')

        then:
        impl.getMethod('findAllByAge', int)
                .getAnnotation(org.grails.datastore.gorm.services.Implemented) != null
    }
}
