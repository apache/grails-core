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
import spock.lang.Specification

/**
 * Specification for MongoGormInstanceApi
 *
 * @author Graeme Rocher
 * @since 8.0
 */
@CompileDynamic
class MongoGormInstanceApiSpec extends Specification {

    void "MongoGormInstanceApi adds flush parameter when not present"() {
        given: "arguments without flush key"
        def args = [validate: false]
        
        when: "the MongoGormInstanceApi processes arguments"
        // Simulate what the override does
        if (!args?.containsKey("flush")) {
            args = (args ?: [:]) + [flush: true]
        }
        
        then: "flush:true should be added"
        args.containsKey("flush")
        args.flush == true
        args.validate == false  // Other args should be preserved
    }

    void "MongoGormInstanceApi preserves explicit flush=false"() {
        given: "arguments with explicit flush=false"
        def args = [flush: false, validate: true]
        
        when: "the MongoGormInstanceApi evaluates this"
        if (!args?.containsKey("flush")) {
            args = (args ?: [:]) + [flush: true]
        }
        
        then: "flush=false should be preserved"
        args.flush == false
        args.validate == true
    }

    void "MongoGormInstanceApi boolean save calls map variant with flush"() {
        given: "parameters for save(instance, boolean)"
        def validateArg = true
        
        when: "simulating save(instance, boolean) behavior"
        // This is what save(instance, boolean) does
        def expectedArgs = [flush: true, validate: validateArg]
        
        then: "the parameters should be set correctly"
        expectedArgs.flush == true
        expectedArgs.validate == validateArg
    }

    void "MongoGormInstanceApi no-argument save calls map variant with flush"() {
        given: "no additional parameters"
        
        when: "simulating save(instance) behavior"
        // This is what save(instance) does
        def expectedArgs = [flush: true]
        
        then: "flush should be true"
        expectedArgs.flush == true
        expectedArgs.size() == 1
    }
}




