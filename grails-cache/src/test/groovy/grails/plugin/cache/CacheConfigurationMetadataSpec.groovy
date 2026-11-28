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

import groovy.json.JsonSlurper

import spock.lang.Specification

/**
 * The configuration metadata the plugin publishes is what an IDE offers and what the Application
 * Properties reference lists, so it names the settings the plugin reads, and no other.
 */
class CacheConfigurationMetadataSpec extends Specification {

    void 'the metadata lists the settings the plugin reads, and none that only a plugin of another cache provider read'() {
        when:
        List<String> names = settings()*.get('name') as List<String>

        then: 'enabled gates the beans of the plugin, cacheManager chooses its cache manager, and clearAtStartup and caches are bound to CachePluginConfiguration'
        names.sort() == ['grails.cache.cacheManager', 'grails.cache.caches', 'grails.cache.clearAtStartup', 'grails.cache.enabled']

        and: 'the settings of the plugin for Ehcache, which Grails does not ship, are not among them'
        !names.any { String name -> name.startsWith('grails.cache.ehcache') }
    }

    // the properties of the one metadata on the class path whose groups declare the prefix of the plugin
    private static List<Map> settings() {
        List<Map> metadata = CacheConfigurationMetadataSpec.classLoader
                .getResources('META-INF/spring-configuration-metadata.json')
                .collect { URL resource -> new JsonSlurper().parse(resource) as Map }
                .findAll { Map candidate -> (candidate.get('groups') as List<Map>)?.any { Map group -> group.get('name') == 'grails.cache' } }
        assert metadata.size() == 1
        return metadata.first().get('properties') as List<Map>
    }
}
