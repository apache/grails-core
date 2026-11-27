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

package gorm

import grails.testing.mixin.integration.Integration
import org.hibernate.Hibernate
import org.hibernate.proxy.HibernateProxy
import spock.lang.Specification

/**
 * A nested map bound into an association that is still a lazy proxy must be bound by the rules of the class the
 * proxy stands for, as it is when the association is loaded.
 */
@Integration
class LazyAssociationBindingSpec extends Specification {

    void "request data bound into a lazily loaded association is bound by the association's rules"() {
        given: 'a magazine and its publisher'
        Map<String, Long> ids = Magazine.withNewTransaction {
            Publisher acme = new Publisher(name: 'Acme').save(failOnError: true)
            Magazine weekly = new Magazine(title: 'Weekly', publisher: acme).save(failOnError: true, flush: true)
            [magazine: weekly.id, publisher: acme.id]
        }

        when: 'the magazine is loaded afresh, so that its publisher is a proxy, and bound with nested publisher data'
        boolean publisherWasProxy = false
        Magazine.withNewTransaction {
            Magazine magazine = Magazine.get(ids.magazine)
            publisherWasProxy = magazine.publisher instanceof HibernateProxy && !Hibernate.isInitialized(magazine.publisher)
            magazine.properties = [publisher: [name: 'Acme Periodicals', verified: true]]
            magazine.save(flush: true)
        }
        Publisher publisher = Publisher.withNewTransaction { Publisher.get(ids.publisher) }

        then: 'the publisher was a proxy when it was bound'
        publisherWasProxy

        and: 'its bindable property was bound, and its non-bindable one was left alone'
        publisher.name == 'Acme Periodicals'
        !publisher.verified

        cleanup:
        Magazine.withNewTransaction {
            Magazine.get(ids.magazine)?.delete(flush: true)
            Publisher.get(ids.publisher)?.delete(flush: true)
        }
    }
}
