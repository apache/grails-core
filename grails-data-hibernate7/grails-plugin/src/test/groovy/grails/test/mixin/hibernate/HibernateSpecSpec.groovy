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
package grails.test.mixin.hibernate

import grails.gorm.annotation.Entity
import grails.test.hibernate.HibernateSpec

/**
 * Created by graemerocher on 15/07/2016.
 */
class HibernateSpecSpec extends HibernateSpec {

    void setup() {
        if (!Book.countByTitle("The Stand")) {
            new Book(title: "The Stand").save(flush:true)
        }
    }

    void "test hibernate spec"() {
        expect:
        hibernateDatastore.connectionSources.defaultConnectionSource.settings.dataSource.dbCreate == 'create-drop'
        hibernateDatastore.connectionSources.defaultConnectionSource.settings.dataSource.logSql == true
        Book.count() == 1
        !new Book().validate()
        !new Book(title: "").validate()
        hibernateSession != null
        sessionFactory != null
    }

    void "test hibernate spec with domain constraint inheritance"() {
        given:

        def player = new Player(sport: "Football", name: "Cantona", age: 50)
        player.validate()

        expect:
        !new Player().validate()
        !new Player(sport:"Football").validate()
        !new Player(sport:"Football", name: "Cantona").validate()
        !new Player(sport:"Football", name: "Cantona", age:70).validate()
        new Player(sport:"Football", name: "Cantona", age:50).validate()
    }

    void "Configuration defaults are correct"() {
        expect: "Default from application.yml"
        hibernateDatastore.failOnError == false
        and: "Default"
        hibernateDatastore.defaultFlushModeName == "COMMIT"
    }

    List<Class> getDomainClasses() { [Person, Player, Book] }
}

@Entity
class Person {
    String name
    Integer age
    String phone
    static constraints = {
        age min: 18, max: 65
        name blank: false
        phone nullable: true
    }
}
@Entity
class Player extends Person {
    String sport
    String height
    static constraints = {
        sport blank: false
        height nullable: true
    }
}

@Entity
class Book {
    String title

    static constraints = {
        title validator: { val ->
            val.asBoolean()
        }
    }
}
