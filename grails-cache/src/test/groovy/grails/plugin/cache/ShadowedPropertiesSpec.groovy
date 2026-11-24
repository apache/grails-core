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
package grails.plugin.cache

import java.util.function.Supplier

import ch.qos.logback.classic.Level
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.cache.support.NoOpCacheManager
import org.springframework.context.annotation.AnnotationConfigUtils
import org.springframework.context.support.GenericApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import org.apache.grails.core.testing.support.LogCapture
import org.grails.plugin.cache.MissingCacheReporter

/**
 * A parameter of a cached method named like the cache manager or the key generator a cache-aware class has
 * hides that name in the method; the code the cache annotations generate reads both as properties of
 * {@code this}, so it caches with the class's own: a field the class declares, otherwise what the trait holds.
 */
class ShadowedPropertiesSpec extends Specification {

    /** What a class declares, the declaration, whether a superclass declares it, and whether it is the cache manager to use. */
    private static final List<List<Object>> DECLARED = [
        ['a private cache manager field', 'private CacheManager grailsCacheManager = new OwnCacheManager()', false, true],
        ['a protected cache manager field', 'protected CacheManager grailsCacheManager = new OwnCacheManager()', false, true],
        ['a cache manager property', 'CacheManager grailsCacheManager = new OwnCacheManager()', false, true],
        ['a private key generator field', 'private GrailsCacheKeyGenerator customCacheKeyGenerator = new CountingKeyGenerator()', false, false],
        ['a key generator property', 'GrailsCacheKeyGenerator customCacheKeyGenerator = new CountingKeyGenerator()', false, false],
        // as in 8.0.0, a superclass's field is not the class's: the trait's getter, which the class gets, is read
        ['a superclass\'s private cache manager field', 'private CacheManager grailsCacheManager = new OwnCacheManager()', true, false],
    ]

    @Unroll
    void 'a cached method with parameters named like the cache manager and the key generator caches with the class\'s own, compiled #mode'() {
        given: 'the class created as a bean beside its cache manager'
        CacheManager cacheManager = new ConcurrentMapCacheManager('configured')
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.registerBean('grailsCacheManager', CacheManager, { -> cacheManager } as Supplier<CacheManager>)
        context.registerBean('service', (Class<Object>) compile(mode))
        context.refresh()
        def service = context.getBean('service')

        and: 'another cache manager, and no key generator, passed to the methods'
        CacheManager passed = new ConcurrentMapCacheManager('configured')

        when: 'the cached method is called twice'
        List<Object> results = [service.cached(1, passed, null), service.cached(1, passed, null)]

        then: 'it ran once, and cached in the class\'s cache manager, not the one passed'
        results == [2, 2]
        service.calls == 1
        nativeCache(cacheManager).size() == 1
        nativeCache(passed).isEmpty()

        when: 'a put and an eviction of every entry, with the same parameters'
        service.put(5, passed, null)
        int afterPut = nativeCache(cacheManager).size()
        service.evictAll(passed, null)

        then: 'both act on the class\'s cache manager'
        afterPut == 2
        nativeCache(cacheManager).isEmpty()
        nativeCache(passed).isEmpty()

        cleanup:
        context?.close()

        where:
        mode << ['statically', 'dynamically']
    }

    @Unroll
    void 'a cache the class\'s cache manager does not have is reported for that cache manager, compiled #mode'() {
        given: 'the class created as a bean beside its cache manager, which has no cache named missing'
        CacheManager cacheManager = new ConcurrentMapCacheManager('configured')
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.registerBean('grailsCacheManager', CacheManager, { -> cacheManager } as Supplier<CacheManager>)
        context.registerBean('service', (Class<Object>) compile(mode))
        context.refresh()
        def service = context.getBean('service')
        LogCapture log = new LogCapture(MissingCacheReporter, Level.WARN)

        when: 'the method is called with no cache manager, then with another one'
        List<Object> results = [service.uncached(1, null, null), service.uncached(1, new NoOpCacheManager(), null)]

        then: 'it ran uncached both times, and the class\'s cache manager was reported once'
        results == [2, 2]
        service.calls == 2
        log.events*.formattedMessage == ["ShadowingService does not cache with missing: its cache manager, ${ConcurrentMapCacheManager.name}, ".toString() +
                'has no cache of that name, so the methods run uncached. Configure the cache in that cache manager.']

        cleanup:
        log?.close()
        context?.close()

        where:
        mode << ['statically', 'dynamically']
    }

