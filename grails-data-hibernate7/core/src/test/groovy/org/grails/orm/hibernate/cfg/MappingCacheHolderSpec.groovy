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
package org.grails.orm.hibernate.cfg

import spock.lang.Specification

class MappingCacheHolderSpec extends Specification {

    def "getMapping returns null for null class"() {
        given:
        def holder = new MappingCacheHolder()

        expect:
        holder.getMapping(null) == null
    }

    def "getMapping returns null for unknown class"() {
        given:
        def holder = new MappingCacheHolder()

        expect:
        holder.getMapping(String) == null
    }

    def "cacheMapping with class and Mapping stores and retrieves it"() {
        given:
        def holder = new MappingCacheHolder()
        def mapping = new Mapping()

        when:
        holder.cacheMapping(String, mapping)

        then:
        holder.getMapping(String).is(mapping)
    }

    def "cacheMapping with null class is ignored"() {
        given:
        def holder = new MappingCacheHolder()

        when:
        holder.cacheMapping((Class<?>) null, new Mapping())

        then:
        noExceptionThrown()
    }

    def "cacheMapping with null mapping is ignored"() {
        given:
        def holder = new MappingCacheHolder()

        when:
        holder.cacheMapping(String, (Mapping) null)

        then:
        holder.getMapping(String) == null
    }

    def "clear removes all cached mappings"() {
        given:
        def holder = new MappingCacheHolder()
        holder.cacheMapping(String, new Mapping())
        holder.cacheMapping(Integer, new Mapping())

        when:
        holder.clear()

        then:
        holder.getMapping(String) == null
        holder.getMapping(Integer) == null
    }

    def "clear(Class) removes only the specified class mapping"() {
        given:
        def holder = new MappingCacheHolder()
        def mappingA = new Mapping()
        def mappingB = new Mapping()
        holder.cacheMapping(String, mappingA)
        holder.cacheMapping(Integer, mappingB)

        when:
        holder.clear(String)

        then:
        holder.getMapping(String) == null
        holder.getMapping(Integer).is(mappingB)
    }
}
