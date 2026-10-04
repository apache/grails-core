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
package org.grails.datastore.gorm.jdbc

import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.support.DefaultConversionService
import spock.lang.Specification
import spock.lang.Unroll

class RelaxedConversionServiceSpec extends Specification {

    static enum Color {
        RED, GREEN_BLUE
    }

    void "convert delegates to the provided root conversionService when it can handle the conversion"() {
        given:
        def service = new RelaxedConversionService(new DefaultConversionService())

        expect:
        service.convert('42', Integer) == 42
        service.canConvert(String, Integer)
    }

    void "canConvert(TypeDescriptor, TypeDescriptor) delegates to the provided root conversionService"() {
        given:
        def service = new RelaxedConversionService(new DefaultConversionService())

        expect:
        service.canConvert(TypeDescriptor.valueOf(String), TypeDescriptor.valueOf(Integer))
    }

    void "convert falls back to the additional converters for String to char[] which the root does not support"() {
        given:
        def service = new RelaxedConversionService(new DefaultConversionService())

        expect:
        service.convert('abcde', char[].class) == 'abcde'.toCharArray()
    }

    void "convert works with a null root conversionService using only the additional converters"() {
        given:
        def service = new RelaxedConversionService(null)

        expect:
        service.convert('abcde', char[].class) == 'abcde'.toCharArray()
        service.canConvert(String, char[].class)
    }

    static class Unconvertible {
        private Unconvertible() {}
    }

    void "canConvert returns false for an unsupported conversion"() {
        given:
        def service = new RelaxedConversionService(new DefaultConversionService())

        expect:
        !service.canConvert(String, Unconvertible)
    }

    @Unroll
    void "convert resolves '#source' to enum #expected ignoring case and separator style"() {
        given:
        def service = new RelaxedConversionService(new DefaultConversionService())

        expect:
        service.convert(source, Color) == expected

        where:
        source        | expected
        'RED'         | Color.RED
        'red'         | Color.RED
        'Red'         | Color.RED
        'GREEN_BLUE'  | Color.GREEN_BLUE
        'green-blue'  | Color.GREEN_BLUE
        'green_blue'  | Color.GREEN_BLUE
    }

    void "convert returns null for an empty string when the target is an enum"() {
        given:
        def service = new RelaxedConversionService(new DefaultConversionService())

        expect:
        service.convert('', Color) == null
    }

    void "convert throws a ConversionFailedException for an unrecognized enum constant"() {
        given:
        def service = new RelaxedConversionService(new DefaultConversionService())

        when:
        service.convert('not-a-color', Color)

        then:
        def ex = thrown(ConversionFailedException)
        ex.cause instanceof IllegalArgumentException
    }
}
