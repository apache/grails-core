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

package org.grails.web.binding

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.RequestParameter
import spock.lang.Specification

class ControllerActionParameterBindingSpec extends Specification implements ControllerUnitTest<TestBindingController> {
    void "Test request parameter name matching argument name but not matching @RequestParameter name"() {
        when:
            controller.params.name = 'Herbert'
            controller.params.age = '47'
            def model = controller.methodActionWithRequestMapping()

        then:
            !controller.hasErrors()
            null == model.name
            0 == model.age

    }

     void "Test method is duplicated and directly callable with full parameters"() {
        when:
            def firstName = 'Herbert'
            def age = 47
            def model = controller.methodActionWithRequestMapping(firstName, age)

        then:
            !controller.hasErrors()
            'Herbert' == model.name
            47 == model.age

    }

    void "Test @RequestParameter"() {
        when:
            controller.params.firstName = 'Herbert'
            controller.params.numberOfYearsOld = '47'
            def model = controller.methodActionWithRequestMapping()

        then:
            !controller.hasErrors()
            'Herbert' == model.name
            47 == model.age

    }

    void "Test binding request parameters to basic types"() {
        when:
           controller.params.stringParam = 'Herbert'
           controller.params.shortParam = '1001'
           controller.params.primitiveShortParam = '1002'
           controller.params.integerParam = '1003'
           controller.params.primitiveIntParam = '1004'
           controller.params.longParam = '1005'
           controller.params.primitiveLongParam = '1006'
           controller.params.floatParam = '1007.1007'
           controller.params.primitiveFloatParam = '1008.1008'
           controller.params.doubleParam = '1009.1009'
           controller.params.primitiveDoubleParam = '1010.1010'
           controller.params.booleanParam = 'true'
           controller.params.primitiveBooleanParam = 'true'
           controller.params.byteParam = '101'
           controller.params.primitiveByteParam = '102'
           controller.params.charParam = 'Y'
           controller.params.primitiveCharParam = 'Z'

           def model = controller.methodAction()

        then:
            !controller.hasErrors()
            'Herbert' == model.stringParam
            1001 == model.shortParam
            1002 == model.primitiveShortParam
            1003 == model.integerParam
            1004 == model.primitiveIntParam
            1005 == model.longParam
            1006 == model.primitiveLongParam
            1007.1007f == model.floatParam
            1008.1008f == model.primitiveFloatParam
            1009.1009 == model.doubleParam
            1010.1010 == model.primitiveDoubleParam
            model.booleanParam
            model.primitiveBooleanParam
            101 == model.byteParam
            102 == model.primitiveByteParam
            'Y' == model.charParam
            'Z' == model.primitiveCharParam

    }

    void "Test conversion errors"() {
        when:
           controller.params.shortParam = 'bogus'
           controller.params.primitiveShortParam = 'bogus'
           controller.params.integerParam = 'bogus'
           controller.params.primitiveIntParam = 'bogus'
           controller.params.longParam = 'bogus'
           controller.params.primitiveLongParam = 'bogus'
           controller.params.floatParam = 'bogus'
           controller.params.primitiveFloatParam = 'bogus'
           controller.params.doubleParam = 'bogus'
           controller.params.primitiveDoubleParam = 'bogus'
           controller.params.booleanParam = 'bogus'
           controller.params.primitiveBooleanParam = 'bogus'
           controller.params.byteParam = 'bogus'
           controller.params.primitiveByteParam = 'bogus'
           controller.params.charParam = 'bogus'
           controller.params.primitiveCharParam = 'bogus'

           def model = controller.methodAction()

        then:
            null == model.stringParam
            null == model.shortParam
            0 == model.primitiveShortParam
            null == model.integerParam
            0 == model.primitiveIntParam
            null == model.longParam
            0 == model.primitiveLongParam
            null == model.floatParam
            0f == model.primitiveFloatParam
            null == model.doubleParam
            0 == model.primitiveDoubleParam
            !model.booleanParam
            !model.primitiveBooleanParam
            null == model.byteParam
            0 == model.primitiveByteParam
            null == model.charParam
            0 == model.primitiveCharParam
            controller.hasErrors()
            14 == controller.errors.errorCount
            controller.errors.getFieldError('shortParam')
            controller.errors.getFieldError('primitiveShortParam')
            controller.errors.getFieldError('integerParam')
            controller.errors.getFieldError('primitiveIntParam')
            controller.errors.getFieldError('longParam')
            controller.errors.getFieldError('primitiveLongParam')
            controller.errors.getFieldError('floatParam')
            controller.errors.getFieldError('primitiveFloatParam')
            controller.errors.getFieldError('doubleParam')
            controller.errors.getFieldError('primitiveDoubleParam')
            controller.errors.getFieldError('byteParam')
            controller.errors.getFieldError('primitiveByteParam')
            controller.errors.getFieldError('charParam')
            controller.errors.getFieldError('primitiveCharParam')

            // boolean conversions should never fail
            !controller.errors.getFieldError('booleanParam')
            !controller.errors.getFieldError('primitiveBooleanParam')

    }

    void "Test uninitialized action parameters"() {
        when:
           def model = controller.methodAction()

        then:
            !controller.hasErrors()
            null == model.stringParam
            null == model.shortParam
            0 == model.primitiveShortParam
            null == model.integerParam
            0 == model.primitiveIntParam
            null == model.longParam
            0 == model.primitiveLongParam
            null == model.floatParam
            0f == model.primitiveFloatParam
            null == model.doubleParam
            0 == model.primitiveDoubleParam
            !model.booleanParam
            !model.primitiveBooleanParam
            null == model.byteParam
            0 == model.primitiveByteParam
            null == model.charParam
            0 == model.primitiveCharParam

    }
}

@Artefact('Controller')
class TestBindingController {

    def methodAction(String stringParam,
            Short shortParam,
            short primitiveShortParam,
            Integer integerParam,
            int primitiveIntParam,
            Long longParam,
            long primitiveLongParam,
            Float floatParam,
            float primitiveFloatParam,
            Double doubleParam,
            double primitiveDoubleParam,
            Boolean booleanParam,
            boolean primitiveBooleanParam,
            Byte byteParam,
            byte primitiveByteParam,
            Character charParam,
            char primitiveCharParam) {
        [ stringParam: stringParam,
            integerParam: integerParam,
            primitiveIntParam: primitiveIntParam,
            shortParam: shortParam,
            primitiveShortParam: primitiveShortParam,
            longParam: longParam,
            primitiveLongParam: primitiveLongParam,
            floatParam: floatParam,
            primitiveFloatParam: primitiveFloatParam,
            doubleParam: doubleParam,
            primitiveDoubleParam: primitiveDoubleParam,
            booleanParam: booleanParam,
            primitiveBooleanParam: primitiveBooleanParam,
            byteParam: byteParam,
            primitiveByteParam: primitiveByteParam,
            charParam: charParam,
            primitiveCharParam: primitiveCharParam ]
    }

    def methodActionWithRequestMapping(@RequestParameter('firstName') String name, @RequestParameter('numberOfYearsOld') int age) {
        [name: name, age: age]
    }

}
