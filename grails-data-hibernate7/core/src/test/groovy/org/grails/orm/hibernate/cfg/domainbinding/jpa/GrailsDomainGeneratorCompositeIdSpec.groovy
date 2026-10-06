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
import jakarta.persistence.AssociationOverride
import jakarta.persistence.AssociationOverrides
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.CollectionTable
import jakarta.persistence.JoinColumns
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.boot.Metadata
import org.hibernate.mapping.Collection as HibernateCollection
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.ToOne

import grails.unbootable.UnbootableComposite
import grails.unbootable.UnbootableFlat
import grails.unbootable.UnbootableJoinedChild
import grails.unbootable.UnbootableJoinedParent
import grails.unbootable.UnbootableMiddle
import grails.unbootable.UnbootableParts
import grails.unbootable.UnbootableRefToParts
import grails.unbootable.UnbootableRefToTop
import grails.unbootable.UnbootableTarget
import grails.unbootable.UnbootableTop
import grails.unbootable.UnbootableJoinToComposite
import grails.unbootable.UnbootableMmComposite
import grails.unbootable.UnbootableMmOther
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * Describes how {@link GrailsDomainGenerator} states a composite identifier ({@code id composite: [...]}) and the foreign keys
 * that point at one. The binder builds one identifier component from the parts, whose columns are the primary key, and a
 * foreign key to such an entity has one column for each identifier property. The generated class states each part as an
 * {@code @Id} field of its own and names a generated key class with {@code @IdClass}, which makes Hibernate bind a non-aggregated
 * identifier as the binder's component is, and a foreign key states {@code @JoinColumns} that name the column of the key each of
 * them points at. The differential spec compares the identifier and the foreign keys on every domain class.
 */
class GrailsDomainGeneratorCompositeIdSpec extends GrailsDomainGeneratorSupport {

    void setupSpec() {
        manager.registerDomainClasses(
                GenCidSimple, GenCidTarget, GenCidParts, GenCidRef, GenCidIndexed, GenCidNested, GenCidParent, GenCidChild,
                GenCidOwner, GenCidItem, GenCidKid, GenCidListedKid, GenCidRefNested, GenCidEmbOwner)
    }

    void "a composite identifier is an @IdClass of a generated key class that names the parts, and each part is an @Id field of the entity"() {
        when:
        Class<?> generated = generateGroup(GenCidSimple).values().first()
        Class<?> key = generated.getAnnotation(IdClass).value()

        then:
        !key.isAnnotationPresent(Embeddable)
        Serializable.isAssignableFrom(key)
        key.name == generated.name + '_Id'
        key.declaredFields*.name == ['last', 'age']
        key.getDeclaredField('last').type == String
        key.getDeclaredField('age').type == Long
        generated.declaredFields*.name.toSet() == ['last', 'age', 'version', 'note'].toSet()
        generated.getDeclaredField('last').isAnnotationPresent(Id)
        generated.getDeclaredField('age').isAnnotationPresent(Id)
        !generated.getDeclaredField('note').isAnnotationPresent(Id)
        generated.getDeclaredField('version').isAnnotationPresent(Version)
    }

    void "the parts are never null, whatever the mapping says, and are named as the binder names them"() {
        when:
        Class<?> generated = generateGroup(GenCidSimple).values().first()

        then:
        generated.getDeclaredField('last').getAnnotation(Column).name() == 'last'
        !generated.getDeclaredField('last').getAnnotation(Column).nullable()
        generated.getDeclaredField('age').getAnnotation(Column).name() == 'age'
        !generated.getDeclaredField('age').getAnnotation(Column).nullable()
    }

