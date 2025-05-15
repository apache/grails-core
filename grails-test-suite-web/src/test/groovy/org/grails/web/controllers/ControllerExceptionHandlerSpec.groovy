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

package org.grails.web.controllers

import grails.artefact.Artefact
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.mapping.mvc.exceptions.CannotRedirectException
import spock.lang.PendingFeatureIf

import java.sql.BatchUpdateException
import java.sql.SQLException
import spock.lang.Issue
import spock.lang.Specification

class ControllerExceptionHandlerSpec extends Specification implements ControllerUnitTest<ErrorHandlersController> {

    @Issue('GRAILS-11453')
    void 'Test exception handler which renders a String'() {
        when:
        params.exceptionToThrow = 'java.sql.SQLException'
        controller.testAction()

        then:
        response.contentAsString == 'A SQLException Was Handled From DatabaseExceptionHandler'
    }

    @Issue('GRAILS-11453')
    void 'Test exception handler which renders a String from command object action'() {
        when:
        params.exceptionToThrow = 'java.sql.SQLException'
        controller.testActionWithCommandObject()

        then:
        response.contentAsString == 'A SQLException Was Handled From DatabaseExceptionHandler'
    }

    @Issue(['GRAILS-11095', 'GRAILS-11453'])
    void 'Test passing command object as argument to action'() {
        when:
        controller.testActionWithCommandObject(new MyCommand(exceptionToThrow: 'java.sql.SQLException'))

        then:
        response.contentAsString == 'A SQLException Was Handled From DatabaseExceptionHandler'
    }

    @Issue('GRAILS-11453')
    void 'Test exception handler which renders a String from action with typed parameter'() {
        when:
        params.exceptionToThrow = 'java.sql.SQLException'
        controller.testActionWithNonCommandObjectParameter()

        then:
        response.contentAsString == 'A SQLException Was Handled From DatabaseExceptionHandler'
    }

    @Issue('GRAILS-11453')
    void 'Test exception handler which issues a redirect'() {
        when:
        params.exceptionToThrow = 'java.sql.BatchUpdateException'
        controller.testAction()

        then:
        response.redirectedUrl == '/logging/batchProblem'
    }

    @Issue('GRAILS-11453')
    void 'Test exception handler which issues a redirect from a command object action'() {
        when:
        params.exceptionToThrow = 'java.sql.BatchUpdateException'
        controller.testActionWithCommandObject()

        then:
        response.redirectedUrl == '/logging/batchProblem'
    }

    @Issue('GRAILS-11453')
    void 'Test exception handler which issues a redirect from action with typed parameter'() {
        when:
        params.exceptionToThrow = 'java.sql.BatchUpdateException'
        controller.testActionWithNonCommandObjectParameter()

        then:
        response.redirectedUrl == '/logging/batchProblem'
    }

    void 'Test exception handler which returns a model'() {
        when:
        params.exceptionToThrow = 'java.lang.NumberFormatException'
        def model = controller.testAction()

        then:
        model.problemDescription == 'A Number Was Invalid'
    }

    void 'Test exception handler which returns a model from a command object action'() {
        when:
        params.exceptionToThrow = 'java.lang.NumberFormatException'
        def model = controller.testActionWithCommandObject()

        then:
        model.problemDescription == 'A Number Was Invalid'
    }

    void 'Test exception handler which returns a model from action with typed parameter'() {
        when:
        params.exceptionToThrow = 'java.lang.NumberFormatException'
        def model = controller.testActionWithNonCommandObjectParameter()

        then:
        model.problemDescription == 'A Number Was Invalid'
    }

    @PendingFeatureIf({
        // thrown() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void 'Test throwing an exception that does not have a handler'() {
        when:
        params.exceptionToThrow = 'grails.web.mapping.mvc.exceptions.CannotRedirectException'
        def model = controller.testActionWithNonCommandObjectParameter()

        then:
        thrown(CannotRedirectException)
    }

    @PendingFeatureIf({
        // thrown() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void 'Test throwing an exception that does not have a handler and does match a private method in the parent controller'() {
        when: 'a controller action throws an exception which matches an inherited private method which should not be treated as an exception handler'
        params.exceptionToThrow = 'java.io.IOException'
        def model = controller.testActionWithNonCommandObjectParameter()

        then: 'the method is ignored and the exception is thrown'
        thrown IOException
    }

    @PendingFeatureIf({
        // thrown() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void 'Test action throws an exception that does not have a corresponding error handler'() {
        when:
        params.exceptionToThrow = 'java.lang.UnsupportedOperationException'
        controller.testAction()

        then:
        thrown UnsupportedOperationException
    }

    @PendingFeatureIf({
        // thrown() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void 'Test command object action throws an exception that does not have a corresponding error handler'() {
        when:
        params.exceptionToThrow = 'java.lang.UnsupportedOperationException'
        controller.testActionWithCommandObject()

        then:
        thrown UnsupportedOperationException
    }

    @PendingFeatureIf({
        // thrown() does currently not work with Groovy 5
        GroovySystem.version.startsWith('5')
    })
    void 'Test typed parameter action throws an exception that does not have a corresponding error handler'() {
        when:
        params.exceptionToThrow = 'java.lang.UnsupportedOperationException'
        controller.testActionWithNonCommandObjectParameter()

        then:
        thrown UnsupportedOperationException
    }

    @Issue('GRAILS-10866')    
    void 'Test exception handler for an Exception class written in Groovy'() {
        when:
        params.exceptionToThrow = MyException.name
        controller.testActionWithNonCommandObjectParameter()

        then:
        response.contentAsString == 'MyException was thrown'
    }
}

@Artefact('Controller')
abstract class SomeAbstractController {
    
    private somePrivateMethodWhichIsNotAnExceptionHandler(IOException e) {
    }
}

trait DatabaseExceptionHandler {
    def handleSQLException(SQLException e) {
        render 'A SQLException Was Handled From DatabaseExceptionHandler'
    }

    // BatchUpdateException extends SQLException
    def handleSQLException(BatchUpdateException e) {
        redirect controller: 'logging', action: 'batchProblem'
    }
}

@Artefact('Controller')
class ErrorHandlersController extends SomeAbstractController implements DatabaseExceptionHandler {

    def testAction() {
        def exceptionClass = Class.forName(params.exceptionToThrow)
        throw exceptionClass.getDeclaredConstructor().newInstance()
    }

    def testActionWithCommandObject(MyCommand co) {
        def exceptionClass = Class.forName(co.exceptionToThrow)
        throw exceptionClass.getDeclaredConstructor().newInstance()
    }


    def testActionWithNonCommandObjectParameter(String exceptionToThrow) {
        def exceptionClass = Class.forName(exceptionToThrow)
        throw exceptionClass.getDeclaredConstructor().newInstance()
    }

    def handleNumberFormatException(NumberFormatException nfe) {
        [problemDescription: 'A Number Was Invalid']
    }
    
    def handleSomeGroovyException(MyException e) {
        render 'MyException was thrown'
    }
}

class MyCommand {
    String exceptionToThrow
}

class MyException extends Exception {
    
}