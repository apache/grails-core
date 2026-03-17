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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

class HibernateToManyPropertySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([HTMPBook, HTMPAuthor, HTMPAuthorCustom, HTMPStudent, HTMPCourse])
    }

    void "resolveJoinTableForeignKeyColumnName derives name from associated entity when no explicit config"() {
        given:
        def authorEntity = mappingContext.getPersistentEntity(HTMPAuthor.name)
        HibernateToManyProperty property = (HibernateToManyProperty) authorEntity.getPropertyByName("books")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        when:
        String columnName = property.resolveJoinTableForeignKeyColumnName(namingStrategy)

        then:
        columnName == "htmpbook_id"
    }

    void "resolveJoinTableForeignKeyColumnName uses explicit join table column name when configured"() {
        given:
        def authorEntity = mappingContext.getPersistentEntity(HTMPAuthorCustom.name)
        HibernateToManyProperty property = (HibernateToManyProperty) authorEntity.getPropertyByName("books")
        def namingStrategy = getGrailsDomainBinder().namingStrategy

        when:
        String columnName = property.resolveJoinTableForeignKeyColumnName(namingStrategy)

        then:
        columnName == "custom_book_fk"
    }

    void "isAssociationColumnNullable returns false for ManyToMany"() {
        when:
        def studentEntity = mappingContext.getPersistentEntity(HTMPStudent.name)
        def coursesProp = studentEntity.getPropertyByName("courses")

        then:
        !coursesProp.isAssociationColumnNullable()
    }
}

@Entity
class HTMPBook {
    Long id
    String title
}

@Entity
class HTMPAuthor {
    Long id
    String name
    Set<HTMPBook> books
    static hasMany = [books: HTMPBook]
}

@Entity
class HTMPAuthorCustom {
    Long id
    String name
    Set<HTMPBook> books
    static hasMany = [books: HTMPBook]
    static mapping = {
        books joinTable: [column: 'custom_book_fk']
    }
}

@Entity
class HTMPStudent {
    Long id
    String name
    Set<HTMPCourse> courses
    static hasMany = [courses: HTMPCourse]
}

@Entity
class HTMPCourse {
    Long id
    String title
    Set<HTMPStudent> students
    static hasMany = [students: HTMPStudent]
}
