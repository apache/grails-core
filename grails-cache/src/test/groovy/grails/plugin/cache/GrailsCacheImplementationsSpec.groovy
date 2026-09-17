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

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import org.springframework.cache.Cache
import org.springframework.cache.support.SimpleValueWrapper
import spock.lang.Specification

class GrailsCacheImplementationsSpec extends Specification {

    void 'a value wrapper exposes the value and the native wrapper'() {
        given:
        Object nativeWrapper = new Object()

        when:
        GrailsValueWrapper wrapper = new GrailsValueWrapper('v', nativeWrapper)
        GrailsValueWrapper nullWrapper = new GrailsValueWrapper(null, null)

        then:
        wrapper instanceof SimpleValueWrapper
        wrapper.get() == 'v'
        wrapper.nativeWrapper.is(nativeWrapper)
        nullWrapper.get() == null
        nullWrapper.nativeWrapper == null
    }

    void 'the linked map cache stores null values when allowed'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10)

        when:
        cache.put('nullKey', null)
        cache.put('key', 'value')

        then:
        cache.size == 2
        cache.get('missing') == null
        cache.get('nullKey') instanceof GrailsValueWrapper
        cache.get('nullKey').get() == null
        cache.get('nullKey').nativeWrapper == null
        cache.get('key').get() == 'value'
        cache.getAllKeys().toList() == ['nullKey', 'key']
        cache.getAllKeys().is(cache.nativeCache.keySet())
        cache.nativeCache.get('nullKey') != null
        cache.nativeCache.get('nullKey') instanceof Serializable
        cache.nativeCache.get('nullKey').is(new GrailsConcurrentLinkedMapCache('other', 1).with { it.put('n', null); it.nativeCache.get('n') })
        cache.get('missing', String) == null
        cache.get('key', String) == 'value'
        cache.get('key', (Class) null) == 'value'
        cache.get('key', Object) == 'value'
    }

    void 'the linked map cache rejects a typed get of the wrong type'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10)
        cache.put('key', 'value')

        when:
        cache.get('key', Integer)

        then:
        IllegalStateException e = thrown()
        e.message == 'Cached value is not of required type [java.lang.Integer]: value'

        when:
        cache.get('key', { -> 'loaded' } as Callable)

        then:
        thrown(UnsupportedOperationException)
    }

    void 'the linked map cache does not translate nulls when null values are disallowed'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10, false)

        when:
        cache.put('key', null)

        then:
        NullPointerException e = thrown()
        cache.size == 0
        !cache.allowNullValues
        cache.capacity == 10
        cache.name == 'c'
        cache.nativeCache instanceof ConcurrentLinkedHashMap
    }

    void 'putIfAbsent returns the existing wrapped value or null'() {
        given:
        GrailsConcurrentLinkedMapCache cache = new GrailsConcurrentLinkedMapCache('c', 10)

        when:
        Cache.ValueWrapper first = cache.putIfAbsent('key', 'one')
        Cache.ValueWrapper second = cache.putIfAbsent('key', 'two')
        cache.put('nullKey', null)
        Cache.ValueWrapper third = cache.putIfAbsent('nullKey', 'three')

        then:
        first == null
        second instanceof SimpleValueWrapper
        !(second instanceof GrailsValueWrapper)
        second.get() == 'one'
        cache.get('key').get() == 'one'
        third instanceof SimpleValueWrapper
        third.get() == null
        cache.hottestKeys.toList() == ['key', 'nullKey']
    }

    void 'the concurrent map cache wraps values in grails value wrappers'() {
        given:
        ConcurrentMap<Object, Object> store = new ConcurrentHashMap<Object, Object>()
        GrailsConcurrentMapCache byName = new GrailsConcurrentMapCache('a')
        GrailsConcurrentMapCache noNulls = new GrailsConcurrentMapCache('b', false)
        GrailsConcurrentMapCache withStore = new GrailsConcurrentMapCache('c', store, true)

        when:
        byName.put('key', 'value')
        byName.put('nullKey', null)
        withStore.put('key', 'value')

        then:
        byName instanceof GrailsCache
        byName.name == 'a'
        byName.allowNullValues
        !noNulls.allowNullValues
        withStore.nativeCache.is(store)
        store.containsKey('key')
        byName.get('missing') == null
        byName.get('key') instanceof GrailsValueWrapper
        byName.get('key').get() == 'value'
        byName.get('key').nativeWrapper == null
        byName.get('nullKey') instanceof GrailsValueWrapper
        byName.get('nullKey').get() == null
        byName.getAllKeys().sort() == ['key', 'nullKey']
        byName.getAllKeys().is(byName.nativeCache.keySet())
        withStore.getAllKeys().toList() == ['key']
        noNulls.getAllKeys().empty

        when:
        noNulls.put('k', null)

        then:
        thrown(IllegalArgumentException)
    }

}
