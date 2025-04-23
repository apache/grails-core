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

import grails.core.support.proxy.DefaultProxyHandler
import grails.testing.gorm.DataTest
import grails.testing.web.GrailsWebUnitTest
import org.grails.datastore.mapping.model.MappingContext
import org.grails.scaffolding.model.property.DomainPropertyFactoryImpl
import spock.lang.Specification

/**
 * Created by jameskleeh on 5/3/17.
 */
abstract class BuildsAccessorFactory extends Specification implements GrailsWebUnitTest, DataTest {

    void setupSpec() {
        defineBeans { ->
            def dpf = new DomainPropertyFactoryImpl(grailsDomainClassMappingContext: applicationContext.getBean("grailsDomainClassMappingContext", MappingContext), trimStrings: true, convertEmptyStringsToNull: true)

            beanPropertyAccessorFactory(BeanPropertyAccessorFactory) {
                constraintsEvaluator = ref(FieldsGrailsPlugin.CONSTRAINTS_EVALULATOR_BEAN_NAME)
                proxyHandler = new DefaultProxyHandler()
                grailsDomainClassMappingContext = ref("grailsDomainClassMappingContext")
                fieldsDomainPropertyFactory = dpf
                grailsApplication = ref('grailsApplication')
            }
        }
    }

    BeanPropertyAccessorFactory getFactory() {
        applicationContext.getBean(BeanPropertyAccessorFactory)
    }
}