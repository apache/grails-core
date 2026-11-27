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
package grails.validation

import spock.lang.Issue
import spock.lang.Specification

@Issue('https://github.com/apache/grails-core/issues/15364')
class SharedDomainSuperclassSpec extends Specification {

    void 'a command sharing properties through #sharing with an entity validates only its own properties'() {
        given:
        def loader = new GroovyClassLoader()
        loader.parseClass("""
            import grails.persistence.Entity
            import grails.validation.Validateable

            $baseDeclaration {
                String foo
            }

            @Entity
            class Foo $relationship BaseFoo {
                static constraints = {
                    foo nullable: false
                }
            }

            class FooCommand $commandDeclaration {
                static constraints = {
                    foo nullable: false
                }
            }
        """)
        def command = loader.loadClass('FooCommand').getDeclaredConstructor().newInstance()

        when:
        command.foo = 'valid'

        then:
        command.validate()
        !command.hasErrors()
        command.hasProperty('id') == null
        command.hasProperty('version') == null
        command.constraintsMap.keySet() == ['foo'] as Set

        when:
        command.foo = null

        then:
        !command.validate()
        command.errors.fieldErrors*.field == ['foo']
        command.errors.getFieldError('foo').code == 'nullable'

        and:
        loader.loadClass('Foo').getDeclaredField('id').type == Long
        loader.loadClass('Foo').getDeclaredField('version').type == Long

        cleanup:
        loader.close()

        where:
        sharing              | baseDeclaration          | relationship | commandDeclaration
        'a plain superclass' | 'abstract class BaseFoo'  | 'extends'    | 'extends BaseFoo implements Validateable'
        'a trait'            | 'trait BaseFoo'           | 'implements' | 'implements BaseFoo, Validateable'
    }
}