    @Unroll
    void 'a class that declares #declared caches, puts and evicts with it, with and without parameters named like it, compiled #mode'() {
        given: 'the class created as a bean beside a cache manager named grailsCacheManager'
        CacheManager bean = new ConcurrentMapCacheManager('configured')
        GenericApplicationContext context = new GenericApplicationContext()
        AnnotationConfigUtils.registerAnnotationConfigProcessors(context)
        context.registerBean('grailsCacheManager', CacheManager, { -> bean } as Supplier<CacheManager>)
        context.registerBean('service', (Class<Object>) compileDeclaring(mode, members, inBase))
        context.refresh()
        def service = context.getBean('service')
        CacheManager own = service.respondsTo('ownCacheManager') ? (CacheManager) service.ownCacheManager() : null
        CacheManager expected = expectsOwnCacheManager ? own : bean

        and: 'another cache manager and another key generator, passed to the method with parameters'
        CacheManager passed = new ConcurrentMapCacheManager('configured')
        def passedKeyGenerator = service.newKeyGenerator()
        LogCapture log = new LogCapture(MissingCacheReporter, Level.WARN)

        when: 'a method without the parameters and one with them are called'
        List<Object> results = [service.cached(1), service.cachedWithParameters(2, passed, passedKeyGenerator)]

        then: 'both cached in the cache manager the class means, with the key generator it means'
        results == [2, 4]
        nativeCache(expected).size() == 2
        [bean, passed, own].findAll { it != null && !it.is(expected) }.every { nativeCache(it).isEmpty() }
        passedKeyGenerator.count == 0
        !service.respondsTo('ownKeyGenerator') || service.ownKeyGenerator().count == 2

        when: 'a put and an eviction of every entry, with the parameters'
        service.put(5, passed, passedKeyGenerator)
        int afterPut = nativeCache(expected).size()
        service.evictAll(passed, passedKeyGenerator)

        then: 'both act on the cache manager the class means, the put with the key generator it means'
        afterPut == 3
        [bean, passed, own].findAll { it != null }.every { nativeCache(it).isEmpty() }
        passedKeyGenerator.count == 0
        !service.respondsTo('ownKeyGenerator') || service.ownKeyGenerator().count == 3

        when: 'a method whose cache is missing is called with the parameters'
        service.uncached(3, passed, passedKeyGenerator)

        then: 'the cache manager the class means is reported'
        log.events*.formattedMessage.collect { it.find(/cache manager, (\S+),/) { all, name -> name } } == [expected.getClass().name]

        cleanup:
        log?.close()
        context?.close()

        where:
        [declared, members, inBase, expectsOwnCacheManager, mode] << DECLARED.collectMany { row ->
            ['statically', 'dynamically'].collect { compiled -> row + compiled }
        }
    }

    private static Map<?, ?> nativeCache(CacheManager cacheManager) {
        (Map<?, ?>) cacheManager.getCache('configured').nativeCache
    }

    private static Class<?> compile(String mode) {
        new GroovyShell().evaluate("""
import grails.plugin.cache.*
import groovy.transform.CompileStatic
import org.springframework.cache.CacheManager

${mode == 'statically' ? '@CompileStatic' : ''}
class ShadowingService {
    int calls

    @Cacheable('configured')
    int cached(int x, CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        calls++
        x * 2
    }

    @CachePut('configured')
    int put(int x, CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        calls++
        x * 2
    }

    @Cacheable('missing')
    int uncached(int x, CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        calls++
        x * 2
    }

    @CacheEvict(value = 'configured', allEntries = true)
    void evictAll(CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        calls++
    }
}
return ShadowingService
""") as Class<?>
    }

    /**
     * A cache-aware class that declares the members given, or whose superclass does, with an accessor for what
     * it declares, and methods with and without parameters named like the cache manager and the key generator.
     */
    private static Class<?> compileDeclaring(String mode, String members, boolean inBase) {
        String accessor = members.contains('grailsCacheManager') ?
                'CacheManager ownCacheManager() { grailsCacheManager }' :
                'CountingKeyGenerator ownKeyGenerator() { (CountingKeyGenerator) customCacheKeyGenerator }'
        String declaration = inBase ?
                """
class DeclaringBase {
    $members
    $accessor
}

${mode == 'statically' ? '@CompileStatic' : ''}
class DeclaringService extends DeclaringBase {
""" :
                """
${mode == 'statically' ? '@CompileStatic' : ''}
class DeclaringService {
    $members
    $accessor
"""
        new GroovyShell().evaluate("""
import grails.plugin.cache.*
import groovy.transform.CompileStatic
import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager

class OwnCacheManager extends ConcurrentMapCacheManager {
    OwnCacheManager() {
        super('configured')
    }
}

class CountingKeyGenerator implements GrailsCacheKeyGenerator {
    int count

    Serializable generate(String className, String methodName, int objHashCode, Closure keyGenerator) {
        count++
        methodName
    }

    Serializable generate(String className, String methodName, int objHashCode, Map methodParams) {
        count++
        methodName + methodParams
    }
}
$declaration
    CountingKeyGenerator newKeyGenerator() {
        new CountingKeyGenerator()
    }

    @Cacheable('configured')
    int cached(int x) {
        x * 2
    }

    @Cacheable('configured')
    int cachedWithParameters(int x, CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        x * 2
    }

    @CachePut('configured')
    int put(int x, CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        x * 2
    }

    @CacheEvict(value = 'configured', allEntries = true)
    void evictAll(CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
    }

    @Cacheable('missing')
    int uncached(int x, CacheManager grailsCacheManager, GrailsCacheKeyGenerator customCacheKeyGenerator) {
        x * 2
    }
}
return DeclaringService
""") as Class<?>
    }
}
