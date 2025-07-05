/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.grails.data.testing.tck.tests

import org.apache.grails.data.testing.tck.domains.OptLockNotVersioned
import org.apache.grails.data.testing.tck.domains.OptLockVersioned
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.mapping.core.OptimisticLockingException
import spock.lang.IgnoreIf

/**
 * @author Burt Beckwith
 */
class OptimisticLockingSpec extends GrailsDataTckSpec {

    void setupSpec() {
        manager.addAllDomainClasses([OptLockVersioned, OptLockNotVersioned])
    }

    void "Test versioning"() {

        given:
        def o = new OptLockVersioned(name: 'locked')

        when:
        o.save flush: true

        then:
        o.version == 0

        when:
        manager.session.clear()
        o = OptLockVersioned.get(o.id)
        o.name = 'Fred'
        o.save flush: true

        then:
        o.version == 1

        when:
        manager.session.clear()
        o = OptLockVersioned.get(o.id)

        then:
        o.name == 'Fred'
        o.version == 1
    }

    // hibernate has a customized version of this
    @IgnoreIf({ System.getProperty('hibernate5.gorm.suite') })
    void "Test optimistic locking"() {

        given:
        def o = new OptLockVersioned(name: 'locked').save(flush: true)
        manager.session.clear()

        when:
        o = OptLockVersioned.get(o.id)

        Thread.start {
            OptLockVersioned.withNewSession { s ->
                def reloaded = OptLockVersioned.get(o.id)
                assert reloaded
                reloaded.name += ' in new session'
                reloaded.save(flush: true)
            }
        }.join()
        sleep 2000 // heisenbug

        o.name += ' in main session'
        def ex
        try {
            o.save(flush: true)
        }
        catch (e) {
            ex = e
            e.printStackTrace()
        }

        manager.session.clear()
        o = OptLockVersioned.get(o.id)

        then:
        ex instanceof OptimisticLockingException
        o.version == 1
        o.name == 'locked in new session'
    }

    void "Test optimistic locking disabled with 'version false'"() {

        given:
        def o = new OptLockNotVersioned(name: 'locked').save(flush: true)
        manager.session.clear()

        when:
        o = OptLockNotVersioned.get(o.id)

        Thread.start {
            OptLockNotVersioned.withNewSession { s ->
                def reloaded = OptLockNotVersioned.get(o.id)
                reloaded.name += ' in new session'
                reloaded.save(flush: true)
            }
        }.join()
        sleep 2000 // heisenbug

        o.name += ' in main session'
        def ex
        try {
            o.save(flush: true)
        }
        catch (e) {
            ex = e
            e.printStackTrace()
        }

        manager.session.clear()
        o = OptLockNotVersioned.get(o.id)

        then:
        ex == null
        o.name == 'locked in main session'
    }
}
