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

import org.grails.datastore.gorm.proxy.GroovyProxyFactory
import org.springframework.dao.DataIntegrityViolationException

/**
 * @author graemerocher
 */
class GroovyProxySpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Location]
    }

    void "Test creation and behavior of Groovy proxies"() {
        setup:
        if(useGroovyProxyFactory) {
            session.mappingContext.proxyFactory = new GroovyProxyFactory()
        }

        def id = new Location(name:"United Kingdom", code:"UK").save(flush:true)?.id
        session.clear()

        when:
        def location = Location.proxy(id)

        then:

        location != null
        id == location.id
        Location.isInstance(location) == true
        null != location.metaClass
        false == location.isInitialized()
        false == location.initialized

        "UK" == location.code
        "United Kingdom - UK" == location.namedAndCode()
        true == location.isInitialized()
        true == location.initialized
        null != location.target
        Location.isInstance(location) == true
        null != location.metaClass
        where:
        useGroovyProxyFactory << [true, false]
    }

    void "Test setting metaClass property on proxy"() {
        setup:
        if(useGroovyProxyFactory) {
            session.mappingContext.proxyFactory = new GroovyProxyFactory()
        }

        when:
        def location = Location.proxy(123)
        location.metaClass = null
        then:
        location.metaClass != null
        where:
        useGroovyProxyFactory << [true, false]
    }

    void "Test calling setMetaClass method on proxy"() {
        setup:
        if(useGroovyProxyFactory) {
            session.mappingContext.proxyFactory = new GroovyProxyFactory()
        }

        when:
        def location = Location.proxy(123)
        location.setMetaClass(null)
        then:
        location.metaClass != null
        where:
        useGroovyProxyFactory << [true, false]
    }

    void "Test creation and behavior of Groovy proxies with method call"() {
        setup:
        if(useGroovyProxyFactory) {
            session.mappingContext.proxyFactory = new GroovyProxyFactory()
        }
        def id = new Location(name:"United Kingdom", code:"UK").save(flush:true)?.id
        session.clear()

        when:
        def location = Location.proxy(id)

        then:

        location != null
        id == location.id
        Location.isInstance(location) == true
        null != location.metaClass
        false == location.isInitialized()
        false == location.initialized

        "United Kingdom - UK" == location.namedAndCode() // method first
        "UK" == location.code
        true == location.isInitialized()
        true == location.initialized
        null != location.target
        Location.isInstance(location) == true
        null != location.metaClass
        where:
        useGroovyProxyFactory << [true, false]
    }
}
