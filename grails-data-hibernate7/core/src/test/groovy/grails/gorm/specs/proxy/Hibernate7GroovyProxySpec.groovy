/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package grails.gorm.specs.proxy

import org.apache.grails.data.hibernate7.core.GrailsDataHibernate7TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.apache.grails.data.testing.tck.domains.Location
import org.grails.datastore.gorm.proxy.GroovyProxyFactory

/**
 * @author graemerocher
 */
class Hibernate7GroovyProxySpec extends GrailsDataTckSpec<GrailsDataHibernate7TckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Location])
    }
    void 'Test creation and behavior of Groovy proxies'() {
        given:
        manager.session.mappingContext.proxyFactory = new GroovyProxyFactory()
        def id = new Location(name: 'United Kingdom', code: 'UK').save(flush: true)?.id
        manager.session.clear()
        manager.hibernateSession.clear()

        when:
        def location = Location.proxy(id)

        then:
        location != null
        id == location.id
        // Use the method on the proxy
        false == location.isInitialized()
        false == manager.hibernateDatastore.mappingContext.proxyHandler.isInitialized(location)

        'UK' == location.code
        'United Kingdom - UK' == location.namedAndCode()
        true == location.isInitialized()
        true == manager.hibernateDatastore.mappingContext.proxyHandler.isInitialized(location)
        null != location.target
    }
}
