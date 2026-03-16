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

package grails.gorm.specs.proxy

import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.persistence.Entity
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.proxy.HibernateProxy
import spock.lang.Shared
import spock.lang.Specification

/**
 * Test cases for ByteBuddy proxy handling
 */
class ByteBuddyProxySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
       manager.addAllDomainClasses([ByteBuddyClub, ByteBuddyTeam, ByteBuddyPlayer])
    }

    void "Test that accessing id on a proxy does not initialize it"() {
        given:
        def club = new ByteBuddyClub(name: "The Club").save(flush: true)
        def team = new ByteBuddyTeam(name: "A-Team", club: club).save(flush: true)
        session.clear()

        when:
        def hibernateSession = manager.hibernateDatastore.sessionFactory.currentSession
        team = hibernateSession.getReference(ByteBuddyTeam.class, team.id)
        def proxyHandler = manager.hibernateDatastore.mappingContext.proxyHandler

        then:
        "Dynamic Groovy access does not initialize the proxy"
        assert_id_without_init(proxyHandler, team)


    }

    void "Test that accessing a lazy association returns an uninitialized proxy"() {
        given:
        def club = new ByteBuddyClub(name: "The Club").save(flush: true)
        def team = new ByteBuddyTeam(name: "A-Team", club: club).save(flush: true)
        session.clear()

        when:
        team = ByteBuddyTeam.load(team.id)
        def proxyHandler = manager.hibernateDatastore.mappingContext.proxyHandler
        proxyHandler.initialize(team)
        def clubProxy = team.club

        then:
        "The association is a proxy"
        assert_is_proxy(clubProxy)

        and: "The association proxy is not initialized"
        !proxyHandler.isInitialized(clubProxy)


    }

    private void assert_id_without_init(ProxyHandler handler, Object proxy) {
        assert manager.sessionFactory.persistenceUnitUtil.getIdentifier(proxy) != null
        assert !handler.isInitialized(proxy)
    }

    private void assert_is_proxy(Object proxy) {
        assert (proxy instanceof HibernateProxy)
    }

}

@Entity
class ByteBuddyClub {
    Long id
    String name
    static mapping = {
        id generator: 'native'
        version false
    }
}

@Entity
class ByteBuddyTeam {
    Long id
    String name
    ByteBuddyClub club

    static hasMany = [players: ByteBuddyPlayer]
    static mapping = {
        id generator: 'native'
        version false
        club lazy: true
    }
}

@Entity
class ByteBuddyPlayer {
    Long id
    String name
    static mapping = {
        id generator: 'native'
        version false
    }
}
