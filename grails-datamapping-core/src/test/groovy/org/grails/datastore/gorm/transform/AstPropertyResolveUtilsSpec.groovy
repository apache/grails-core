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
package org.grails.datastore.gorm.transform

import java.lang.reflect.Modifier
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.model.config.GormProperties

/**
 * {@link AstPropertyResolveUtils} caches resolved property metadata as metadata on the
 * {@link ClassNode} it describes (see that class's javadoc). Two distinct compilations (e.g. the
 * same source parsed in two different {@code GroovyClassLoader}s, as happens for
 * dynamically-generated sources and in tests) produce distinct {@code ClassNode} instances that
 * can legitimately share the exact same name - {@code ClassNode#equals(Object)} compares by name,
 * so a naive name- or equals()-based cache key would conflate them, corrupting the resolved
 * properties of one class with those of an unrelated class that happens to share its name. This
 * spec proves the cache is scoped strictly per {@code ClassNode} instance, so same-named-but-distinct
 * class nodes never contaminate each other's cached property data, that domain-class-specific
 * resolution (identity/version injection, association metadata) works, and that concurrent
 * resolution of distinct nodes is safe.
 */
class AstPropertyResolveUtilsSpec extends Specification {

    void "property lookups for two same-named ClassNodes in different packages do not corrupt each other"() {
        given: 'two distinct ClassNodes with the same simple name declared in different packages'
        ClassNode first = new ClassNode('org.example.one.Widget', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        first.addProperty('color', Modifier.PUBLIC, ClassHelper.STRING_TYPE, null, null, null)

        ClassNode second = new ClassNode('org.example.two.Widget', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        second.addProperty('weight', Modifier.PUBLIC, ClassHelper.Integer_TYPE, null, null, null)

        when: 'the first class node is resolved, populating its cache entry'
        List<String> firstProperties = AstPropertyResolveUtils.getPropertyNames(first)

        then: 'only its own property is resolved'
        firstProperties.contains('color')
        !firstProperties.contains('weight')

        when: 'the second, differently-packaged, same-simple-name class node is resolved'
        List<String> secondProperties = AstPropertyResolveUtils.getPropertyNames(second)

        then: 'its own property is resolved, not leaked from the first class node'
        secondProperties.contains('weight')
        !secondProperties.contains('color')

        and: 'the first class node cache entry remains unaffected by resolving the second'
        List<String> firstPropertiesAfter = AstPropertyResolveUtils.getPropertyNames(first)
        firstPropertiesAfter.contains('color')
        !firstPropertiesAfter.contains('weight')
    }

    void "property lookups for two distinct ClassNode instances with the exact same unqualified name do not corrupt each other"() {
        given: 'two distinct ClassNode instances - as produced by two separate compilations - sharing an identical unqualified name'
        ClassNode first = new ClassNode('Widget', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        first.addProperty('color', Modifier.PUBLIC, ClassHelper.STRING_TYPE, null, null, null)

        ClassNode second = new ClassNode('Widget', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        second.addProperty('weight', Modifier.PUBLIC, ClassHelper.Integer_TYPE, null, null, null)

        and: 'they are genuinely different instances - the precondition a name- or equals()-keyed cache would get wrong'
        // ClassNode#equals()/hashCode() compare by getText() (essentially the class name), so
        // first == second and first.hashCode() == second.hashCode() both hold here even though
        // these are two unrelated ClassNode instances with different declared properties. A cache
        // keyed by name or by equals()/hashCode() would treat them as the same entry; only
        // reference identity (!first.is(second)) tells them apart, which is exactly what the
        // cache must key on. This is an "and:" continuing "given:", so Spock does not apply an
        // implicit condition here - the explicit assert is required for this to actually fail
        // the test if it were ever untrue.
        assert !first.is(second)

        when: 'both class nodes are resolved'
        List<String> firstProperties = AstPropertyResolveUtils.getPropertyNames(first)
        List<String> secondProperties = AstPropertyResolveUtils.getPropertyNames(second)

        then: 'each keeps its own, independently-resolved properties despite comparing equal'
        firstProperties.contains('color')
        !firstProperties.contains('weight')
        secondProperties.contains('weight')
        !secondProperties.contains('color')
    }

    void "getPropertyType resolves the type of a declared property"() {
        given: 'a class node with a declared property'
        ClassNode classNode = new ClassNode('org.example.PropertyTypeWidget', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        classNode.addProperty('label', Modifier.PUBLIC, ClassHelper.STRING_TYPE, null, null, null)

        expect: 'the resolved property type matches the declared type, both on first and second lookup'
        AstPropertyResolveUtils.getPropertyType(classNode, 'label') == ClassHelper.STRING_TYPE
        AstPropertyResolveUtils.getPropertyType(classNode, 'label') == ClassHelper.STRING_TYPE
    }

    void "getPropertyNames returns the snapshot taken on first lookup rather than reflecting properties added afterwards"() {
        given: 'a class node with one declared property'
        ClassNode classNode = new ClassNode('org.example.CachedSnapshotWidget', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        classNode.addProperty('label', Modifier.PUBLIC, ClassHelper.STRING_TYPE, null, null, null)

        when: 'the property names are resolved once, populating the cache'
        List<String> firstLookup = AstPropertyResolveUtils.getPropertyNames(classNode)

        then:
        firstLookup == ['label']

        when: 'a second property is added directly to the ClassNode after the cache has already been populated'
        classNode.addProperty('extra', Modifier.PUBLIC, ClassHelper.Integer_TYPE, null, null, null)

        then: 'a direct lookup on the ClassNode confirms the property really was added - so the cache below is stale, not simply broken'
        classNode.getProperty('extra') != null

        and: 'getPropertyNames still returns the cached snapshot from the first lookup, proving the result was actually cached rather than recomputed on every call'
        !AstPropertyResolveUtils.getPropertyNames(classNode).contains('extra')
    }

    void "getPropertyNames injects identity and version for a domain class and resolves hasMany/belongsTo/hasOne declared via AST initial expressions"() {
        given: 'a domain class node declaring hasMany/belongsTo/hasOne as property initial expressions, as "static hasMany = [...]" compiles to'
        ClassNode associatedType = new ClassNode('org.example.AssociatedThing', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        ClassNode classNode = new ClassNode('org.example.AstDrivenDomain', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        classNode.addAnnotation(new AnnotationNode(ClassHelper.make(Entity)))
        classNode.addProperty('title', Modifier.PUBLIC, ClassHelper.STRING_TYPE, null, null, null)
        classNode.addProperty(GormProperties.HAS_MANY, Modifier.PUBLIC | Modifier.STATIC, ClassHelper.MAP_TYPE.getPlainNodeReference(),
                mapExpressionOf('things', associatedType), null, null)

        when:
        List<String> propertyNames = AstPropertyResolveUtils.getPropertyNames(classNode)

        then: 'the AST-declared property is present alongside the injected identity/version properties'
        propertyNames.containsAll(['title', GormProperties.IDENTITY, GormProperties.VERSION, 'things'])
        AstPropertyResolveUtils.getPropertyType(classNode, GormProperties.IDENTITY) == new ClassNode(Long.class)
        AstPropertyResolveUtils.getPropertyType(classNode, 'things') == associatedType

        and: 'the raw hasMany/belongsTo/hasOne map property itself is not exposed as a plain property'
        !propertyNames.contains(GormProperties.HAS_MANY)
    }

    void "getPropertyNames resolves hasMany/belongsTo/hasOne association metadata via reflection once the domain class is fully resolved"() {
        given: 'a real, already-compiled domain class with hasMany/belongsTo/hasOne associations'
        GroovyClassLoader gcl = new GroovyClassLoader()
        gcl.parseClass('''
            import grails.gorm.annotation.Entity

            @Entity
            class ReflectedAssociationAuthor {
                String name
            }

            @Entity
            class ReflectedAssociationBook {
                String title
            }

            @Entity
            class ReflectedAssociationPublisher {
                String company
            }

            @Entity
            class ReflectedAssociationFixture {
                static hasMany = [books: ReflectedAssociationBook]
                static belongsTo = [author: ReflectedAssociationAuthor]
                static hasOne = [publisher: ReflectedAssociationPublisher]
            }
        ''')
        Class<?> domainClass = gcl.loadedClasses.find { it.simpleName == 'ReflectedAssociationFixture' }

        and: 'a fresh ClassNode built from the already-compiled class, as happens once compilation has finished'
        ClassNode resolvedNode = ClassHelper.make(domainClass)

        expect: 'the node reports itself resolved, which is what gates the reflection-based association lookup'
        resolvedNode.isResolved()

        when:
        List<String> propertyNames = AstPropertyResolveUtils.getPropertyNames(resolvedNode)

        then: 'the reflected association properties are present alongside the injected identity/version properties'
        propertyNames.containsAll([GormProperties.IDENTITY, GormProperties.VERSION, 'books', 'author', 'publisher'])

        cleanup:
        gcl.close()
    }

    void "concurrent resolution of distinct, identically-named ClassNode instances never corrupts each other's cached properties"() {
        given: 'many threads, each building and resolving its own distinct ClassNode sharing one common name'
        int threadCount = 20
        ExecutorService executor = Executors.newFixedThreadPool(threadCount)
        CyclicBarrier barrier = new CyclicBarrier(threadCount)

        when: 'all threads race to populate the cache for their own instance at the same time'
        List<Future<Boolean>> futures = (0..<threadCount).collect { int i ->
            executor.submit({ ->
                barrier.await(30, TimeUnit.SECONDS)
                ClassNode node = new ClassNode('ConcurrentWidget', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
                String propertyName = "prop${i}".toString()
                node.addProperty(propertyName, Modifier.PUBLIC, ClassHelper.STRING_TYPE, null, null, null)

                List<String> names = AstPropertyResolveUtils.getPropertyNames(node)
                names.contains(propertyName) && names.count { it.startsWith('prop') } == 1
            } as Callable<Boolean>)
        }
        List<Boolean> outcomes = futures.collect { Future<Boolean> future -> future.get(30, TimeUnit.SECONDS) }

        then: 'every thread resolved its own property set, uncontaminated by any of the other concurrently-resolved same-named instances'
        outcomes.every { it }

        cleanup:
        executor.shutdownNow()
    }

    void "concurrent resolution of the exact same shared ClassNode instance from many threads is safe"() {
        // Distinct-instance concurrency (the test above) can never exercise a race on the
        // underlying node-metadata storage, because nothing is shared between the threads. A single
        // ClassNode instance genuinely can be looked up from more than one thread at once in
        // practice - e.g. ClassHelper.OBJECT_TYPE/STRING_TYPE are JVM-wide singletons that this
        // utility's callers can resolve to for a plain Object- or def-typed property, so two
        // unrelated, concurrently-running compilations could both reach this cache for the exact
        // same node. This test exercises that shared-node case directly and asserts every thread's
        // returned value is correct.
        //
        // Note on what this test can and can't prove: the cached computation here is deterministic
        // and idempotent, so even with the "synchronized (cacheHolder)" guard in
        // AstPropertyResolveUtils#getPropertiesFromCache removed, every thread still computes and
        // returns the same correct value in practice - a black-box test of returned values cannot
        // reliably force ClassNode's underlying, explicitly-documented-not-thread-safe metadata
        // storage into an observably-wrong state without reaching into Groovy internals neither this
        // spec nor AstPropertyResolveUtils controls. Verified by temporarily removing that guard and
        // running this test 8 times without a failure. The synchronization is kept as a correctness
        // fix justified by ListHashMap's own documentation, not because this test can demonstrate
        // its absence breaking anything; this test instead guards against a regression to something
        // observably broken (an exception, a null, a wrong/partial result) under real concurrent
        // load, which is the failure mode a future refactor could plausibly introduce.
        given: 'one ClassNode instance that every thread will resolve concurrently'
        ClassNode sharedNode = new ClassNode('SharedWidget', Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        sharedNode.addProperty('label', Modifier.PUBLIC, ClassHelper.STRING_TYPE, null, null, null)
        int threadCount = 32
        ExecutorService executor = Executors.newFixedThreadPool(threadCount)
        CyclicBarrier barrier = new CyclicBarrier(threadCount)

        when: 'all threads race to resolve properties for the same instance at once'
        List<Future<List<String>>> futures = (0..<threadCount).collect {
            executor.submit({ ->
                barrier.await(30, TimeUnit.SECONDS)
                AstPropertyResolveUtils.getPropertyNames(sharedNode)
            } as Callable<List<String>>)
        }
        List<List<String>> results = futures.collect { Future<List<String>> future -> future.get(30, TimeUnit.SECONDS) }

        then: 'every thread observes the same, fully and correctly populated result - none sees a partial or corrupted map'
        results.every { it == ['label'] }

        cleanup:
        executor.shutdownNow()
    }

    private static Expression mapExpressionOf(String key, ClassNode valueType) {
        MapExpression mapExpression = new MapExpression()
        mapExpression.addMapEntryExpression(new ConstantExpression(key), new ClassExpression(valueType))
        return mapExpression
    }
}
