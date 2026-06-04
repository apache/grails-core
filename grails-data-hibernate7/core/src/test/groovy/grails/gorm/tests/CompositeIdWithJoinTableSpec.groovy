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

import static grails.gorm.hibernate.mapping.MappingBuilder.define

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 26/01/2017.
 */
class CompositeIdWithJoinTableSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore datastore = new HibernateDatastore(CompositeIdParent, CompositeIdChild)
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    @Rollback
    void "test composite id with join table"() {
        when:"A parent with a composite id and a join table is saved"
        new CompositeIdParent(name: "Test" , last:"Test 2")
                .addToChildren(new CompositeIdChild())
                .save(flush:true)


        then:"The entity was saved"
        CompositeIdParent.count() == 1
        CompositeIdParent.list().first().children.size() == 1
    }
}

@Entity
class CompositeIdParent implements Serializable {
    String name
    String last
    static hasMany = [children:CompositeIdChild]
    static mapping = define {
        id composite('name','last')
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
}

@Entity
class CompositeIdChild {

    static mapping = {

    }
    static constraints = {
    }
}