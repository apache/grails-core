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
import jakarta.persistence.Cacheable
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import org.hibernate.annotations.Immutable
import org.hibernate.boot.Metadata
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass

import org.grails.orm.hibernate.cfg.CacheConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

/**
 * Describes how {@link GrailsDomainGenerator} states the second-level cache of an entity ({@code cache true},
 * {@code cache usage: ..., include: ...}): {@code @Cacheable} with {@code @Cache(usage, includeLazy)}, and {@code @Immutable}
 * for the {@code read-only} usage, as the binder makes the class immutable. The cache is a setting of the root of a hierarchy.
 */
class GrailsDomainGeneratorCacheSpec extends GrailsDomainGeneratorSupport {

    void setupSpec() {
        manager.registerDomainClasses(GenCacheDefault, GenCacheReadOnly, GenCacheNonStrict, GenCacheOff, GenCacheNone, GenCacheRoot, GenCacheLeaf, GenCacheBogus)
    }

    void "an enabled cache is stated with its usage, and lazy properties are included unless the mapping says non-lazy"() {
        when:
        Class<?> generated = generateGroup(domain).values().first()

        then:
        generated.isAnnotationPresent(Cacheable)
        generated.getAnnotation(Cache).usage() == usage
        generated.getAnnotation(Cache).includeLazy() == includeLazy
        generated.isAnnotationPresent(Immutable) == immutable

        where:
        domain             | usage                                    | includeLazy | immutable
        GenCacheDefault    | CacheConcurrencyStrategy.READ_WRITE      | true        | false
        GenCacheReadOnly   | CacheConcurrencyStrategy.READ_ONLY       | false       | true
        GenCacheNonStrict  | CacheConcurrencyStrategy.NONSTRICT_READ_WRITE | true   | false
    }

    void "a disabled cache and no cache state nothing"() {
        when:
        Class<?> generated = generateGroup(domain).values().first()

        then:
        !generated.isAnnotationPresent(Cacheable)
        !generated.isAnnotationPresent(Cache)
        !generated.isAnnotationPresent(Immutable)

        where:
        domain << [GenCacheOff, GenCacheNone]
    }

    void "the facets are the usage of the mapping, whether lazy properties are cached and whether the class is mutable, and only a root has them"() {
        expect:
        newGenerator().cacheFacets(entity(GenCacheDefault)) == new CacheFacets('read-write', true, true)
        newGenerator().cacheFacets(entity(GenCacheReadOnly)) == new CacheFacets('read-only', false, false)
        newGenerator().cacheFacets(entity(GenCacheOff)) == null
        newGenerator().cacheFacets(entity(GenCacheNone)) == null
        newGenerator().cacheFacets(entity(GenCacheLeaf)) == null
    }

    void "a cache usage that @Cache has no strategy for is rejected by name"() {
        given:
        CacheConfig cache = entity(GenCacheBogus).hibernateMappedForm.cache
        CacheConfig.Usage original = cache.usage
        cache.setUsage('bogus')

        when:
        generateGroup(GenCacheBogus)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('GenCacheBogus')
        e.message.contains('bogus')

        cleanup:
        cache.usage = original
    }

    void "the cache of a hierarchy is stated on its root"() {
        when:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCacheRoot, GenCacheLeaf)

        then:
        classes[entity(GenCacheRoot)].isAnnotationPresent(Cache)
        !classes[entity(GenCacheLeaf)].isAnnotationPresent(Cache)
    }

    void "Hibernate's annotation binder reads the cache as the binder bound it"() {
        given:
        Map<GrailsHibernatePersistentEntity, Class<?>> classes =
                generateGroup(GenCacheDefault, GenCacheReadOnly, GenCacheNonStrict, GenCacheOff, GenCacheNone, GenCacheRoot, GenCacheLeaf)
        Metadata metadata = annotationMetadata(classes.values())

        expect:
        [GenCacheDefault, GenCacheReadOnly, GenCacheNonStrict, GenCacheOff, GenCacheNone, GenCacheRoot].every { Class<?> domain ->
            cacheState((RootClass) entity(domain).persistentClass) ==
                    cacheState((RootClass) metadata.getEntityBinding(classes[entity(domain)].name))
        }
    }

    private static Map<String, Object> cacheState(RootClass root) {
        // a class that is not cached has a default strategy and lazy-property setting that mean nothing
        return root.cached ?
                [cached: true, strategy: root.cacheConcurrencyStrategy, mutable: root.mutable, includeLazy: root.lazyPropertiesCacheable] :
                [cached: false, mutable: root.mutable]
    }
}

@Entity
class GenCacheDefault {

    String name

    static mapping = {
        cache true
    }
}

@Entity
class GenCacheReadOnly {

    String name

    static mapping = {
        cache usage: 'read-only', include: 'non-lazy'
    }
}

@Entity
class GenCacheNonStrict {

    String name

    static mapping = {
        cache 'nonstrict-read-write'
    }
}

@Entity
class GenCacheOff {

    String name

    static mapping = {
        cache false
    }
}

@Entity
class GenCacheNone {

    String name
}

@Entity
class GenCacheRoot {

    String name

    static mapping = {
        cache true
    }
}

@Entity
class GenCacheLeaf extends GenCacheRoot {

    String extra
}

@Entity
class GenCacheBogus {

    String name

    static mapping = {
        cache true
    }
}
