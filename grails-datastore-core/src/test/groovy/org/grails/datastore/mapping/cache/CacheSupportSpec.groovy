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
package org.grails.datastore.mapping.cache

import spock.lang.Specification

import org.grails.datastore.mapping.cache.exception.CacheException
import org.grails.datastore.mapping.cache.impl.HashMapTPCacheAdapter
import org.grails.datastore.mapping.cache.impl.TPCacheAdapterRepositoryImpl
import org.grails.datastore.mapping.model.PersistentEntity

class CacheSupportSpec extends Specification {

    void "the hash map adapter stores and returns entries by key"() {
        given:
        HashMapTPCacheAdapter<String> adapter = new HashMapTPCacheAdapter<>()

        expect:
        adapter.getCachedEntry(1L) == null

        when:
        adapter.cacheEntry(1L, 'one')
        adapter.cacheEntry(1L, 'uno')

        then:
        adapter.getCachedEntry(1L) == 'uno'
        adapter.getCachedEntry(2L) == null
    }

    void "the repository resolves adapters by entity, class or class name"() {
        given:
        TPCacheAdapterRepositoryImpl<String> repository = new TPCacheAdapterRepositoryImpl<>()
        TPCacheAdapter<String> byEntity = Stub(TPCacheAdapter)
        TPCacheAdapter<String> byClass = Stub(TPCacheAdapter)
        TPCacheAdapter<String> byName = Stub(TPCacheAdapter)
        PersistentEntity stringEntity = Stub(PersistentEntity) { getJavaClass() >> String }
        PersistentEntity integerEntity = Stub(PersistentEntity) { getJavaClass() >> Integer }
        PersistentEntity longEntity = Stub(PersistentEntity) { getJavaClass() >> Long }
        PersistentEntity unknownEntity = Stub(PersistentEntity) { getJavaClass() >> Short }

        expect:
        repository.getTPCacheAdapter(null) == null
        repository.getTPCacheAdapter(unknownEntity) == null

        when:
        repository.setTPCacheAdapter(stringEntity, byEntity)
        repository.setTPCacheAdapter(Integer, byClass)
        repository.setTPCacheAdapter(Long.name, byName)

        then:
        repository.getTPCacheAdapter(stringEntity).is(byEntity)
        repository.getTPCacheAdapter(integerEntity).is(byClass)
        repository.getTPCacheAdapter(longEntity).is(byName)
        repository.getTPCacheAdapter(unknownEntity) == null

        when:
        repository.setTPCacheAdapter(stringEntity, byName)

        then:
        repository.getTPCacheAdapter(stringEntity).is(byName)
    }

    void "cache exceptions carry message and cause"() {
        given:
        Throwable cause = new IllegalStateException('cause')

        expect:
        new CacheException().message == null
        new CacheException('m').message == 'm'
        new CacheException('m', cause).cause.is(cause)
        new CacheException(cause).cause.is(cause)
        new CacheException(cause).message == cause.toString()
    }

}
