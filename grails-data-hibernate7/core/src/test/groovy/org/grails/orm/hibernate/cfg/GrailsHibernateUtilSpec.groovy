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
package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.config.GormProperties
import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import spock.lang.Unroll

class GrailsHibernateUtilSpec extends HibernateGormDatastoreSpec {

    @Unroll
    def "test isDomainClass for #clazz.simpleName"() {
        expect:
        GrailsHibernateUtil.isDomainClass(clazz) == expected

        where:
        clazz              | expected
        GHUBook            | true
        GHUNonDomain       | false
        String             | false
        GHUAnnotatedEntity | true
    }

    def "test incrementVersion"() {
        given:
        def book = new GHUBook(version: 1L)

        when:
        GrailsHibernateUtil.incrementVersion(book)

        then:
        book.version == 2L
    }

    def "test incrementVersion with non-long version"() {
        given:
        def obj = new GHUNonDomain()

        when:
        GrailsHibernateUtil.incrementVersion(obj)

        then:
        noExceptionThrown()
    }

    def "test qualify and unqualify"() {
        expect:
        GrailsHibernateUtil.qualify("org.test", "MyClass") == "org.test.MyClass"
        GrailsHibernateUtil.unqualify("org.test.MyClass") == "MyClass"
    }

    def "test isNotEmpty"() {
        expect:
        GrailsHibernateUtil.isNotEmpty("test")
        !GrailsHibernateUtil.isNotEmpty("")
        !GrailsHibernateUtil.isNotEmpty(null)
    }

    def "test unwrapIfProxy with non-proxy"() {
        given:
        def obj = new Object()

        expect:
        GrailsHibernateUtil.unwrapIfProxy(obj).is(obj)
        GrailsHibernateUtil.unwrapIfProxy(null) == null
    }

    def "test unwrapIfProxy with EntityProxy"() {
        given:
        def implementation = new Object()
        def proxy = [
            getTarget: { implementation },
            isInitialized: { true }
        ] as org.grails.datastore.mapping.proxy.EntityProxy

        expect:
        GrailsHibernateUtil.unwrapIfProxy(proxy).is(implementation)
    }
}

@Entity
class GHUBook {
    Long id
    Long version
    String title
}

class GHUNonDomain {
    String name
}

@grails.persistence.Entity
class GHUAnnotatedEntity {
    Long id
}
