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
package grails.gorm.tests.generated

import groovy.transform.CompileStatic
import grails.gorm.annotation.Entity
import org.hibernate.Hibernate
import org.hibernate.LazyInitializationException
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.dialect.H2Dialect
import org.hibernate.metamodel.MappingMetamodel
import org.hibernate.persister.entity.EntityPersister
import org.hibernate.proxy.HibernateProxy
import spock.lang.AutoCleanup
import spock.lang.PendingFeature
import spock.lang.Specification

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

/**
 * What the generated carrier classes may not do: leak. They have the names of the real domain classes and live in a loader of
 * their own, so serialization, by-name lookups and every Class that Hibernate hands out must give the real class, as with the
 * classic binding of Grails 8.
 */
class GeneratedDomainClassesSerializationSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot() {
        datastore?.close()
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'dataSource.url'                 : "jdbc:h2:mem:gdcSer${System.nanoTime()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'            : 'create-drop',
                'dataSource.dialect'             : H2Dialect.name,
                'hibernate.hbm2ddl.auto'         : 'create-drop',
                'hibernate.cache.queries'        : 'false',
                'hibernate.cache.use_query_cache': 'false',
        ]), GdcSerAuthor, GdcSerBook, GdcSerPet, GdcSerDog, GdcSerComposite)
        return datastore
    }

    private static byte[] serialize(Object value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { ObjectOutputStream out -> out.writeObject(value) }
        return bytes.toByteArray()
    }

    private static Object deserialize(byte[] bytes) {
        return new ObjectInputStream(new ByteArrayInputStream(bytes)).withCloseable { ObjectInputStream input -> input.readObject() }
    }

    private static boolean leaks(Class<?> type) {
        return type != null && (type.name.startsWith('org.grails.orm.hibernate.generated') ||
                type.classLoader?.getClass()?.simpleName == 'ByteArrayClassLoader')
    }

    private Map scenario() {
        HibernateDatastore booted = boot()
        Map r = [:]
        Long authorId
        GdcSerAuthor.withTransaction {
            GdcSerAuthor author = new GdcSerAuthor(name: 'a', home: new GdcSerAddress(street: 's', city: 'c'), status: GdcSerStatus.OPEN)
            author.addToBooks(new GdcSerBook(title: 'b1'))
            author.addToBooks(new GdcSerBook(title: 'b2'))
            author.save(flush: true, failOnError: true)
            authorId = author.id
            new GdcSerDog(name: 'rex', breed: 'lab').save(flush: true, failOnError: true)
            new GdcSerComposite(keyA: 'x', keyB: 'y', label: 'c').save(flush: true, failOnError: true)
        }

        GdcSerAuthor detached = GdcSerAuthor.withNewSession { GdcSerAuthor found = GdcSerAuthor.get(authorId); found.books.size(); found }
        byte[] bytes = serialize(detached)
        GdcSerAuthor copy = (GdcSerAuthor) deserialize(bytes)
        r.detachedCopy = [copy.getClass() == GdcSerAuthor, copy.name, copy.books*.title.sort(), copy.books.getClass().name,
                          copy.home.getClass() == GdcSerAddress, copy.status, copy.books.first().getClass() == GdcSerBook]
        r.streamMentionsGenerated = new String(bytes, 'ISO-8859-1').contains('org.grails.orm.hibernate.generated')

        GdcSerAuthor lazy = GdcSerAuthor.withNewSession { GdcSerAuthor.get(authorId) }
        GdcSerAuthor lazyCopy = (GdcSerAuthor) deserialize(serialize(lazy))
        r.lazyInitializedBefore = Hibernate.isInitialized(lazyCopy.books)
        try {
            lazyCopy.books.size()
            r.lazyAccess = 'loaded'
        } catch (LazyInitializationException ignored) {
            r.lazyAccess = 'LazyInitializationException'
        }

        copy.name = 'renamed'
        copy.books.find { it.title == 'b1' }.title = 'b1 changed'
        GdcSerAuthor.withTransaction {
            GdcSerAuthor.withSession { Session s -> s.merge(copy) }
        }
        r.afterMerge = GdcSerAuthor.withNewSession {
            GdcSerAuthor found = GdcSerAuthor.get(authorId)
            [found.name, found.books*.title.sort(), found.version]
        }
        GdcSerAuthor reattached = (GdcSerAuthor) deserialize(serialize(GdcSerAuthor.withNewSession { GdcSerAuthor.get(authorId) }))
        reattached.name = 'reattached'
        GdcSerAuthor.withTransaction { reattached.attach(); reattached.save(flush: true) }
        r.afterAttach = GdcSerAuthor.withNewSession { GdcSerAuthor.get(authorId).name }

        GdcSerAuthor.withNewSession { Session s ->
            Object proxy = s.getReference(GdcSerAuthor, authorId)
            r.proxy = [proxy instanceof HibernateProxy, proxy.getClass().superclass == GdcSerAuthor,
                       ProxyInspector.persistentClass(proxy) == GdcSerAuthor,
                       ProxyInspector.entityName(proxy) == GdcSerAuthor.name,
                       s.getEntityName(proxy) == GdcSerAuthor.name, Hibernate.getClass(proxy) == GdcSerAuthor,
                       Hibernate.isInitialized(proxy)]
            try {
                Object proxyCopy = deserialize(serialize(proxy))
                r.proxySerialization = [proxyCopy instanceof HibernateProxy, proxyCopy.getClass().superclass == GdcSerAuthor,
                                        proxyCopy.name]
            } catch (Exception e) {
                r.proxySerialization = e.class.name
            }
        }

        GdcSerAuthor.withNewSession { Session s ->
            SessionFactory sf = s.sessionFactory
            MappingMetamodel mm = sf.mappingMetamodel
            EntityPersister persister = mm.getEntityDescriptor(GdcSerAuthor.name)
            GdcSerAuthor found = s.get(GdcSerAuthor.name, authorId)
            r.byName = [
                    persister.mappedClass == GdcSerAuthor,
                    mm.getEntityDescriptor(GdcSerAuthor).entityName,
                    mm.findEntityDescriptor(GdcSerAuthor.name)?.mappedClass == GdcSerAuthor,
                    mm.getEntityDescriptor(GdcSerPet).subclassEntityNames.toList().sort(),
                    Class.forName(persister.entityName) == GdcSerAuthor,
                    Class.forName(persister.entityName, true, Thread.currentThread().contextClassLoader) == GdcSerAuthor,
                    found.getClass() == GdcSerAuthor,
                    s.getEntityName(found),
                    sf.metamodel.entity(GdcSerAuthor).javaType == GdcSerAuthor,
                    sf.metamodel.entity(GdcSerAuthor.name).javaType == GdcSerAuthor,
                    sf.metamodel.embeddable(GdcSerAddress).javaType == GdcSerAddress,
                    booted.metadata.getEntityBinding(GdcSerAuthor.name).mappedClass == GdcSerAuthor,
                    booted.metadata.getEntityBinding(GdcSerAuthor.name).proxyInterface == GdcSerAuthor,
                    s.createQuery("from ${GdcSerAuthor.name} a where a.name = :n".toString(), GdcSerAuthor).setParameter('n', 'reattached').resultList.size(),
                    s.createQuery('from GdcSerAuthor', GdcSerAuthor).resultList.size(),
                    s.createEntityGraph(GdcSerAuthor).graphedType.javaType == GdcSerAuthor,
                    s.criteriaBuilder.createQuery(GdcSerAuthor).from(GdcSerAuthor).javaType == GdcSerAuthor,
            ]
            r.polymorphic = [s.get(GdcSerPet, GdcSerPet.list().first().id).getClass() == GdcSerDog,
                             s.getEntityName(s.get(GdcSerPet, GdcSerPet.list().first().id)),
                             s.createQuery('from GdcSerPet', GdcSerPet).resultList*.getClass()*.name]
            GdcSerAddress probeAddress = new GdcSerAddress(street: 's', city: 'c')
            r.embeddedQueries = [
                    s.createQuery('select a.home from GdcSerAuthor a', GdcSerAddress).resultList.collect { [it.getClass() == GdcSerAddress, it.city] },
                    s.createQuery('from GdcSerAuthor a where a.home = :h', GdcSerAuthor).setParameter('h', probeAddress).resultList.size(),
                    s.createQuery('from GdcSerAuthor a where a.home.city = :c', GdcSerAuthor).setParameter('c', 'c').resultList.size(),
                    GdcSerAuthor.findAllByHome(probeAddress).size(),
                    GdcSerAuthor.where { home.city == 'c' }.count(),
            ]
            r.propertyTypes = [home: mm.getEntityDescriptor(GdcSerAuthor).getPropertyType('home').returnedClass.name,
                               id  : mm.getEntityDescriptor(GdcSerComposite).identifierType.returnedClass.name]
            r.composite = s.createQuery('from GdcSerComposite', GdcSerComposite).resultList.collect { [it.getClass() == GdcSerComposite, it.keyA] }
        }

        Set<Class<?>> seen = new LinkedHashSet<Class<?>>()
        Closure collect = { Closure<Object> source ->
            try {
                Object value = source.call()
                if (value instanceof Class) {
                    seen << (Class<?>) value
                }
            } catch (Exception ignored) {
                // a part of the model that has no such class
            }
        }
        HibernateDatastore ds = booted
        MappingMetamodel mm = ds.sessionFactory.mappingMetamodel
        mm.forEachEntityDescriptor { EntityPersister p ->
            collect { p.mappedClass }
            collect { p.concreteProxyClass }
            collect { p.entityMetamodel.mappedClass }
            collect { p.identifierMapping.getJavaType().javaTypeClass }
            collect { p.representationStrategy.mappedJavaType.javaTypeClass }
            collect { p.identifierType.returnedClass }
            collect { ds.metadata.getEntityBinding(p.entityName).mappedClass }
            collect { ds.metadata.getEntityBinding(p.entityName).proxyInterface }
            p.attributeMappings.each { attribute -> collect { attribute.getJavaType().javaTypeClass } }
            p.entityMetamodel.properties.each { property -> collect { property.type.returnedClass } }
        }
        ds.sessionFactory.metamodel.managedTypes.each { type -> collect { type.javaType } }
        ds.sessionFactory.metamodel.entities.each { entity -> entity.attributes.each { attribute -> collect { attribute.javaType } } }
        r.leakedClasses = seen.findAll { it != null && leaks(it) }*.name.sort()
        r.mappedClasses = seen.findAll { it != null }*.name.findAll { it.startsWith('grails.gorm.tests.generated') }.toSet().sort()
        return r
    }

    private static Map RESULTS

    private Map results() {
        if (RESULTS == null) {
            RESULTS = scenario()
        }
        return RESULTS
    }

    def "real instances serialize and deserialize as the real classes, detached, with their collections, and reattach"() {
        when:
        Map generated = results()

        then: 'a detached entity with an initialized collection and an embedded value comes back as the real classes'
        generated.detachedCopy == [true, 'a', ['b1', 'b2'], 'org.hibernate.collection.spi.PersistentSet', true, GdcSerStatus.OPEN, true]
        !generated.streamMentionsGenerated

        and: 'an uninitialized collection stays lazy and fails outside a session, as it did with classic binding'
        generated.lazyInitializedBefore == false
        generated.lazyAccess == 'LazyInitializationException'

        and: 'the detached copy merges into a new session, cascading to its collection, and a deserialized entity reattaches'
        generated.afterMerge == ['renamed', ['b1 changed', 'b2'], 1]
        generated.afterAttach == 'reattached'
    }

    def "proxies are proxies of the real class, and a serialized proxy deserializes as it did with classic binding"() {
        when:
        Map generated = results()

        then: 'superclass, persistent class, entity name and Hibernate.getClass are the real ones'
        generated.proxy == [true, true, true, true, true, true, true]

        and: 'a serialized proxy deserializes as a plain instance of the real class with its state, as it did with classic binding'
        generated.proxySerialization == [false, false, 'reattached']
    }

    def "lookups by entity name, class name and class give the real class"() {
        when:
        Map generated = results()

        then:
        generated.byName == [
                true, GdcSerAuthor.name, true, [GdcSerDog.name, GdcSerPet.name], true, true, true, GdcSerAuthor.name,
                true, true, true, true, true, 1, 1, true, true,
        ]
        generated.polymorphic == [true, GdcSerDog.name, [GdcSerDog.name]]
        generated.composite == [[true, 'x']]
        generated.embeddedQueries == [[[true, 'c']], 1, 1, 1, 1]
    }

    def "no class that the mapping and JPA metamodels hand out is a generated class, but the cached type of an embedded value and of a composite identifier"() {
        when:
        Map generated = results()

        then: 'the model has the real classes, under their own names'
        generated.mappedClasses.containsAll([GdcSerAuthor, GdcSerBook, GdcSerPet, GdcSerDog, GdcSerComposite, GdcSerAddress]*.name)

        and: 'Hibernate builds the component type of an embedded property and of a composite identifier before the switch to the real classes and caches its class, which has no public way to be reset'
        generated.leakedClasses == [
                'org.grails.orm.hibernate.generated.grails_gorm_tests_generated_GdcSerAddress_Embeddable',
                'org.grails.orm.hibernate.generated.grails_gorm_tests_generated_GdcSerComposite_Id',
        ]
    }

    @PendingFeature(reason = 'Component.getType() caches the component class when Hibernate first asks for the type while binding the generated classes; Component has no public way to reset it')
    def "the returned class of an embedded property and of a composite identifier is the real class"() {
        expect:
        results().leakedClasses == []
    }
}

@CompileStatic
class ProxyInspector {
    static Class<?> persistentClass(Object proxy) {
        return ((HibernateProxy) proxy).getHibernateLazyInitializer().getPersistentClass()
    }

    static String entityName(Object proxy) {
        return ((HibernateProxy) proxy).getHibernateLazyInitializer().getEntityName()
    }
}

enum GdcSerStatus { OPEN, CLOSED }

class GdcSerAddress implements Serializable {
    String street
    String city
}

@Entity
class GdcSerAuthor implements Serializable {
    String name
    GdcSerStatus status
    GdcSerAddress home
    static embedded = ['home']
    static hasMany = [books: GdcSerBook]
}

@Entity
class GdcSerBook implements Serializable {
    String title
    static belongsTo = [author: GdcSerAuthor]
}

@Entity
class GdcSerPet implements Serializable {
    String name
}

@Entity
class GdcSerDog extends GdcSerPet {
    String breed
}

@Entity
class GdcSerComposite implements Serializable {
    String keyA
    String keyB
    String label
    static mapping = {
        id composite: ['keyA', 'keyB']
    }
}
