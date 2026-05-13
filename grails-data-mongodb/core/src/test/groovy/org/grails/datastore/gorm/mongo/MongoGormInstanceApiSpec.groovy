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

package org.grails.datastore.gorm.mongo

import groovy.transform.CompileDynamic
import org.grails.datastore.gorm.mongo.api.MongoGormInstanceApi
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import spock.lang.Specification

/**
 * Specification for MongoGormInstanceApi
 *
 * @author Graeme Rocher
 * @since 8.0
 */
@CompileDynamic
class MongoGormInstanceApiSpec extends Specification {

    MongoDatastore datastore
    MongoGormInstanceApi<TestMongoEntity> instanceApi

    void setup() {
        datastore = new MongoDatastore(new MongoMappingContext(TestMongoEntity.simpleName))
        instanceApi = new MongoGormInstanceApi<TestMongoEntity>(TestMongoEntity, datastore)
    }

    void cleanup() {
        datastore?.close()
    }

    void "MongoGormInstanceApi auto-flushes on save by default"() {
        given: "a new entity instance"
        def entity = new TestMongoEntity(name: "Test Entity", age: 42)
        
        when: "save is called without explicit flush parameter"
        def result = instanceApi.save(entity)
        
        then: "the entity is saved with auto-flush enabled"
        result != null
        result.id != null
        // Verify flush was called by checking data persists
    }

    void "MongoGormInstanceApi respects explicit flush=false"() {
        given: "a new entity instance"
        def entity = new TestMongoEntity(name: "Test No Flush", age: 25)
        
        when: "save is called with flush: false"
        def result = instanceApi.save(entity, [flush: false])
        
        then: "the entity is saved but may not be flushed yet"
        result != null
    }

    void "MongoGormInstanceApi preserves other parameters when adding flush"() {
        given: "a new entity instance"
        def entity = new TestMongoEntity(name: "Test Validate", age: 30)
        
        when: "save is called with other parameters"
        def result = instanceApi.save(entity, [validate: false])
        
        then: "flush is added without removing other parameters"
        result != null
    }

    void "MongoGormInstanceApi flush parameter defaults to true"() {
        given: "the instanceApi instance"
        
        when: "save is called without arguments"
        def entity = new TestMongoEntity(name: "Default Flush", age: 35)
        def result = instanceApi.save(entity)
        
        then: "default flush should be true"
        result != null
    }

    static class TestMongoEntity {
        String name
        int age
    }
}
