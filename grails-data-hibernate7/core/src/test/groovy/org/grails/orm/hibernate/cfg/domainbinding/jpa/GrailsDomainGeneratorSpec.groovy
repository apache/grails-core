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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import java.lang.reflect.Field

import grails.gorm.annotation.Entity
import grails.gorm.tests.HibernateGormDatastoreSpec
import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.dialect.H2Dialect

import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.NumericColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.StringColumnConstraintsBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

/**
 * Describes what {@link GrailsDomainGenerator} emits for a GORM entity. Expectations are written down only where the
 * domain states them explicitly; defaults are covered by the differential spec, which compares the generator with
 * what the domain binder actually bound.
 */
class GrailsDomainGeneratorSpec extends HibernateGormDatastoreSpec {

    Class<?> generated

    void setupSpec() {
        manager.registerDomainClasses(GenBasic)
    }

    void setup() {
        generated = generate(GenBasic)
    }

    void "the generated class carries the entity and table annotations"() {
        expect:
        generated.name == 'org.grails.orm.hibernate.generated.org_grails_orm_hibernate_cfg_domainbinding_jpa_GenBasic'
        generated.getAnnotation(jakarta.persistence.Entity).name() == 'GenBasic'
        generated.getAnnotation(Table).name() == 'gen_basic'
    }

    void "the identifier is the only field marked as an id"() {
        expect:
        field('id').isAnnotationPresent(Id)
        generated.declaredFields.findAll { it.isAnnotationPresent(Id) }*.name == ['id']
    }

    void "the generated fields are exactly the persistent properties"() {
        expect:
        generated.declaredFields*.name.toSet() == ['id', 'name', 'code', 'age', 'price', 'notes'].toSet()
    }

    void "a field keeps the Java type of its property"() {
        expect:
        field(property).type == type

        where:
        property | type
        'name'   | String
        'age'    | Integer
        'price'  | BigDecimal
    }

    void "the column annotation reflects the constraints and mapping stated on the domain class"() {
        when:
        Column column = field(property).getAnnotation(Column)

        then:
        column.name() == name
        column.nullable() == nullable
        column.unique() == unique
        column.length() == length
        column.scale() == scale

        where:
        property | name     | nullable | unique | length | scale
        'name'   | 'name'   | false    | true   | 50     | 0
        'code'   | 'code_x' | true     | false  | 255    | 0
        'age'    | 'age'    | true     | false  | 255    | 0
        'price'  | 'price'  | true     | false  | 255    | 2
    }

    private Class<?> generate(Class<?> domainClass) {
        def domainBinder = getGrailsDomainBinder()
        def naming = domainBinder.getNamingStrategy()
        def generator = new GrailsDomainGenerator(
                naming,
                new ColumnNameForPropertyAndPathFetcher(naming, new DefaultColumnNameFetcher(naming), new BackticksRemover()),
                new ColumnConfigToColumnBinder(),
                new StringColumnConstraintsBinder(),
                new NumericColumnConstraintsBinder(new H2Dialect()))
        GrailsHibernatePersistentEntity entity = getPersistentEntity(domainClass)
        return generator.generate(entity, getClass().classLoader)
    }

    private Field field(String name) {
        return generated.getDeclaredField(name)
    }
}

@Entity
class GenBasic {

    String name
    String code
    Integer age
    BigDecimal price
    String notes

    static constraints = {
        name maxSize: 50, unique: true, nullable: false
        age nullable: true
        price scale: 2, nullable: true
        notes nullable: true
    }

    static mapping = {
        code column: 'code_x'
    }
}
