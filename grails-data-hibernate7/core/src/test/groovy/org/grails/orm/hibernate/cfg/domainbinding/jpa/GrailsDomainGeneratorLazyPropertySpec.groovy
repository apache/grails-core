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

import grails.gorm.annotation.Entity
import jakarta.persistence.Basic
import jakarta.persistence.FetchType
import org.hibernate.boot.Metadata
import org.hibernate.mapping.PersistentClass

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * Describes how {@link GrailsDomainGenerator} states {@code lazy: true} on a simple or enum property: the binder marks
 * the Hibernate property lazy (a lazy attribute, which Hibernate loads on first access when the class is enhanced), and
 * the annotation for that is {@code @Basic(fetch = LAZY)}. Hibernate's annotation binder ignores that annotation on an
 * {@code @Embedded}, so a lazy embedded property is accepted and marked lazy when the generated classes are bound.
 */
class GrailsDomainGeneratorLazyPropertySpec extends GrailsDomainGeneratorSupport {

    void setupSpec() {
        manager.registerDomainClasses(GenLazyBasic, GenLazyEmbedded)
    }

    void "a lazy property is a basic attribute fetched lazily"() {
        when:
        Class<?> generated = generateGroup(GenLazyBasic).values().first()

        then:
        generated.getDeclaredField('big').getAnnotation(Basic).fetch() == FetchType.LAZY
        generated.getDeclaredField('state').getAnnotation(Basic).fetch() == FetchType.LAZY
        !generated.getDeclaredField('plain').isAnnotationPresent(Basic)
    }

    void "Hibernate's annotation binder reads the lazy properties as the binder bound them"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenLazyBasic)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass bound = entity(GenLazyBasic).persistentClass
        PersistentClass read = metadata.getEntityBinding(classes[entity(GenLazyBasic)].name)

        expect:
        ['plain', 'big', 'state'].every { String name -> bound.getProperty(name).lazy == read.getProperty(name).lazy }
        bound.getProperty('big').lazy
    }

    void "a lazy embedded property is accepted: annotations cannot state it, so the binder of the generated classes sets it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenLazyEmbedded)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass bound = entity(GenLazyEmbedded).persistentClass
        PersistentClass read = metadata.getEntityBinding(classes[entity(GenLazyEmbedded)].name)

        expect:
        bound.getProperty('home').lazy
        !read.getProperty('home').lazy
        !classes.values().first().getDeclaredField('home').isAnnotationPresent(Basic)
    }
}

@Entity
class GenLazyBasic {

    String plain
    String big
    GenLazyState state

    static mapping = {
        big lazy: true
        state lazy: true
    }
}

@Entity
class GenLazyEmbedded {

    GenLazyHome home

    static embedded = ['home']

    static mapping = {
        home lazy: true
    }
}

class GenLazyHome {

    String street
}

enum GenLazyState {
    ON, OFF
}
