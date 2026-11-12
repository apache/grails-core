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
package org.grails.compiler.gorm

import groovy.transform.Generated

import jakarta.persistence.Transient

import spock.lang.Specification

import org.springframework.validation.annotation.Validated

import org.apache.grails.common.compiler.GroovyTransformOrder
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher

/**
 * {@link org.grails.compiler.gorm.GlobalJpaEntityTransform} applies {@link JpaGormEntityTransformation}
 * to every {@code @jakarta.persistence.Entity}-annotated class automatically, exercising its
 * {@code visit(ClassNode, SourceUnit)} entry point - but only ever on a class that already carries the
 * JPA entity annotation, so the branch that adds it never runs that way. The local,
 * {@code @grails.gorm.annotation.JpaEntity}-driven entry point (the other {@code visit} overload,
 * {@code visit(ASTNode[], SourceUnit)}) is a separate, otherwise-untested code path: it is what a class
 * takes when it is annotated {@code @JpaEntity} directly without also being a JPA entity, and it is what
 * actually adds the missing {@code @jakarta.persistence.Entity} annotation before delegating to the same
 * {@code visit(ClassNode, SourceUnit)} logic.
 */
class JpaEntityTransformSpec extends Specification {

    void 'test the JPA entity transform the entity correctly'() {
        given:
        def customerClass = new GroovyClassLoader().parseClass('''
            import jakarta.persistence.*
            import jakarta.validation.constraints.Digits

            @Entity
            class Customer {

                @Id
                @GeneratedValue(strategy=GenerationType.AUTO)
                Long myId

                @Digits(integer = 10, fraction = 2)
                String firstName

                String lastName

                @jakarta.persistence.OneToMany
                Set<Customer> related

            }
        ''')
        def cpf = ClassPropertyFetcher.forClass(customerClass)
        def instance = customerClass.getDeclaredConstructor().newInstance()
        instance.myId = 1L

        expect:
        instance.id == 1L
        GormEntity.isAssignableFrom(customerClass)
        customerClass.getAnnotation(Validated)
        customerClass.getDeclaredMethod('getId').returnType == Long
        customerClass.getDeclaredMethod('getId').getAnnotation(Transient)
        customerClass.getDeclaredMethod('getId').isAnnotationPresent(Generated)
        cpf.getPropertyDescriptor(GormProperties.IDENTITY)
        customerClass.getDeclaredMethod('addToRelated', Object)
        customerClass.getDeclaredMethod('addToRelated', Object).isAnnotationPresent(Generated)
        customerClass.getDeclaredMethod('removeFromRelated', Object)
        customerClass.getDeclaredMethod('removeFromRelated', Object).isAnnotationPresent(Generated)
    }

    void 'a class annotated with @JpaEntity but not @jakarta.persistence.Entity is made a GORM entity and gets the JPA annotation added'() {
        given: 'a class using the local, annotation-driven transform entry point instead of the global one'
        def productClass = new GroovyClassLoader().parseClass('''
            import grails.gorm.annotation.JpaEntity

            @JpaEntity
            class Product {
                Long myId
                String name
            }
        ''')

        expect: 'the transform added the missing @jakarta.persistence.Entity annotation itself'
        productClass.getAnnotation(jakarta.persistence.Entity)

        and: 'the same GORM entity enhancement as the globally-triggered path was applied'
        GormEntity.isAssignableFrom(productClass)
    }

    void 'a class already annotated with @jakarta.persistence.Entity is not annotated a second time'() {
        given: 'a class carrying both annotations, so the local transform entry point runs but the class already has the JPA annotation'
        def alreadyJpaClass = new GroovyClassLoader().parseClass('''
            import grails.gorm.annotation.JpaEntity

            @JpaEntity
            @jakarta.persistence.Entity
            class AlreadyJpaProduct {
                Long myId
            }
        ''')

        expect:
        alreadyJpaClass.getDeclaredAnnotations().findAll { it.annotationType() == jakarta.persistence.Entity }.size() == 1
    }

    void 'priority orders the transform relative to the dirty-check transform'() {
        expect:
        new JpaGormEntityTransformation().priority() == GroovyTransformOrder.JPA_GORM_ENTITY_ORDER
    }
}

