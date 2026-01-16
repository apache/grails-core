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
package grails.gorm.specs.hasmany

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import spock.lang.Issue

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TwoUnidirectionalHasManySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([EcmMask, EcmUser])
    }

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10811')
    void "test two undirectional one to many references"() {
        when:
        new EcmMask(name: "test")
                .addToCreateUsers(new EcmUser(name: "Fred"))
                .addToUpdateUsers(new EcmUser(name: "Bob"))
                .save(flush:true, failOnError: true)

        session.clear()
        EcmMask mask = EcmMask.first()

        then:
        mask != null
        mask.createUsers.size() == 1
        mask.updateUsers.size() == 1
    }

}

@Entity

class EcmMask {

    String name

    static hasMany = [createUsers:EcmUser, updateUsers:EcmUser]

    static mappedBy = [createUsers: 'maskForCreated', updateUsers: 'maskForUpdated']

}



@Entity



class EcmUser {



    String name



    EcmMask maskForCreated



    EcmMask maskForUpdated







    static constraints = {



        maskForCreated nullable: true



        maskForUpdated nullable: true



    }

    static mapping = {
        maskForCreated column: 'mask_created_id'
        maskForUpdated column: 'mask_updated_id'
    }

}
