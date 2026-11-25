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
package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.hibernate.proxy.HibernateProxy
import spock.lang.Issue

/**
 * A criteria get of the one row that matches returns the instance, as Hibernate 5 does, also when the session already
 * holds that row as a proxy: the proxy is the answer of a list, and of nothing that asks for one instance.
 */
@Issue('https://github.com/apache/grails-core/issues/16808')
class CriteriaGetUnproxiesSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(CguPerson)
    }

    void "a get of a row that the session holds as a proxy returns the instance"() {
        given:
        Long id = new CguPerson(name: 'Ann').save(flush: true).id
        manager.session.clear()
        def proxy = CguPerson.proxy(id)

        when:
        def found = CguPerson.createCriteria().get { eq('name', 'Ann') }

        then:
        proxy instanceof HibernateProxy
        found != null
        !(found instanceof HibernateProxy)
        found.id == id
        found.name == 'Ann'
    }

    void "a get of a row that the session does not hold returns the instance"() {
        given:
        new CguPerson(name: 'Ann').save(flush: true)
        manager.session.clear()

        when:
        def found = CguPerson.createCriteria().get { eq('name', 'Ann') }

        then:
        !(found instanceof HibernateProxy)
        found.name == 'Ann'
    }

    void "a get of no row returns null"() {
        expect:
        CguPerson.createCriteria().get { eq('name', 'nobody') } == null
    }
}

@Entity
class CguPerson {

    Long id
    String name
}
