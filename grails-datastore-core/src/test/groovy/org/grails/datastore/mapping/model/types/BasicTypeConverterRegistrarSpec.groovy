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
package org.grails.datastore.mapping.model.types

import org.springframework.core.convert.ConversionFailedException
import org.springframework.core.convert.support.GenericConversionService
import spock.lang.Specification
import spock.lang.Unroll

class BasicTypeConverterRegistrarSpec extends Specification {

    GenericConversionService conversionService = new GenericConversionService()

    void setup() {
        new BasicTypeConverterRegistrar().register(conversionService)
    }

    @Unroll
    void "converts #source (#source.class.simpleName) to #target.simpleName"() {
        expect:
        conversionService.convert(source, target) == expected

        where:
        source                    | target   || expected
        String                    | String   || 'java.lang.String'
        new Date(1000L)           | String   || '1000'
        1                         | Long     || 1L
        7L                        | Integer  || 7
        2                         | Double   || 2.0d
        '1000'                    | Date     || new Date(1000L)
        new StringBuilder('1.5')  | Double   || 1.5d
        '42'                      | Integer  || 42
        new StringBuilder('42')   | Long     || 42L
        new Object() { String toString() { 'obj' } } | String || 'obj'
    }

    void "dates and calendars convert in both directions"() {
        given:
        Date date = new Date(5000L)

        when:
        Calendar calendar = conversionService.convert(date, Calendar)

        then:
        calendar.time == date
        conversionService.convert(calendar, String) == '5000'
        conversionService.convert('5000', Calendar).time == date
    }

    @Unroll
    void "an unparseable #target.simpleName string fails with an IllegalArgumentException cause"() {
        when:
        conversionService.convert('nope', target)

        then:
        ConversionFailedException e = thrown()
        e.cause instanceof IllegalArgumentException
        e.cause.cause instanceof NumberFormatException

        where:
        target << [Date, Double, Integer, Long, Calendar]
    }
}
