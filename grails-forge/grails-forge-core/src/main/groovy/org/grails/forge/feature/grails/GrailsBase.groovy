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
package org.grails.forge.feature.grails

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.feature.Features
import org.grails.forge.options.Options
import org.grails.forge.template.URLTemplate

@Component
@CompileStatic
class GrailsBase implements DefaultFeature {

    @Override
    boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return true
    }

    @Override
    String getName() {
        return 'base'
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }

    @Override
    boolean isVisible() {
        return false
    }

    /** The feature of Micronaut, which gives an application a BOM of Grails for Micronaut */
    static final String MICRONAUT = 'grails-micronaut'

    /** The feature of Grails Data for Hibernate 7, which gives an application a BOM of Grails for Hibernate 7 */
    static final String HIBERNATE7 = 'gorm-hibernate7'

    /** The feature of GORM for Neo4j, which gives an application the BOM of Grails for Neo4j */
    static final String NEO4J = 'gorm-neo4j'

    /**
     * @param micronaut whether the application has the Micronaut feature
     * @param hibernate7 whether the application has Grails Data for Hibernate 7
     * @param neo4j whether the application has GORM for Neo4j
     * @return the artifact of the BOM of Grails such an application is generated with, see
     * {@link #bomArtifactId(Collection)}
     * @since 9.0
     */
    static String bomArtifactId(boolean micronaut, boolean hibernate7, boolean neo4j) {
        if (micronaut) {
            return hibernate7 ? 'grails-hibernate7-micronaut-bom' : 'grails-micronaut-bom'
        }
        if (hibernate7) {
            return 'grails-hibernate7-bom'
        }
        return neo4j ? 'grails-neo4j-bom' : 'grails-bom'
    }

    /**
     * Returns the artifact id, in the {@code org.apache.grails} group, of the one Grails BOM that an
     * application with the given features imports: in its dependencies, on its buildscript classpath
     * and in {@code buildSrc}.
     *
     * <p>When Micronaut is used, the application must consume the Micronaut BOM variant as an
     * enforcedPlatform so the Micronaut platform cannot override the Grails BOM. The Hibernate 7 BOM
     * variants pin the Hibernate 7 dependency versions (e.g. jandex) that conflict with the default
     * (Hibernate 5) grails-bom - see grails-core issue #15942. GORM for Neo4j needs the Neo4j driver
     * that grails-neo4j-bom holds strictly, which is older than the one Spring Boot manages, and a
     * strict version that only comes in transitively cannot replace the version the application's
     * own BOM asks for. An application with GORM for Neo4j therefore imports grails-neo4j-bom, which
     * imports grails-bom, so GORM for Hibernate 5 next to it keeps every version grails-bom gives.
     * No BOM covers GORM for Neo4j together with Hibernate 7 or Micronaut, and
     * {@link org.grails.forge.feature.database.Neo4jGormValidator} refuses those combinations
     * before this is asked.</p>
     *
     * @param features the names of the features of the application
     * @return the artifact id of the Grails BOM
     * @since 9.0
     */
    static String bomArtifactId(Collection<String> features) {
        return bomArtifactId(features.contains(MICRONAUT), features.contains(HIBERNATE7), features.contains(NEO4J))
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        Features features = generatorContext.getFeatures()
        boolean useMicronautBom = features.contains(MICRONAUT)
        String bomArtifactId = bomArtifactId(features)

        generatorContext.addDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId(bomArtifactId)
                .pom(true)
                .enforced(useMicronautBom)
                .version('$grailsVersion')
                .implementation())

        generatorContext.addBuildscriptDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId(bomArtifactId)
                .pom(true)
                .version('$grailsVersion')
                .classpath())

        generatorContext.addDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId('grails-core')
                .implementation())
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId('grails-web-boot')
                .implementation())
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId('grails-logging')
                .implementation())
        generatorContext.addDependency(Dependency.builder()
                .groupId('org.apache.grails')
                .artifactId('grails-events')
                .implementation())

        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader()
        generatorContext.addTemplate('src/main/groovy', new URLTemplate('src/main/groovy/.gitkeep', classLoader.getResource('.gitkeep')))
        generatorContext.addTemplate('src/test/groovy', new URLTemplate('src/test/groovy/.gitkeep', classLoader.getResource('.gitkeep')))
        generatorContext.addTemplate('src/integration-test/groovy', new URLTemplate('src/integration-test/groovy/.gitkeep', classLoader.getResource('.gitkeep')))

        generatorContext.addTemplate('grails-app/services', new URLTemplate('grails-app/services/.gitkeep', classLoader.getResource('.gitkeep')))
        generatorContext.addTemplate('grails-app/domain', new URLTemplate('grails-app/domain/.gitkeep', classLoader.getResource('.gitkeep')))

        if (!generatorContext.getApplicationType().isRestApi()) {
            generatorContext.addTemplate('grails-app/taglib', new URLTemplate('grails-app/taglib/.gitkeep', classLoader.getResource('.gitkeep')))
        }
    }
}
