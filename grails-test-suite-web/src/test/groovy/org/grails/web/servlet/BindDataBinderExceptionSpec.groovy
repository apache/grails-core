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
package org.grails.web.servlet

import grails.artefact.Artefact
import grails.databinding.DataBinder
import grails.databinding.DataBindingSource
import grails.databinding.events.DataBindingListener
import grails.testing.web.controllers.ControllerUnitTest
import groovy.xml.slurpersupport.GPathResult
import org.grails.spring.beans.factory.InstanceFactoryBean
import spock.lang.Specification

/**
 * Verifies that when the underlying data binder throws an exception whose message is null,
 * bindData and secureBindData still produce a non-null, actionable ObjectError default message
 * rather than a null/empty one.
 */
class BindDataBinderExceptionSpec extends Specification implements ControllerUnitTest<ExceptionBindingController> {

    Closure doWithSpring() {{ ->
        grailsWebDataBinder(InstanceFactoryBean, new ThrowingDataBinder(), DataBinder)
    }}

    void 'Test bindData falls back to the exception class name when the binder throws with a null message'() {
        when:
        def model = controller.bindDataWithThrowingBinder()
        def bindingResult = model.bindingResult

        then:
        bindingResult.hasErrors()
        bindingResult.errorCount == 1
        bindingResult.allErrors[0].defaultMessage == 'java.lang.RuntimeException'
    }

    void 'Test secureBindData falls back to the exception class name when the binder throws with a null message'() {
        when:
        def model = controller.secureBindDataWithThrowingBinder()
        def bindingResult = model.bindingResult

        then:
        bindingResult.hasErrors()
        bindingResult.errorCount == 1
        bindingResult.allErrors[0].defaultMessage == 'java.lang.RuntimeException'
    }
}

@Artefact('Controller')
class ExceptionBindingController {

    def bindDataWithThrowingBinder() {
        def target = new ExceptionCommandObject()
        def bindingResult = bindData(target, [name: 'Marc Palmer'])
        [bindingResult: bindingResult]
    }

    def secureBindDataWithThrowingBinder() {
        def target = new ExceptionCommandObject()
        def bindingResult = secureBindData(target, [name: 'Marc Palmer'], ['name'])
        [bindingResult: bindingResult]
    }
}

class ExceptionCommandObject {
    String name
}

class ThrowingDataBinder implements DataBinder {

    void bind(Object obj, DataBindingSource source, String filter, List<String> whiteList, List<String> blackList, DataBindingListener listener) {
        throw new RuntimeException()
    }

    void bind(Object obj, DataBindingSource source, String filter, List<String> whiteList, List<String> blackList) {
        throw new RuntimeException()
    }

    void bind(Object obj, GPathResult gpath) {
        throw new RuntimeException()
    }

    void bind(Object obj, DataBindingSource source, List<String> whiteList, List<String> blackList) {
        throw new RuntimeException()
    }

    void bind(Object obj, DataBindingSource source, List<String> whiteList) {
        throw new RuntimeException()
    }

    void bind(Object obj, DataBindingSource source, DataBindingListener listener) {
        throw new RuntimeException()
    }

    void bind(Object obj, DataBindingSource source) {
        throw new RuntimeException()
    }
}
