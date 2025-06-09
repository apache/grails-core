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

package com.example

import grails.plugins.redis.RedisService
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

@Integration
class MemoizeAnnotationSpec extends Specification {

    @Autowired RedisService redisService

    void setup() {
        redisService.flushDB()
    }

    void testMemoizeAnnotationExpire() {
        given:
        // set up test class
        def testClass = new GroovyClassLoader().parseClass('''
import grails.plugins.redis.*

class TestClass{
    RedisService redisService

	def key
	def expire

	@Memoize(key="#{key}", expire="#{expire}")
	def testAnnotatedMethod(){
		return "testValue"
	}
}
''')
        String testKey = "key123"
        String testExpire = "1000"

        when:
        def testInstance = testClass.getDeclaredConstructor().newInstance()

        // inject redis service
        testInstance.redisService = redisService
        testInstance.key = testKey
        testInstance.expire = testExpire

        then:
        redisService.get("$testKey") == null

        when:
        def output = testInstance.testAnnotatedMethod()

        then:
        output == 'testValue'
        redisService.get("$testKey") == 'testValue'

    }

}
