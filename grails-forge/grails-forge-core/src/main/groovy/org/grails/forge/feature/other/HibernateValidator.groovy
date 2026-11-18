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
package org.grails.forge.feature.other

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.Category
import org.grails.forge.feature.Feature

@Component
@CompileStatic
class HibernateValidator implements Feature {

    @Override
    String getName() {
        return 'hibernate-validator'
    }

    @Override
    String getTitle() {
        return 'Hibernate Validator'
    }

    @Override
    String getDescription() {
        return 'Add support for the Hibernate Validator.'
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.hibernate.validator')
                .artifactId('hibernate-validator')
                .implementation())
    }

    @Override
    String getCategory() {
        return Category.VALIDATION
    }

    @Override
    String getDocumentation() {
        return 'https://hibernate.org/validator/'
    }
}
