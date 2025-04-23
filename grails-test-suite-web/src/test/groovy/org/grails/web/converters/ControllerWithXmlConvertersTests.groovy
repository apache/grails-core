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

package org.grails.web.converters

import grails.artefact.Artefact
import grails.converters.XML
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

class ControllerWithXmlConvertersTests extends Specification implements ControllerUnitTest<XmlController> {

    void testConvertArrayWithNullEments() {
        when:
        controller.convertArray()

        then:
        response.text == '<?xml version="1.0" encoding="UTF-8"?><array><string>tst0</string><string>tst1</string><null /><string>fail</string></array>'
    }

    void testConvertListWithNullEments() {
        when:
        controller.convertList()

        then:
        response.text == '<?xml version="1.0" encoding="UTF-8"?><list><string>tst0</string><string>tst1</string><null /><string>fail</string></list>'
    }
}

@Artefact("Controller")
class XmlController {

     def convertArray() {
        def ar = []

        ar[0] = "tst0"
        ar[1] = "tst1"
        ar[3] = "fail"

        ar = ar as String[]

        render ar as XML
    }

    def convertList() {
        def ar = []

        ar[0] = "tst0"
        ar[1] = "tst1"
        ar[3] = "fail"

        render ar as XML
    }
}
