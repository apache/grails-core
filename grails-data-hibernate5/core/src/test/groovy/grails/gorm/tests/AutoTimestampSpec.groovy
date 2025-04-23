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

class AutoTimestampSpec extends GormDatastoreSpec {
    @Override
    List getDomainClasses() {
        [DateCreatedTestA, DateCreatedTestB]
    }

    void "autoTimestamp should prevent custom changes to dateCreated and lastUpdated if turned on"() {
        when: "testing insert ignores custom dateCreated and lastUpdated"
        def before = new Date() - 5
        def a = new DateCreatedTestA(name: 'David Estes', lastUpdated: before, dateCreated: before)
        a.save(flush:true)
        a.refresh()
        def lastUpdated = a.lastUpdated
        def dateCreated = a.dateCreated

        then:
        lastUpdated > before
        dateCreated > before

        when: "testing update ignores custom dateCreated and lastUpdated"
        a.name = "David R. Estes"
        a.lastUpdated = before - 5
        a.dateCreated = before - 5
        a.save(flush:true)
        a.refresh()

        then:
        a.lastUpdated > lastUpdated
        a.dateCreated == dateCreated
    }

    void "dateCreated and lastUpdated should not be modified by GORM if turned off"() {
        when: "insert allows custom dateCreated and lastUpdated"
        def now = new Date()
        def before = now - 5

        def a = new DateCreatedTestB(name: 'David Estes', lastUpdated: before, dateCreated: before)
        a.save(flush:true)
        a.refresh()

        def lastUpdated = a.lastUpdated
        def dateCreated = a.dateCreated

        then:
        lastUpdated == before
        dateCreated == before

        when: "update allows custom dateCreated and lastUpdated"
        a.name = "David R. Estes"
        a.lastUpdated = now
        a.dateCreated = now
        a.save(flush:true)
        a.refresh()

        then:
        a.lastUpdated == now
        a.dateCreated == now
    }
}

@Entity
class DateCreatedTestA {
    String name
    Date dateCreated
    Date lastUpdated

    static mapping = {
        autoTimestamp true
    }
}

@Entity
class DateCreatedTestB {
    String name
    Date dateCreated
    Date lastUpdated

    static mapping = {
        autoTimestamp false
    }
}
