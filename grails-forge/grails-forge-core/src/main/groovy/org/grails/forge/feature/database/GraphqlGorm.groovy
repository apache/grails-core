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
package org.grails.forge.feature.database

import groovy.transform.CompileStatic
import jakarta.annotation.Nullable
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.Category
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureContext

/**
 * Adds the {@code grails-data-graphql} plugin to the generated application.
 *
 * <p>GraphQL is a layer on top of GORM rather than a GORM implementation, so
 * this feature is selectable in addition to (not instead of) {@link GrailsDataHibernate5}
 * or {@link GrailsDataMongoDB}. If the user opts into GraphQL without explicitly
 * selecting a GORM persistence layer, Hibernate is added as a sensible default
 * via {@link #processSelectedFeatures(FeatureContext)}.</p>
 */
@Component
@CompileStatic
class GraphqlGorm implements Feature {

    private final GrailsDataHibernate5 grailsDataHibernate5

    GraphqlGorm(GrailsDataHibernate5 grailsDataHibernate5) {
        this.grailsDataHibernate5 = grailsDataHibernate5
    }

    @Override
    String getName() {
        return 'gorm-graphql'
    }

    @Override
    String getTitle() {
        return 'Grails Data for GraphQL'
    }

    @Override
    String getDescription() {
        return 'Generates a GraphQL schema based on entities in Grails Data.'
    }

    @Override
    String getCategory() {
        return Category.API
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return applicationType == ApplicationType.WEB || applicationType == ApplicationType.REST_API
    }

    @Override
    void processSelectedFeatures(FeatureContext featureContext) {
        // GraphQL needs a GORM implementation to introspect; default to Hibernate
        // when the user has not explicitly chosen a GORM provider.
        if (!featureContext.isPresent(GormFeature) && !featureContext.isPresent(GormOneOfFeature)) {
            featureContext.addFeature(grailsDataHibernate5)
        }
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId('grails-data-graphql')
                .implementation())
    }

    @Nullable
    @Override
    String getThirdPartyDocumentation() {
        return 'https://graphql.org'
    }
}
