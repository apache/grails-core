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
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 26/01/2017.
 */
class TwoBidirectionalOneToManySpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore datastore = new HibernateDatastore(Room, PointX, PointY)
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    @Rollback
    void "test an entity with 2 bidirectional one-to-many mappings"() {
        when:"A new entity is created is created"
        Room r = new Room(name:"Test")
                        .addToPointx(new PointX())
                        .addToPointy(new PointY())

        r.save(flush:true)

        then:"The entity was saved"
        !r.errors.hasErrors()
        Room.count == 1
    }
}

@Entity
class Room {
    static hasMany = [pointx:PointX,pointy:PointY]

    String name
}

@Entity
class PointX {
    static belongsTo = [room:Room]
    Room destiny
    static constraints = {
        destiny nullable:true
    }
}

@Entity
class PointY {
    static  belongsTo = [room:Room]
    Room destiny
    static constraints = {
        destiny nullable:true
    }
}
