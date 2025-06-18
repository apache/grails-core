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
package grails.gorm.specs.validation

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import groovy.transform.EqualsAndHashCode
import org.springframework.dao.DuplicateKeyException
import spock.lang.Issue

/**
 * Created by graemerocher on 29/05/2017.
 */
@Issue('https://github.com/grails/gorm-hibernate5/issues/36')
class UniqueWithinGroupSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.domainClasses.addAll([Thing])
    }

    @Rollback
    void "test insert"() {
        when:
        Thing thing1 = new Thing(hello: 1, world: 2)
        thing1.insert(flush: true)

        Thing thing2 = new Thing(hello: 1, world: 2)
        thing2.insert(flush: true)

        then:
        notThrown(DuplicateKeyException)
        !thing1.hasErrors()
        thing2.hasErrors()

    }

    @Rollback
    void "test save"() {
        when:
        Thing thing1 = new Thing(hello: 1, world: 2)
        thing1.save(insert: true, flush: true)
        manager.sessionFactory.currentSession.flush()
        Thing thing2 = new Thing(hello: 1, world: 2)
        thing2.save(insert: true, flush: true)

        then:
        notThrown(DuplicateKeyException)
        !thing1.hasErrors()
        thing2.hasErrors()

    }

    @Rollback
    void "test validate"() {
        when:
        Thing thing1 = new Thing(hello: 1, world: 2).save(insert: true, flush: true)
        manager.sessionFactory.currentSession.flush()
        Thing thing2 = new Thing(hello: 1, world: 2)

        then:
        !thing1.hasErrors()
        !thing2.validate()
        thing2.errors.getFieldError('hello').code == 'unique'
    }
}

@Entity
@EqualsAndHashCode(includes = ['hello', 'world'])
class Thing implements Serializable {
    Long hello
    Long world

    static constraints = {
        hello unique: 'world'
    }
    static mapping = {
        version false
        id composite: ['hello', 'world']
    }
}
