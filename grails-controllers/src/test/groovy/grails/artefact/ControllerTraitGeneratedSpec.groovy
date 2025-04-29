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

package grails.artefact

import groovy.transform.Generated
import spock.lang.Specification

import java.lang.reflect.Method

class ControllerTraitGeneratedSpec extends Specification {

    void "test that all Controller trait methods are marked as Generated"() {
        // It will test also RequestForwarder, ResponseRedirector and ResponseRenderer traits, cause Controller implements them

        expect: "all Controller methods are marked as Generated on implementation class"
        Controller.getMethods().each { Method traitMethod ->
            assert TestController.class.getMethod(traitMethod.name, traitMethod.parameterTypes).isAnnotationPresent(Generated)
        }
    }
}

class TestController implements Controller {

}
