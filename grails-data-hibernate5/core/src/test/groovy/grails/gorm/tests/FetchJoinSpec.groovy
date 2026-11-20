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
import org.hibernate.Hibernate

class FetchJoinSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(FetchJoinParent, FetchJoinChild)
    }

    void "findById with fetch join eagerly initializes the hasMany collection"() {
        given:
        def parent = new FetchJoinParent(name: "p")
        parent.addToChildren(new FetchJoinChild(name: "c1"))
        parent.addToChildren(new FetchJoinChild(name: "c2"))
        parent.save(flush: true)
        manager.session.clear()

        when: "loading by id with an explicit join fetch of the collection"
        def found = FetchJoinParent.findById(parent.id, [fetch: [children: 'join']])

        then: "the collection is eagerly initialized"
        found != null
        Hibernate.isInitialized(found.children)
        found.children.size() == 2
    }
}

@Entity
class FetchJoinParent {
    String name
    static hasMany = [children: FetchJoinChild]
}

@Entity
class FetchJoinChild {
    String name
    static belongsTo = [parent: FetchJoinParent]
}