    void "a many-to-one part is an @Id association of the entity with its foreign key column, typed as the generated class of its target in the key class too"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidParts, GenCidTarget)
        Class<?> generated = classes[entity(GenCidParts)]
        Class<?> key = generated.getAnnotation(IdClass).value()

        then:
        generated.getDeclaredField('owner').isAnnotationPresent(Id)
        generated.getDeclaredField('owner').isAnnotationPresent(ManyToOne)
        !generated.getDeclaredField('owner').getAnnotation(ManyToOne).optional()
        generated.getDeclaredField('owner').getAnnotation(JoinColumn).name() == 'owner_id'
        !generated.getDeclaredField('owner').getAnnotation(JoinColumn).nullable()
        generated.getDeclaredField('owner').type == classes[entity(GenCidTarget)]
        key.getDeclaredField('owner').type == classes[entity(GenCidTarget)]
        key.declaredFields*.name == ['name', 'owner']
    }

    void "Hibernate's annotation binder reads the composite identifier as a non-aggregated identifier like the binder's"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidSimple, GenCidParts, GenCidTarget)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass bound = entity(domain).persistentClass
        PersistentClass read = metadata.getEntityBinding(classes[entity(domain)].name)

        expect: "no identifier property, an embedded identifier component with the same parts, and the same primary key columns"
        read.identifierProperty == null
        ((Component) read.identifier).embedded
        parts(read) == parts(bound)
        read.table.primaryKey.columns*.name.toSet() == bound.table.primaryKey.columns*.name.toSet()
        read.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet() ==
                bound.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet()

        where:
        domain << [GenCidSimple, GenCidParts]
    }

    void "a foreign key to a composite identifier has a join column for each identifier property, naming the key column it points at"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidRef, GenCidSimple)
        JoinColumns plain = classes[entity(GenCidRef)].getDeclaredField('plain').getAnnotation(JoinColumns)
        JoinColumns named = classes[entity(GenCidRef)].getDeclaredField('named').getAnnotation(JoinColumns)

        then: "the default names are the table of the target and the name of the identifier property, in the order of the mapping"
        plain.value()*.name() == ['gen_cid_simple_last', 'gen_cid_simple_age']
        plain.value()*.referencedColumnName() == ['last', 'age']
        plain.value().every { it.nullable() }
        named.value()*.name() == ['n_last', 'n_age']
        named.value()*.referencedColumnName() == ['last', 'age']
        !classes[entity(GenCidRef)].getDeclaredField('plain').isAnnotationPresent(JoinColumn)
    }

    void "Hibernate's annotation binder reads the foreign key to a composite identifier as the binder bound it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidRef, GenCidSimple)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass bound = entity(GenCidRef).persistentClass
        PersistentClass read = metadata.getEntityBinding(classes[entity(GenCidRef)].name)

        expect: "the same columns for each association, in whatever order Hibernate arranges them, and the same constraints"
        ['plain', 'named'].every { String name ->
            ((ToOne) bound.getProperty(name).value).selectables*.text.toSet() == ((ToOne) read.getProperty(name).value).selectables*.text.toSet() &&
                    bound.getProperty(name).columns*.nullable.toSet() == read.getProperty(name).columns*.nullable.toSet()
        }
        read.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet() ==
                bound.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet()
    }

    void "a collection of basic values of an entity with a composite identifier has a key column for each identifier property"() {
        when:
        Class<?> generated = generateGroup(GenCidOwner, GenCidItem, GenCidKid, GenCidListedKid).get(entity(GenCidOwner))
        CollectionTable table = generated.getDeclaredField('tags').getAnnotation(CollectionTable)

        then:
        table.name() == 'gen_cid_owner_tags'
        table.joinColumns()*.name() == ['gen_cid_owner_code', 'gen_cid_owner_region']
        table.joinColumns()*.referencedColumnName() == ['code', 'region']
    }

    void "a unidirectional collection of an entity with a composite identifier is a join table whose key has a column for each identifier property"() {
        when:
        Class<?> generated = generateGroup(GenCidOwner, GenCidItem, GenCidKid, GenCidListedKid).get(entity(GenCidOwner))
        JoinTable table = generated.getDeclaredField('items').getAnnotation(JoinTable)

        then:
        table.joinColumns()*.name() == ['gen_cid_owner_code', 'gen_cid_owner_region']
        table.joinColumns()*.referencedColumnName() == ['code', 'region']
        table.inverseJoinColumns()*.name() == ['gen_cid_item_id']
    }

    void "a collection mapped by the foreign key of the other side needs no columns, and an indexed list names the foreign key columns it manages"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidOwner, GenCidItem, GenCidKid, GenCidListedKid)
        Class<?> owner = classes[entity(GenCidOwner)]

        then:
        owner.getDeclaredField('kids').getAnnotation(OneToMany).mappedBy() == 'owner'
        owner.getDeclaredField('listedKids').getAnnotation(OneToMany).mappedBy() == ''
        owner.getDeclaredField('listedKids').getAnnotation(JoinColumns).value()*.name() == ['gen_cid_owner_code', 'gen_cid_owner_region']
        classes[entity(GenCidKid)].getDeclaredField('owner').getAnnotation(JoinColumns).value()*.referencedColumnName() == ['code', 'region']
    }

    void "Hibernate's annotation binder reads the collections of an entity with a composite identifier as the binder bound them"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidOwner, GenCidItem, GenCidKid, GenCidListedKid)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass bound = entity(GenCidOwner).persistentClass
        PersistentClass read = metadata.getEntityBinding(classes[entity(GenCidOwner)].name)

        expect: "the same table and the same key columns, whatever the order Hibernate arranges them in"
        ['tags', 'items', 'kids', 'listedKids'].every { String name ->
            HibernateCollection b = (HibernateCollection) bound.getProperty(name).value
            HibernateCollection r = (HibernateCollection) read.getProperty(name).value
            b.key.columns*.name.toSet() == r.key.columns*.name.toSet() && b.inverse == r.inverse &&
                    (b.oneToMany || b.collectionTable.name == r.collectionTable.name)
        }
    }

    void "a collection to an entity with a composite identifier that the generator cannot describe is rejected by name"() {
        when:
        newGenerator().generateAll(unbound(domain, composite), getClass().classLoader)

        then: "the binder cannot boot a join table to a composite identifier either (see GrailsDomainBinderCompositeIdDefectSpec)"
        UnsupportedOperationException e = thrown()
        e.message.contains(domain.simpleName)
        e.message.contains(property)
        e.message.contains(reason)

        where:
        domain                    | composite             | property     | reason
        UnbootableJoinToComposite | UnbootableComposite   | 'targets'    | 'join table'
        UnbootableMmOther         | UnbootableMmComposite | 'composites' | 'many-to-many'
    }

    void "a foreign key to a composite identifier inside an embedded type is stated with an association override for each column"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidEmbOwner, GenCidSimple)
        Class<?> owner = classes[entity(GenCidEmbOwner)]
        AssociationOverride override = owner.getDeclaredField('home').getAnnotation(AssociationOverrides).value().find { it.name() == 'ref' }

        then:
        override.joinColumns()*.name() == ['gen_cid_simple_last', 'gen_cid_simple_age']
        override.joinColumns()*.referencedColumnName() == ['last', 'age']
        owner.getDeclaredField('home').type.getDeclaredField('ref').getAnnotation(JoinColumns).value()*.name() == ['gen_cid_simple_last', 'gen_cid_simple_age']
    }

    void "Hibernate's annotation binder reads a foreign key to a composite identifier inside an embedded type as the binder bound it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidEmbOwner, GenCidSimple)
        Metadata metadata = annotationMetadata(classes.values())
        Component bound = (Component) entity(GenCidEmbOwner).persistentClass.getProperty('home').value
        Component read = (Component) metadata.getEntityBinding(classes[entity(GenCidEmbOwner)].name).getProperty('home').value

        expect:
        read.getProperty('ref').columns*.name.toSet() == bound.getProperty('ref').columns*.name.toSet()
        read.getProperty('ref').columns*.nullable == bound.getProperty('ref').columns*.nullable
    }

    void "an index on a part of the identifier is an index of the table, as the binder binds it"() {
        when:
        Class<?> generated = generateGroup(GenCidIndexed).values().first()

        then:
        generated.getAnnotation(Table).indexes()*.name() == ['gen_cid_a_idx']
        generated.getAnnotation(Table).indexes()*.columnList() == ['a']
        entity(GenCidIndexed).persistentClass.table.indexes.keySet() == ['gen_cid_a_idx'].toSet()
    }

    void "an identifier part that refers to an entity with a composite identifier has a join column for each of its identifier properties"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidNested, GenCidSimple)
        JoinColumns parent = classes[entity(GenCidNested)].getDeclaredField('parent').getAnnotation(JoinColumns)

        then:
        parent.value()*.name() == ['gen_cid_simple_last', 'gen_cid_simple_age']
        parent.value()*.referencedColumnName() == ['last', 'age']
        parent.value().every { !it.nullable() }
    }

    void "a foreign key to a composite identifier that has such a part expands the part, one column for each of its identifier properties"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidRefNested, GenCidNested, GenCidSimple)
        JoinColumns target = classes[entity(GenCidRefNested)].getDeclaredField('target').getAnnotation(JoinColumns)

        then: "the columns are named after the part and the identifier property of the part, and each points at the column the part has in the key"
        target.value()*.name() == ['gen_cid_nested_parent_last', 'gen_cid_nested_parent_age', 'gen_cid_nested_name']
        target.value()*.referencedColumnName() == ['gen_cid_simple_last', 'gen_cid_simple_age', 'name']
    }

    void "Hibernate's annotation binder reads a composite identifier with such a part, and a foreign key to it, as the binder bound them"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidRefNested, GenCidNested, GenCidSimple)
        Metadata metadata = annotationMetadata(classes.values())
        PersistentClass boundKey = entity(GenCidNested).persistentClass
        PersistentClass readKey = metadata.getEntityBinding(classes[entity(GenCidNested)].name)
        PersistentClass bound = entity(GenCidRefNested).persistentClass
        PersistentClass read = metadata.getEntityBinding(classes[entity(GenCidRefNested)].name)

        expect:
        readKey.table.primaryKey.columns*.name.toSet() == boundKey.table.primaryKey.columns*.name.toSet()
        ((ToOne) bound.getProperty('target').value).selectables*.text.toSet() == ((ToOne) read.getProperty('target').value).selectables*.text.toSet()
        read.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet() ==
                bound.table.foreignKeys.values().collect { it.columns*.name.toSet() }.toSet()
    }

    void "a foreign key to a composite identifier that the binder cannot bind is rejected by name"() {
        when:
        newGenerator().generateAll(unbound(domain, *others), getClass().classLoader)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains(domain.simpleName)
        e.message.contains(reason)

        where:
        domain                | others                                                 | reason
        UnbootableRefToParts  | [UnbootableParts, UnbootableTarget]                     | 'ForeignKeyColumnCountCalculator'
        UnbootableRefToTop    | [UnbootableTop, UnbootableMiddle, UnbootableFlat]       | 'one level deep'
    }

    void "a single-table subclass of an entity with a composite identifier shares the key of its root"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCidParent, GenCidChild)

        then:
        classes[entity(GenCidChild)].superclass == classes[entity(GenCidParent)]
        classes[entity(GenCidChild)].declaredFields*.name == ['c']
        classes[entity(GenCidParent)].isAnnotationPresent(IdClass)
    }

    void "a joined subclass of an entity with a composite identifier is rejected by name"() {
        when:
        newGenerator().generateAll(unbound(UnbootableJoinedParent, UnbootableJoinedChild), getClass().classLoader)

        then: "the binder binds the key of a joined subclass with one column (see GrailsDomainBinderCompositeIdDefectSpec)"
        UnsupportedOperationException e = thrown()
        e.message.contains('UnbootableJoinedParent')
        e.message.contains('joined subclass')
    }

    private static Map<String, Map<String, Object>> parts(PersistentClass persistentClass) {
        Component id = (Component) persistentClass.identifier
        return id.properties.collectEntries { Property part ->
            [(part.name): [columns: part.columns*.name, nullable: part.columns*.nullable, toOne: part.value instanceof ToOne]]
        } as Map<String, Map<String, Object>>
    }
}

