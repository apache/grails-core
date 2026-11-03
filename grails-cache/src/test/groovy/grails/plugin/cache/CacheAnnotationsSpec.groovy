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

import java.lang.annotation.Annotation
import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.codehaus.groovy.transform.GroovyASTTransformationClass
import org.springframework.cache.CacheManager
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.plugin.cache.GrailsCacheManager

class CacheAnnotationsSpec extends Specification {

    @Unroll
    void '#type.simpleName targets methods and types and is retained at runtime'() {
        expect:
        type.isAnnotation()
        type.getAnnotation(Target).value() == [ElementType.METHOD, ElementType.TYPE] as ElementType[]
        type.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        type.isAnnotationPresent(Inherited) == inherited
        type.isAnnotationPresent(Documented) == inherited
        type.getAnnotation(GroovyASTTransformationClass)?.value() == transformation

        where:
        type           | inherited | transformation
        Cacheable      | true      | ['org.grails.plugin.cache.compiler.CacheableTransformation'] as String[]
        CachePut       | true      | ['org.grails.plugin.cache.compiler.CachePutTransformation'] as String[]
        CacheEvict     | true      | ['org.grails.plugin.cache.compiler.CacheEvictTransformation'] as String[]
        CacheOperation | false     | null
    }

    @Unroll
    void '#type.simpleName declares #members with the expected defaults'() {
        expect:
        type.declaredMethods*.name.sort() == members.sort()
        type.getDeclaredMethod('value').returnType == String[]
        type.getDeclaredMethod('value').defaultValue == null
        type.getDeclaredMethod('key').returnType == Class[]
        type.getDeclaredMethod('key').defaultValue == new Class[0]
        !members.contains('condition') || type.getDeclaredMethod('condition').defaultValue == new Class[0]
        !members.contains('allEntries') || type.getDeclaredMethod('allEntries').defaultValue == false

        where:
        type       | members
        Cacheable  | ['value', 'key', 'condition']
        CachePut   | ['value', 'key']
        CacheEvict | ['value', 'key', 'condition', 'allEntries']
    }

    void 'the annotations can be read back from a transformed class with the closure members consumed'() {
        given:
        Class annotated = new GroovyShell().evaluate('''
import grails.plugin.cache.*

class Annotated {
    @Cacheable(value = 'one', key = { name }, condition = { name != null })
    String cached(String name) { name }

    @CachePut(value = 'one', key = { name })
    String put(String name) { name }

    @CacheEvict(value = 'one', allEntries = true)
    void evict() { }

    @CacheOperation
    void op() { }
}
return Annotated
''')

        when:
        Cacheable cacheable = annotated.getMethod('cached', String).getAnnotation(Cacheable)
        CachePut put = annotated.getMethod('put', String).getAnnotation(CachePut)
        CacheEvict evict = annotated.getMethod('evict').getAnnotation(CacheEvict)
        Annotation op = annotated.getMethod('op').getAnnotation(CacheOperation)

        then:
        cacheable.value() == ['one'] as String[]
        cacheable.key().length == 0
        cacheable.condition().length == 0
        put.value() == ['one'] as String[]
        put.key().length == 0
        evict.value() == ['one'] as String[]
        evict.key().length == 0
        evict.condition().length == 0
        !evict.allEntries()
        op != null
    }

    void 'the cache manager contract extends the spring cache manager'() {
        expect:
        GrailsCacheManager.isInterface()
        CacheManager.isAssignableFrom(GrailsCacheManager)
        GrailsCacheManager.getMethod('cacheExists', String).returnType == boolean
        GrailsCacheManager.getMethod('destroyCache', String).returnType == boolean
        GrailsCache.isInterface()
        org.springframework.cache.Cache.isAssignableFrom(GrailsCache)
        GrailsCache.getMethod('getAllKeys').returnType == Collection
    }

}
