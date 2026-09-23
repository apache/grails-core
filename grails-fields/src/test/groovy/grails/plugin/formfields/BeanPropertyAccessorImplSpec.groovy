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
package grails.plugin.formfields

import grails.core.GrailsApplication
import spock.lang.Specification

class BeanPropertyAccessorImplSpec extends Specification {

    void "a map of properties constructs the accessor, as the factory does"() {
        given:
        def bean = new Object()
        def grailsApplication = Mock(GrailsApplication)
        Map<String, Object> params = [
                rootBean: bean, rootBeanType: Object, pathFromRoot: 'name',
                beanType: Object, propertyName: 'name', propertyType: String,
                value: 'value', grailsApplication: grailsApplication,
        ]

        when:
        def accessor = new BeanPropertyAccessorImpl(params)

        then:
        accessor.rootBean.is(bean)
        accessor.rootBeanType == Object
        accessor.pathFromRoot == 'name'
        accessor.beanType == Object
        accessor.propertyName == 'name'
        accessor.propertyType == String
        accessor.value == 'value'
        accessor.grailsApplication.is(grailsApplication)
        accessor.domainProperty == null
        accessor.constraints == null
    }
}
