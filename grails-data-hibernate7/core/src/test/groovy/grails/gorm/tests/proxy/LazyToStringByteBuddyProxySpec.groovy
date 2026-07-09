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
package grails.gorm.tests.proxy

import grails.gorm.tests.entities.Club
import grails.gorm.tests.entities.Team
import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import spock.lang.Shared

/**
 * Verifies proxy toString behavior when the {@code hibernate.grails.proxy.lazy_to_string}
 * setting is enabled: toString on an uninitialized proxy answers {@code entityName:id}
 * without initializing it, while an initialized proxy delegates to the entity implementation.
 */
class LazyToStringByteBuddyProxySpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    void setupSpec() {
        // nested ConfigObject path: only nested config reaches the arbitrary hibernate settings
        // map, producing the flattened hibernate.grails.proxy.lazy_to_string property
        manager.grailsConfig.hibernate.grails.proxy.lazy_to_string = 'true'
        manager.registerDomainClasses(Team, Club)
    }

    @Shared
    HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    void "toString does not initialize the proxy when lazy toString is enabled"() {
        when: "a proxy is loaded"
        Club c = new Club(name: "DOOM Club").save(failOnError: true, flush: true)
        def id = c.id
        manager.session.clear()
        Club club = Club.load(id)

        then: "toString answers entityName:id without initializing the proxy"
        proxyHandler.isProxy(club)
        !proxyHandler.isInitialized(club)
        club.toString() == "${Club.name}:${id}"
        !proxyHandler.isInitialized(club)

        when: "the proxy is initialized by accessing a regular property"
        club.name

        then: "toString delegates to the entity implementation"
        proxyHandler.isInitialized(club)
        club.toString() == "DOOM Club"
    }
}
