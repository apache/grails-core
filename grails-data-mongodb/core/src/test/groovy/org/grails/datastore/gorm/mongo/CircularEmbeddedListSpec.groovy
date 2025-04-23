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

package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue

/**
 * Created by graemerocher on 14/03/14.
 */
class CircularEmbeddedListSpec extends GormDatastoreSpec{

    @Issue('GPMONGODB-350')
    void 'Test CRUD operations on circular nested embedded list'() {
        when:"An initial circular relationship is created"
            def t = new Tree(name:"top")
            t.items << new Tree(name:'L1a')
            t.save(flush:true)
            session.clear()
            t = Tree.get(t.id)

        then:"The associated is retrieved correctly"
            t.name == 'top'
            t.items.size() == 1
            t.items[0].name == 'L1a'

        when:"It is updated"
            t.items << new Tree(name:"L1b")
            t.items[0].items << new Tree(name:"L2")
            t.save(flush:true)
            session.clear()
            t = Tree.get(t.id)

        then:"The update is correctly persisted"

            t.items.size() == 2
            t.name == 'top'
            t.items[0].name == 'L1a'
            t.items[0].items[0].name == 'L2'
            t.items[1].name == 'L1b'
    }

    @Override
    List getDomainClasses() {
        [Tree]
    }
}

@Entity
class Tree {
    Long id
    Long version
    String name
    List<Tree> items = []
    static embedded = ['items']
}
