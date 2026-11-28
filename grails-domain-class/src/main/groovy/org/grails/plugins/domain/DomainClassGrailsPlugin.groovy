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
package org.grails.plugins.domain

import groovy.transform.CompileStatic

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.MessageSource

import grails.core.GrailsApplication
import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor
import org.grails.datastore.gorm.validation.constraints.factory.ConstraintFactory
import org.grails.datastore.mapping.model.MappingContext
import org.grails.plugins.domain.support.DefaultConstraintEvaluatorFactoryBean
import org.grails.plugins.domain.support.DefaultMappingContextFactoryBean
import org.grails.plugins.domain.support.ValidatorRegistryFactoryBean

/**
 * Configures the domain classes in the spring context.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@CompileStatic
// The generated DomainClassAutoConfiguration comes after I18nAutoConfiguration, because this plugin
// depends on i18n: the compiler orders the generated auto-configurations as the plugins are ordered.
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@GrailsPluginDescriptor(
        after = ['controllers', 'dataSource'],
        dependsOn = ['i18n:${plugin.version}'],
        watchedResources = ['file:./grails-app/domain/**/*.groovy',
                                'file:./plugins/*/grails-app/domain/**/*.groovy'])
class DomainClassGrailsPlugin extends Plugin {

    def beans = {
        bean('grailsDomainClassMappingContext', DefaultMappingContextFactoryBean).lazy() { GrailsApplication grailsApplication, List<MessageSource> messageSources, List<ConstraintFactory> factories ->
            new DefaultMappingContextFactoryBean(grailsApplication, messageSources).tap {
                constraintFactories = factories ?: []
            }
        }

        bean('validateableConstraintsEvaluator', DefaultConstraintEvaluatorFactoryBean).lazy() {
                List<MessageSource> messageSources,
                @Qualifier('grailsDomainClassMappingContext') MappingContext mappingContext,
                GrailsApplication grailsApplication ->
        }

        bean('gormValidatorRegistry', ValidatorRegistryFactoryBean).lazy() { @Qualifier('grailsDomainClassMappingContext') MappingContext mappingContext ->
            new ValidatorRegistryFactoryBean().tap {
                it.mappingContext = mappingContext
            }
        }
    }
}
