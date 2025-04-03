/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package grails.test.mixin.cascade

import grails.gorm.annotation.Entity
import grails.testing.gorm.DataTest
import grails.validation.ValidationException
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class CascadeCircularSpec extends Specification implements DataTest{

    @Issue('https://github.com/apache/grails-data-mapping/issues/967')
    void "test cascade circular"() {
        given:
        Person splinter = new Person(name: 'Master Splinter')

        Person leo = new Person(name: 'Leonardo')
        Person donnie = new Person(name: 'Donatello')
        Person mikey = new Person(name: 'Michelangelo')
        Person raph = new Person(name: 'Raphael')

        splinter.addToStudents(leo)
        splinter.addToStudents(donnie)
        splinter.addToStudents(mikey)
        splinter.addToStudents(raph)

        leo.peers = [donnie, mikey, raph]
        donnie.peers = [leo, mikey, raph]
        mikey.peers = [leo, donnie, raph]
        raph.peers = [leo, donnie, mikey]

        when:
        splinter.save(failOnError: true)

        then:
        thrown(ValidationException)
    }

    @Override
    Class[] getDomainClassesToMock() {
        [Person]
    }
}
@Entity
class Person {
    String name

    static belongsTo = [master: Person]

    static hasMany = [students: Person, peers: Person]
    static mappedBy = [students: 'none', peers: 'none']

    static constraints = {
        peers cascade: false
    }


    @Override
    String toString() {
        name
    }
}
