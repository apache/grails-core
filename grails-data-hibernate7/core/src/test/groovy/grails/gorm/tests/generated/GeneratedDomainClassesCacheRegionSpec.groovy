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

import javax.cache.Caching

import grails.persistence.Entity
import org.hibernate.cache.spi.access.CollectionDataAccess
import org.hibernate.cache.spi.access.EntityDataAccess
import org.hibernate.cache.spi.access.NaturalIdDataAccess
import org.hibernate.persister.collection.CollectionPersister
import org.hibernate.persister.entity.EntityPersister
import org.hibernate.stat.Statistics
import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore

/**
 * The second-level cache regions carry the names the classic binding of Grails 8 gave them: Hibernate derives a region name from
 * the entity name or the collection role, which are the names of the generated classes, so an application that configures its cache
 * regions by the names of its domain classes would silently lose that configuration. The regions stated here are the ones classic
 * binding gave.
 */
class GeneratedDomainClassesCacheRegionSpec extends Specification {

    @Shared HibernateDatastore generated

    void setupSpec() {
        generated = boot('gdcRegionsGenerated', [:])
    }

    void cleanupSpec() {
        generated?.close()
    }

    private static HibernateDatastore boot(String name, Map<String, Object> extra) {
        Map<String, Object> config = [
                'dataSource.url'                : "jdbc:h2:mem:${name};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'           : 'create-drop',
                'hibernate.cache'               : ['use_second_level_cache': true,
                                                   'region.factory_class'  : 'org.hibernate.cache.jcache.internal.JCacheRegionFactory'] +
                        extra.findAll { String key, Object value -> key.startsWith('hibernate.cache.') }
                                .collectEntries { String key, Object value -> [(key.substring('hibernate.cache.'.length())): value] },
        ] as Map<String, Object>
        return new HibernateDatastore(DatastoreUtils.createPropertyResolver(config),
                GdcRegionAuthor, GdcRegionBook, GdcRegionVehicle, GdcRegionCar, GdcRegionWheel, GdcRegionNatural,
                GdcRegionUncached, GdcRegionPerson, GdcRegionAddress)
    }

    private static Map<String, String> entityRegions(HibernateDatastore datastore) {
        Map<String, String> result = [:]
        datastore.sessionFactory.mappingMetamodel.forEachEntityDescriptor { EntityPersister persister ->
            EntityDataAccess access = persister.cacheAccessStrategy
            result.put(persister.mappedClass.name, access?.region?.name)
        }
        return result
    }

    private static Map<String, String> collectionRegions(HibernateDatastore datastore) {
        Map<String, String> result = [:]
        datastore.sessionFactory.mappingMetamodel.forEachCollectionDescriptor { CollectionPersister persister ->
            CollectionDataAccess access = persister.cacheAccessStrategy
            if (access != null) {
                EntityPersister owner = persister.ownerEntityPersister
                result.put(owner.mappedClass.name + persister.role.substring(owner.entityName.length()), access.region.name)
            }
        }
        return result
    }

    private static Map<String, String> naturalIdRegions(HibernateDatastore datastore) {
        Map<String, String> result = [:]
        datastore.sessionFactory.mappingMetamodel.forEachEntityDescriptor { EntityPersister persister ->
            NaturalIdDataAccess access = persister.naturalIdCacheAccessStrategy
            if (access != null) {
                result.put(persister.mappedClass.name, access.region.name)
            }
        }
        return result
    }

    def "a cached root entity and a cached subclass hierarchy use the region of the real root class"() {
        expect:
        entityRegions(generated)[GdcRegionBook.name] == GdcRegionBook.name
        entityRegions(generated)[GdcRegionVehicle.name] == GdcRegionVehicle.name
        entityRegions(generated)[GdcRegionCar.name] == GdcRegionVehicle.name
        entityRegions(generated)[GdcRegionUncached.name] == null
    }

    def "a cached collection uses the real role as its region, for a root and for a subclass owner"() {
        expect:
        collectionRegions(generated) == [
                (GdcRegionBook.name + '.authors'): GdcRegionBook.name + '.authors',
                (GdcRegionCar.name + '.wheels')  : GdcRegionCar.name + '.wheels']
    }

