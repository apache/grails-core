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

import static grails.gorm.hibernate.mapping.MappingBuilder.define

import grails.gorm.annotation.Entity
import org.jetbrains.annotations.NotNull
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static grails.gorm.hibernate.mapping.MappingBuilder.define

/**
 * Created by graemerocher on 26/01/2017.
 */
//TODO: Failing at MappingModelCreationHelper line 1223
//MappingModelCreationHelper assert ( (SortableValue) collectionBootValueMapping.getKey() ).isSorted()
class CompositeIdWithJoinTableSpec extends HibernateGormDatastoreSpec {
    def setupSpec() {
        manager.addAllDomainClasses([CompositeIdParent, CompositeIdChild])
    }

    //    @Rollback
    void "test composite id with join table"() {
        when: "A parent with a composite id and a join table is saved"
        new CompositeIdParent(name: "Test", last: "Test 2")
                .addToChildren(new CompositeIdChild(foo: "bar"))
                .save(flush: true)


        then: "The entity was saved"
        CompositeIdParent.count() == 1
        CompositeIdParent.list().first().children.size() == 1
    }
}

@Entity
class CompositeIdParent implements Serializable, Comparable<CompositeIdParent> {
    String name
    String last
    SortedSet<CompositeIdChild> children
    static hasMany = [children: CompositeIdChild]
    static mapping = define {
        id composite('name', 'last')
        property("children") {
            joinTable {
                name "child_parent"
                column "child_id"
            }
            column {
                name "foo"
            }
            column {
                name "bar"
            }
        }
    }

    @Override
    int compareTo(@NotNull CompositeIdParent o) {
        this.name <=> o.name ?: this.last <=> o.last
    }
}

@Entity
class CompositeIdChild implements Comparable<CompositeIdChild> {
    String foo
    static belongsTo = [parent: CompositeIdParent]

    static mapping = {

    }
    static constraints = {
    }

    @Override
    int compareTo(CompositeIdChild other) {
        foo <=> other.foo
    }
}