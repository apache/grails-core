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

package org.grails.plugins.domain.support

import grails.core.GrailsApplication
import grails.util.GrailsMessageSourceUtils
import org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.ConstraintRegistry
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.model.MappingContext
import org.grails.validation.ConstraintEvalUtils
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.MessageSource

class DefaultConstraintEvaluatorFactoryBean implements FactoryBean<ConstraintsEvaluator> {

    MessageSource messageSource

    @Autowired
    setMessageSource(List<MessageSource> messageSources) {
        setMessageSource(GrailsMessageSourceUtils.findPreferredMessageSource(messageSources))
    }

    void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource
    }

    @Autowired
    @Qualifier('grailsDomainClassMappingContext')
    MappingContext grailsDomainClassMappingContext

    @Autowired
    GrailsApplication grailsApplication

    @Override
    ConstraintsEvaluator getObject() throws Exception {
        ConstraintRegistry registry = new DefaultConstraintRegistry(messageSource)

        new DefaultConstraintEvaluator(registry, grailsDomainClassMappingContext, ConstraintEvalUtils.getDefaultConstraints(grailsApplication.config))
    }

    @Override
    Class<?> getObjectType() {
        ConstraintsEvaluator
    }

    @Override
    boolean isSingleton() {
        true
    }
}
