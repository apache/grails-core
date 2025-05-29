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

package grails.gorm.specs.compositeid

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.mapping.MappingBuilder
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import jakarta.annotation.Nonnull
import spock.lang.Issue

/**
 * Created by graemerocher on 26/01/2017.
 */
//TODO: CompositeId not working
class CompositeIdWithDeepOneToManyMappingSpec extends HibernateGormDatastoreSpec {

    @Override
    def setupSpec() {
        manager.domainClasses.clear()
        manager.domainClasses.addAll([GrandParent, Parent, Child])
    }

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/660')
    void 'test composite id with nested one-to-many mappings'() {
        when:
        def grandParent = new GrandParent(luckyNumber: 7, name: "Fred")
        def parent = new Parent(name: "Bob")
        grandParent.addToParents(parent)
        parent.addToChildren(new Child(name: "Chuck"))
        grandParent.save(flush: true)

        then:
        Parent.count == 1
        GrandParent.count == 1
        Child.count == 1
        GrandParent.list().first().parents.first().children.first().parent != null
    }
}

@Entity
class Child implements Serializable, Comparable<Child> {
    String name

    static belongsTo = [parent: Parent]

    static mapping = MappingBuilder.define {
        composite('parent', 'name')
    }

    @Override
    int compareTo(@Nonnull Child o) {
        return this.name <=> o.name
    }
}

@Entity
class Parent implements Serializable, Comparable<Parent> {
    String name
    SortedSet<Child> children

    static belongsTo = [grandParent: GrandParent]
    static hasMany = [children: Child]

    static mapping = MappingBuilder.define {
        composite('grandParent', 'name') cascade('all')
    }

    @Override
    int compareTo(@Nonnull Parent o) {
        return this.name <=> o.name
    }
}

@Entity
class GrandParent implements Serializable {
    String name
    Integer luckyNumber
    SortedSet<Parent> parents

    static hasMany = [parents: Parent]

    static mapping = MappingBuilder.define {
        composite('name', 'luckyNumber') cascade("all")

    }
}