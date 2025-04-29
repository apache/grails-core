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
package org.grails.datastore.gorm.timestamp

import java.sql.Timestamp
import org.springframework.util.ClassUtils
import spock.lang.Specification

class DefaultTimestampProviderSpec extends Specification {
    DefaultTimestampProvider timestampProvider = new DefaultTimestampProvider()
    
    def supportedClasses = [Date, Timestamp, Long.TYPE, Long]
    
    def "timestamp provider should support java.util.Date, java.sql.Timestamp, long and Long"() {
        expect:
        supportedClasses.each {clazz ->
            assert timestampProvider.supportsCreating(clazz) == true
        }
    }
    
    def "timestamp provider should create java.util.Date, java.sql.Timestamp, long and Long"() {
        expect:
        supportedClasses.each {clazz ->
            println "type $clazz"
            def timestamp = timestampProvider.createTimestamp(clazz)
            println "timestamp $timestamp"
            assert timestamp.getClass() == ClassUtils.resolvePrimitiveIfNecessary(clazz)
        }
    }
    
    def "timestamp provider should create java.util.Date if given type is Object"() {
        when:
        def timestamp = timestampProvider.createTimestamp(Object)
        then:
        timestamp.getClass() == Date
    }

    //To support JSR310 date classes
    //LocalDateTime, LocalDate, LocalTime, OffsetDateTime, OffsetTime, ZonedDateTime all have a static now() method
    def "timestamp provider should instantiate class with static now method"() {
        when:
        def timestamp = timestampProvider.createTimestamp(Foo)
        then:
        timestamp.getClass() == Foo
    }

    static class Foo {
        static Foo now() {
            return new Foo()
        }
    }

}
