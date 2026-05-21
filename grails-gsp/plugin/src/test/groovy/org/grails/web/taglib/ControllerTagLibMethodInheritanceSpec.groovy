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

package org.grails.web.taglib

import grails.artefact.Artefact
import grails.testing.web.GrailsWebUnitTest
import spock.lang.Issue
import spock.lang.Specification

class ControllerTagLibMethodInheritanceSpec extends Specification implements GrailsWebUnitTest {

    void setupSpec() {
        mockTagLibs(FirstTagLib, SecondTagLib)
    }

    @Issue('GRAILS-10031')
    void 'Test calling an inherited tag which invokes a method overridden in the subclass'() {
        expect:
        'FirstTagLib.doSomething()' == applyTemplate('${first.myTag()}')
        'SecondTagLib.doSomething()' == applyTemplate('${second.myTag()}')
    }
}

@Artefact("TagLibrary")
class FirstTagLib {

    static namespace = 'first'
    
    protected String doSomething() {
        'FirstTagLib.doSomething()'
    }

    def myTag = { attrs ->
        out << doSomething()
    }
}

@Artefact("TagLibrary")
class SecondTagLib extends FirstTagLib {

    static namespace = 'second'
    
    protected String doSomething() {
        'SecondTagLib.doSomething()'
    }
}
