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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy

import grails.core.GrailsApplication
import org.grails.datastore.gorm.validation.constraints.factory.ConstraintFactory
import org.grails.datastore.mapping.model.MappingContext

import org.grails.plugins.domain.support.DefaultConstraintEvaluatorFactoryBean
import org.grails.plugins.domain.support.DefaultMappingContextFactoryBean
import org.grails.plugins.domain.support.ValidatorRegistryFactoryBean
import org.grails.plugins.i18n.I18nAutoConfiguration

@CompileStatic
// TODO: datasource plugin is supposed to always load after this (currently will because this is a configuration)
@AutoConfiguration(after = [I18nAutoConfiguration])
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class GrailsDomainClassAutoConfiguration {

    GrailsApplication grailsApplication

    List<MessageSource> messageSources

    @Autowired
    GrailsDomainClassAutoConfiguration(GrailsApplication grailsApplication, List<MessageSource> messageSources) {
        this.grailsApplication = grailsApplication
        this.messageSources = messageSources
    }

    @Lazy
    @Bean(name = 'grailsDomainClassMappingContext')
    @ConditionalOnMissingBean(name = 'grailsDomainClassMappingContext')
    DefaultMappingContextFactoryBean grailsDomainClassMappingContext(List<ConstraintFactory> factories) {
        new DefaultMappingContextFactoryBean(grailsApplication, messageSources).tap {
            constraintFactories = factories ?: []
        }
    }

    @Lazy
    @Bean
    DefaultConstraintEvaluatorFactoryBean validateableConstraintsEvaluator(@Qualifier('grailsDomainClassMappingContext') MappingContext mappingContext) {
        new DefaultConstraintEvaluatorFactoryBean(messageSources, mappingContext, grailsApplication)
    }

    @Lazy
    @Bean
    ValidatorRegistryFactoryBean gormValidatorRegistry(@Qualifier('grailsDomainClassMappingContext') MappingContext mappingContext) {
        new ValidatorRegistryFactoryBean().tap {
            it.mappingContext = mappingContext
        }
    }
}
