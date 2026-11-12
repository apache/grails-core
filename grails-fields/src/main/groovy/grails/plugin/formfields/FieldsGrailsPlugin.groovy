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

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.core.support.proxy.ProxyHandler
import grails.plugins.Plugin
import org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator
import org.grails.datastore.mapping.model.MappingContext
import org.grails.scaffolding.model.DomainModelServiceImpl
import org.grails.scaffolding.model.property.DomainPropertyFactory
import org.grails.scaffolding.model.property.DomainPropertyFactoryImpl

@CompileStatic
class FieldsGrailsPlugin extends Plugin {

    static final String CONSTRAINTS_EVALULATOR_BEAN_NAME = 'validateableConstraintsEvaluator'

    def grailsVersion = '8.0.0-SNAPSHOT > *'

    def loadAfter = ['domainClass']

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            registry.registerBean('beanPropertyAccessorFactory', BeanPropertyAccessorFactory) {
                it.supplier {
                    BeanPropertyAccessorFactory factory = new BeanPropertyAccessorFactory()
                    factory.constraintsEvaluator = it.bean(CONSTRAINTS_EVALULATOR_BEAN_NAME, ConstraintsEvaluator)
                    factory.proxyHandler = it.bean('proxyHandler', ProxyHandler)
                    factory.fieldsDomainPropertyFactory = it.bean('fieldsDomainPropertyFactory', DomainPropertyFactory)
                    factory.grailsDomainClassMappingContext = it.bean('grailsDomainClassMappingContext', MappingContext)
                    return factory
                }
            }
            registry.registerBean('formFieldsTemplateService', FormFieldsTemplateService)
            registry.registerBean('fieldsDomainPropertyFactory', DomainPropertyFactoryImpl)
            registry.registerBean('domainModelService', DomainModelServiceImpl) {
                it.supplier {
                    DomainModelServiceImpl domainModelService = new DomainModelServiceImpl()
                    domainModelService.domainPropertyFactory = it.bean('fieldsDomainPropertyFactory', DomainPropertyFactory)
                    return domainModelService
                }
            }
        }
    }
}
