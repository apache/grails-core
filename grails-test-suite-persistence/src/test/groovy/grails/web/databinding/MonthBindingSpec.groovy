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
package grails.web.databinding

import java.time.Month

import spock.lang.Specification

import grails.databinding.SimpleMapDataBindingSource
import grails.testing.gorm.DataTest
import grails.validation.Validateable

/**
 * A {@link Month} binds from the number that {@code grails.converters.JSON}, JSON views and Spring Boot render it as.
 */
class MonthBindingSpec extends Specification implements DataTest {

    void "a Month binds from #value"() {
        given:
        def binder = grailsApplication.mainContext.getBean(DataBindingUtils.DATA_BINDER_BEAN_NAME) as GrailsWebDataBinder
        def command = new MonthCommand()

        when:
        binder.bind(command, new SimpleMapDataBindingSource([month: value]))

        then:
        !command.errors.hasErrors()
        command.month == Month.SEPTEMBER

        where:
        value << [9, '9', 'SEPTEMBER']
    }

    void "a month number out of range is a binding error"() {
        given:
        def binder = grailsApplication.mainContext.getBean(DataBindingUtils.DATA_BINDER_BEAN_NAME) as GrailsWebDataBinder
        def command = new MonthCommand()

        when:
        binder.bind(command, new SimpleMapDataBindingSource([month: 13]))

        then:
        command.errors.getFieldError('month')
        command.month == null
    }
}

class MonthCommand implements Validateable {
    Month month
}
