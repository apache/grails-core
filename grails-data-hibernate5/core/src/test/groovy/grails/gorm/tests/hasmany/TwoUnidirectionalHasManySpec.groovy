/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package grails.gorm.tests.hasmany

import grails.gorm.annotation.Entity
import grails.gorm.annotation.JpaEntity
import grails.gorm.hibernate.mapping.MappingBuilder
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.CascadeType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.OneToMany

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class TwoUnidirectionalHasManySpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(getClass().getPackage())


    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10811')
    @Ignore
    void "test two undirectional one to many references"() {
        when:
        new EcmMask(name: "test")
                .addToCreateUsers(name: "Fred")
                .addToUpdateUsers(name:"Bob")
                .save(flush:true).discard()

        EcmMask mask = EcmMask.first()

        then:
        mask != null
        mask.createUsers.size() == 1
        mask.updateUsers.size() == 1

    }

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10811')
    @Ignore
    void "test two JPA undirectional one to many references"() {

        when:
        def jpa = new EcmMaskJpa(name: "test")
        jpa.createdUsers.add(new User2(name: "Fred"))
        jpa.updatedUsers.add(new User2(name: "Bob"))

        jpa.save(flush:true).discard()

        EcmMaskJpa mask = EcmMaskJpa.first()

        then:
        mask != null
        mask.createUsers.size() == 1
        mask.updateUsers.size() == 1

    }

}

@JpaEntity
class EcmMaskJpa {
    @Id
    @GeneratedValue
    Long id

    String name

    @OneToMany(cascade = CascadeType.ALL)
    Set<User2> createdUsers = []

    @OneToMany(cascade = CascadeType.ALL)
    Set<User2> updatedUsers = []
}

@JpaEntity
class User2 {
    @Id
    @GeneratedValue
    Long id
    String name
}

@Entity
class EcmMask {
    String name
    static hasMany = [createUsers:User,updateUsers:User]

    static mapping = MappingBuilder.orm {
//        property('createUsers') {
//            joinTable { name"created_users" }
//        }
//        property('updateUsers') {
//            joinTable { name "updated_users" }
//        }
    }
}

@Entity
class User {
    String name

    static mapping = {
        table '`user`'
    }
}