@Entity
class GenCidSimple implements Serializable {

    String last
    Long age
    String note

    static mapping = {
        id composite: ['last', 'age']
    }
}

@Entity
class GenCidTarget {

    String label
}

@Entity
class GenCidParts implements Serializable {

    GenCidTarget owner
    String name
    String extra

    static mapping = {
        id composite: ['name', 'owner']
    }
}

@Entity
class GenCidRef {

    GenCidSimple plain
    GenCidSimple named

    static mapping = {
        named {
            column name: 'n_last'
            column name: 'n_age'
        }
    }
}

@Entity
class GenCidIndexed implements Serializable {

    String a
    String b

    static mapping = {
        id composite: ['a', 'b']
        a index: 'gen_cid_a_idx'
    }
}

@Entity
class GenCidNested implements Serializable {

    GenCidSimple parent
    String name

    static mapping = {
        id composite: ['parent', 'name']
    }
}

@Entity
class GenCidParent implements Serializable {

    String a
    String b

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class GenCidChild extends GenCidParent {

    String c
}

@Entity
class GenCidOwner implements Serializable {

    String code
    String region
    Set<String> tags
    Set<GenCidItem> items
    Set<GenCidKid> kids
    List<GenCidListedKid> listedKids

    static hasMany = [tags: String, items: GenCidItem, kids: GenCidKid, listedKids: GenCidListedKid]

    static mapping = {
        id composite: ['code', 'region']
    }
}

@Entity
class GenCidItem {

    String label
}

@Entity
class GenCidKid {

    String name
    GenCidOwner owner

    static belongsTo = [owner: GenCidOwner]
}

@Entity
class GenCidListedKid {

    String name
    GenCidOwner owner

    static belongsTo = [owner: GenCidOwner]
}

@Entity
class GenCidRefNested {

    GenCidNested target
}

@Entity
class GenCidEmbOwner {

    GenCidEmbHome home

    static embedded = ['home']
}

class GenCidEmbHome {

    GenCidSimple ref
    String street
}
