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
package grails.gorm.specs

import grails.gorm.annotation.Entity
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

/**
 * Created by graemerocher on 27/06/16.
 */
class LastUpdateWithDynamicUpdateSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([LastUpdateTestA, LastUpdateTestB, LastUpdateTestC])
    }

    void "lastUpdated should work for dynamic update and no versioning on TestA"() {
        given:
        def a = new LastUpdateTestA(name: 'David Estes')
        a.save(flush:true)
        a.refresh()
        def lastUpdated = a.lastUpdated
        sleep(5)
        when:
        a.name = "David R. Estes"
        a.save(flush:true)
        a.refresh()
        then:
        a.lastUpdated > lastUpdated
    }

    void "lastUpdated should work for dynamic update with version true TestB"() {
        given:
        def a = new LastUpdateTestB(name: 'David Estes')
        a.save(flush:true)
        a.refresh()
        def lastUpdated = a.lastUpdated
        sleep(5)
        when:
        a.name = "David R. Estes"
        a.save(flush:true)
        a.refresh()
        then:
        a.lastUpdated > lastUpdated
    }

    void "lastUpdated should work for dynamic update false and versioning on TestC"() {
        given:
        def a = new LastUpdateTestC(name: 'David Estes')
        a.save(flush:true)
        a.refresh()
        def lastUpdated = a.lastUpdated
        sleep(5)
        when:
        a.name = "David R. Estes"
        a.save(flush:true)
        a.refresh()
        then:
        a.lastUpdated > lastUpdated
    }


    void "autoTimestamp should work with updateAll for dynamic update false and versioning on TestC"() {
        given:
        def a = new LastUpdateTestC(name: 'David Estes')
        a.save(flush:true)
        a.refresh()
        def lastUpdated = a.lastUpdated
        sleep(5)
        when:
        LastUpdateTestC.where{
            eq 'id', a.id
        }.updateAll(name: 'David R. Estes')
        a.refresh()
        then:
        a.lastUpdated > lastUpdated
    }
}


@Entity
class LastUpdateTestA {

    String name
    Date dateCreated
    Date lastUpdated

    static mapping = {
        version false
        dynamicUpdate true
    }
}

@Entity
class LastUpdateTestB {
    String name
    Date dateCreated
    Date lastUpdated

    static mapping = {
        version true
        dynamicUpdate true
    }
}

@Entity
class LastUpdateTestC {
    String name
    Date dateCreated
    Date lastUpdated

    static mapping = {
        version true
        dynamicUpdate false
    }
    static constraints = {
    }
}
