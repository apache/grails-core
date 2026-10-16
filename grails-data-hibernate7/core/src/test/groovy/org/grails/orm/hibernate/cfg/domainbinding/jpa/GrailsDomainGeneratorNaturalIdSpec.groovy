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
import jakarta.persistence.Column
import org.hibernate.annotations.NaturalId
import org.hibernate.boot.Metadata
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * Describes how {@link GrailsDomainGenerator} states the natural identifier a mapping asks for with
 * {@code id natural: [properties: [...], mutable: ...]}: {@code @NaturalId} on each property, immutable unless the mapping
 * says so. The binder also adds one unique key over the natural id columns; Hibernate's annotation binder adds its own.
 */
class GrailsDomainGeneratorNaturalIdSpec extends GrailsDomainGeneratorSupport {

    void setupSpec() {
        manager.registerDomainClasses(GenNatTarget, GenNatMutable, GenNatImmutable, GenNatRef, GenNatRoot, GenNatChild, GenNatTypo, GenNatOnlyTypo, GenNatEmbedded, GenNatNone)
    }

    void "every property of a natural id is marked, and it is mutable only when the mapping says so"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenNatMutable, GenNatImmutable)
        Class<?> mutable = classes[entity(GenNatMutable)]
        Class<?> immutable = classes[entity(GenNatImmutable)]

        then:
        mutable.getDeclaredField('code').getAnnotation(NaturalId).mutable()
        mutable.getDeclaredField('region').getAnnotation(NaturalId).mutable()
        !mutable.getDeclaredField('note').isAnnotationPresent(NaturalId)
        !immutable.getDeclaredField('code').getAnnotation(NaturalId).mutable()
        !immutable.getDeclaredField('code').getAnnotation(Column).updatable()
        mutable.getDeclaredField('code').getAnnotation(Column).updatable()
    }

    void "an association can be part of the natural id"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenNatTarget, GenNatRef)

        then:
        classes[entity(GenNatRef)].getDeclaredField('target').isAnnotationPresent(NaturalId)
        classes[entity(GenNatRef)].getDeclaredField('code').isAnnotationPresent(NaturalId)
    }

    void "Hibernate's annotation binder reads the natural id as the binder bound it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenNatMutable, GenNatImmutable, GenNatTarget, GenNatRef)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass bound = entity(domain).persistentClass
        PersistentClass read = metadata.getEntityBinding(classes[entity(domain)].name)

        expect: "the same properties are natural, with the same updatability and columns, and one unique key spans their columns"
        naturalProperties(read) == naturalProperties(bound)
        naturalKeys(read) == naturalKeys(bound).collect { it.toSet() }

        where:
        domain << [GenNatMutable, GenNatImmutable, GenNatRef]
    }

    void "the facets name the properties in the order of the mapping, and an entity with no natural id has none"() {
        expect:
        newGenerator().naturalIdFacets(entity(GenNatMutable)) == new NaturalIdFacets(['code', 'region'], true)
        newGenerator().naturalIdFacets(entity(GenNatImmutable)) == new NaturalIdFacets(['code'], false)
        newGenerator().naturalIdFacets(entity(GenNatRef)) == new NaturalIdFacets(['target', 'code'], false)
        newGenerator().naturalIdFacets(entity(GenNatNone)) == null
        !generateGroup(GenNatNone).values().first().declaredFields.any { it.isAnnotationPresent(NaturalId) }
    }

    void "a name that is no property of the entity is skipped, as the domain binder skips it"() {
        expect:
        newGenerator().naturalIdFacets(entity(GenNatTypo)) == new NaturalIdFacets(['code'], false)
        newGenerator().naturalIdFacets(entity(GenNatOnlyTypo)) == null
        generateGroup(GenNatTypo).values().first().getDeclaredField('code').isAnnotationPresent(NaturalId)
        !generateGroup(GenNatOnlyTypo).values().first().declaredFields.any { it.isAnnotationPresent(NaturalId) }
    }

    void "an embedded property can be part of the natural id, and the root states it with the other properties"() {
        when:
        Class<?> generated = generateGroup(GenNatEmbedded).values().first()

        then:
        newGenerator().naturalIdFacets(entity(GenNatEmbedded)) == new NaturalIdFacets(['code', 'home'], false)
        generated.getDeclaredField('code').isAnnotationPresent(NaturalId)
        !generated.getDeclaredField('home').getAnnotation(NaturalId).mutable()
    }

    void "the natural id of a subclass is described by the facets but not stated: Hibernate refuses @NaturalId on a subclass"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenNatRoot, GenNatChild)

        then:
        newGenerator().naturalIdFacets(entity(GenNatChild)) == new NaturalIdFacets(['code'], false)
        !classes[entity(GenNatChild)].getDeclaredField('code').isAnnotationPresent(NaturalId)
        !classes[entity(GenNatChild)].getDeclaredField('code').getAnnotation(Column).updatable()
    }

    private static Map<String, List<Object>> naturalProperties(PersistentClass persistentClass) {
        return persistentClass.properties.findAll { Property p -> p.naturalIdentifier }.collectEntries { Property p ->
            [(p.name): [p.updateable, p.columns*.name]]
        } as Map<String, List<Object>>
    }

    private static List<Set<String>> naturalKeys(PersistentClass persistentClass) {
        Set<String> natural = persistentClass.properties.findAll { Property p -> p.naturalIdentifier }.collectMany { Property p -> p.columns*.name }.toSet()
        return persistentClass.table.uniqueKeys.values().collect { org.hibernate.mapping.UniqueKey key -> key.columns*.name.toSet() }
                .findAll { Set<String> columns -> columns == natural }
    }
}

@Entity
class GenNatTarget {

    String label
}

@Entity
class GenNatMutable {

    String code
    String region
    String note

    static mapping = {
        id natural: [properties: ['code', 'region'], mutable: true]
    }
}

@Entity
class GenNatImmutable {

    String code
    String note

    static mapping = {
        id natural: 'code'
    }
}

@Entity
class GenNatRef {

    GenNatTarget target
    String code

    static mapping = {
        id natural: ['target', 'code']
    }
}

@Entity
class GenNatRoot {

    String name
}

@Entity
class GenNatChild extends GenNatRoot {

    String code

    static mapping = {
        id natural: 'code'
    }
}

@Entity
class GenNatTypo {

    String code

    static mapping = {
        id natural: ['code', 'typo']
    }
}

@Entity
class GenNatOnlyTypo {

    String code

    static mapping = {
        id natural: ['typo']
    }
}

@Entity
class GenNatEmbedded {

    String code
    GenNatHome home

    static embedded = ['home']

    static mapping = {
        id natural: ['code', 'home']
    }
}

class GenNatHome {

    String street
}

@Entity
class GenNatNone {

    String code
}
