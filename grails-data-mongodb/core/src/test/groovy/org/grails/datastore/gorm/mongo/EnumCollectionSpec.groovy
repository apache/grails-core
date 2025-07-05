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

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class EnumCollectionSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.addAllDomainClasses([Teacher, Teacher2, Teacher3, DerivedTeacher])
    }

    void "Test persistence of enum"() {
        given:
        def i = new Teacher(name: "Melvin", subject: Subject.MATH)

        when:
        i.save(flush: true)

        then: "Saving it doesn't break it"
        i.subject == Subject.MATH

        when:
        manager.session.clear()
        i = Teacher.findByName("Melvin")

        then:
        i != null
        i.name == 'Melvin'
        i.subject == Subject.MATH
    }

    void "Test persistence of enum collections"() {
        given:
        def i = new Teacher2(name: "Melvin", subject: Subject.MATH)
        i.otherSubjects = [Subject.HISTORY, Subject.HOME_EC]

        when: "The entity is saved and flushed"
        i.save(flush: true)

        then: "The collection hasn't been broken by saving it"
        i.otherSubjects == [Subject.HISTORY, Subject.HOME_EC]

        when: "The entity is queried for afresh"
        manager.session.clear()
        i = Teacher2.findByName("Melvin")

        then:
        i != null
        i.name == 'Melvin'
        i.subject == Subject.MATH
        i.otherSubjects != null
        i.otherSubjects.size() == 2
        i.otherSubjects[0] == Subject.HISTORY
        i.otherSubjects[1] == Subject.HOME_EC
    }

    void "Test persistence of parent enum collections"() {
        given:
        def i = new DerivedTeacher(name: "Melvin", subject: Subject.MATH, extra: 'hello')
        i.otherSubjects = [Subject.HISTORY, Subject.HOME_EC]

        when: "The entity is saved and flushed"
        i.save(flush: true)

        then: "The collection hasn't been broken by saving it"
        i.otherSubjects == [Subject.HISTORY, Subject.HOME_EC]

        when: "The entity is queried for afresh"
        manager.session.clear()
        i = DerivedTeacher.findByName("Melvin")

        then:
        i != null
        i.name == 'Melvin'
        i.subject == Subject.MATH
        i.otherSubjects != null
        i.otherSubjects.size() == 2
        i.otherSubjects[0] == Subject.HISTORY
        i.otherSubjects[1] == Subject.HOME_EC
        i.extra == 'hello'
    }


    void "Test persistence of enum  set collections"() {
        given:
        def i = new Teacher3(name: "Melvin")
        i.subjects = [Subject.HISTORY, Subject.HOME_EC]

        when: "The entity is saved and flushed"
        i.save(flush: true)

        then: "The collection hasn't been broken by saving it"
        i.subjects.contains Subject.HISTORY
        i.subjects.contains Subject.HOME_EC
        i.subjects.size() == 2

        when: "The entity is queried for afresh"
        manager.session.clear()
        i = Teacher3.findByName("Melvin")

        then:
        i != null
        i.name == 'Melvin'
        i.subjects != null
        i.subjects.size() == 2
        i.subjects.contains Subject.HISTORY
        i.subjects.contains Subject.HOME_EC
    }
}

@Entity
class Teacher {
    Long id
    String name
    Subject subject

    static mapping = {
        name index: true
    }
}

@Entity
class Teacher2 {
    Long id
    String name
    Subject subject
    List<Subject> otherSubjects

    static mapping = {
        name index: true
    }
}

@Entity
class Teacher3 {
    Long id
    String name
    Set<Subject> subjects

    static mapping = {
        name index: true
    }
}


@Entity
class DerivedTeacher extends Teacher2 {
    String extra

    static mapping = {
        name index: true
    }
}

enum Subject {
    HISTORY, MATH, ENGLISH, HOME_EC

    @Override
    String toString() {
        "Surprise!"
    }
}
