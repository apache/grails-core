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
package grails.views


import org.grails.config.CodeGenConfig
import org.springframework.beans.BeanUtils
import spock.lang.Specification

import java.beans.PropertyDescriptor

class GenericViewConfigurationSpec extends Specification {

    void "test setting of boolean"() {
        given:
        def testClass = new TestClass()
        String yml = 'grails.views.json.compileStatic: false'
        CodeGenConfig config = new CodeGenConfig()
        config.loadYml(new ByteArrayInputStream(yml.bytes))

        expect:
        testClass.compileStatic
        !testClass.useAbsoluteLinks

        when: "no relevant properties in the config"
        testClass.readConfiguration(config)

        then: "the properties don't change from the defaults"
        !testClass.compileStatic
        !testClass.useAbsoluteLinks
    }
}

class TestClass implements GenericViewConfiguration {
    @Override
    String getViewModuleName() {
        "json"
    }

    PropertyDescriptor[] findViewConfigPropertyDescriptor() {
        BeanUtils.getPropertyDescriptors(GenericViewConfiguration)
    }
}