    def "a collection of an embedded type is not cached"() {
        expect:
        !collectionRegions(generated).keySet().any { String key -> key.startsWith(GdcRegionPerson.name) }
    }

    def "the regions are the regions classic binding gave"() {
        expect:
        entityRegions(generated) == [
                (GdcRegionAuthor.name)  : null, (GdcRegionBook.name): GdcRegionBook.name, (GdcRegionVehicle.name): GdcRegionVehicle.name,
                (GdcRegionCar.name)     : GdcRegionVehicle.name, (GdcRegionWheel.name): null, (GdcRegionNatural.name): GdcRegionNatural.name,
                (GdcRegionUncached.name): null, (GdcRegionPerson.name): null,
        ]
        regionNames(generated) == [GdcRegionBook.name, GdcRegionVehicle.name, GdcRegionNatural.name,
                                   GdcRegionBook.name + '.authors', GdcRegionCar.name + '.wheels'] as Set
    }

    def "a natural id of a cached entity has no cache of its own"() {
        expect:
        naturalIdRegions(generated).isEmpty()
    }

    def "the JCache caches carry the real names under the configured region prefix"() {
        given:
        HibernateDatastore prefixed = boot('gdcRegionsPrefixed', ['hibernate.cache.region_prefix': 'app'])
        Set<String> caches = Caching.cachingProvider.cacheManager.cacheNames.findAll { String name -> name.startsWith('app.') } as Set<String>

        expect:
        caches == [GdcRegionBook, GdcRegionVehicle, GdcRegionNatural].collect { Class type -> 'app.' + type.name } as Set<String> +
                ['app.' + GdcRegionBook.name + '.authors', 'app.' + GdcRegionCar.name + '.wheels']

        cleanup:
        prefixed?.close()
    }

    def "the second-level cache works end to end under the real region names"() {
        given: 'a datastore of its own, because GORM binds an entity class to the datastore booted last'
        HibernateDatastore datastore = boot('gdcRegionsEndToEnd', [:])
        Statistics statistics = datastore.sessionFactory.statistics
        statistics.statisticsEnabled = true
        Serializable id = GdcRegionBook.withNewTransaction {
            GdcRegionBook book = new GdcRegionBook(title: 'cached')
            book.save(failOnError: true)
            book.id
        }
        statistics.clear()

        when:
        GdcRegionBook.withNewSession { GdcRegionBook.get(id) }
        GdcRegionBook.withNewSession { GdcRegionBook.get(id) }

        then:
        statistics.getCacheRegionStatistics(GdcRegionBook.name).hitCount >= 1

        cleanup:
        datastore?.close()
    }

    private static Set<String> regionNames(HibernateDatastore datastore) {
        Set<String> names = [] as Set<String>
        names.addAll(entityRegions(datastore).values().findAll { String name -> name != null })
        names.addAll(collectionRegions(datastore).values())
        names.addAll(naturalIdRegions(datastore).values())
        return names
    }
}

@Entity
class GdcRegionAuthor {
    String name
}

@Entity
class GdcRegionBook {
    String title
    static hasMany = [authors: GdcRegionAuthor]
    static mapping = {
        cache true
        authors cache: true
    }
}

@Entity
class GdcRegionVehicle {
    String make
    static mapping = {
        cache true
    }
}

@Entity
class GdcRegionCar extends GdcRegionVehicle {
    int doors
    static hasMany = [wheels: GdcRegionWheel]
    static mapping = {
        wheels cache: true
    }
}

@Entity
class GdcRegionWheel {
    int size
}

@Entity
class GdcRegionNatural {
    String code
    static mapping = {
        cache true
        id natural: 'code'
    }
}

@Entity
class GdcRegionUncached {
    String name
}

@Entity
class GdcRegionPerson {
    GdcRegionAddress home
    static embedded = ['home']
}

class GdcRegionAddress {
    String city
    static hasMany = [tags: String]
    static mapping = {
        tags cache: true
    }
}
