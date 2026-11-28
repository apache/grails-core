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
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Feature
import org.grails.forge.feature.grails.GrailsBase
import org.grails.forge.feature.validation.FeatureValidator
import org.grails.forge.options.Options

/**
 * Refuses GORM for Neo4j together with the features that no Grails BOM covers it with.
 *
 * <p>GORM for Neo4j needs the Neo4j driver that {@code grails-neo4j-bom} holds strictly, which is older
 * than the one Spring Boot manages. An application with GORM for Neo4j imports {@code grails-neo4j-bom}
 * as its one Grails BOM ({@link GrailsBase#bomArtifactId(Collection)}). That BOM imports
 * {@code grails-bom}, so it covers GORM for Hibernate 5, but there is no variant of it for Hibernate 7 or
 * for Micronaut, and with any other Grails BOM the runtime class path of the application does not
 * resolve. The features are looked at as they are selected and again with those the selected ones bring
 * in, so that the application is refused before anything is written: by a create command, by
 * {@code feature add}, and in what {@code feature list} says.</p>
 *
 * @since 9.0
 */
@Component
@CompileStatic
class Neo4jGormValidator implements FeatureValidator {

    // the features no Grails BOM covers GORM for Neo4j with, in the order they are looked for, with why
    private static final Map<String, String> NOT_COVERED = [
            (GrailsBase.HIBERNATE7): 'gorm-neo4j cannot be combined with gorm-hibernate7: an application with GORM for Neo4j uses grails-neo4j-bom, which ' +
                    'manages Hibernate 5, and no Grails BOM manages the Neo4j driver of GORM for Neo4j together with Hibernate 7. Select gorm-hibernate5, ' +
                    'or no Hibernate implementation, with gorm-neo4j.',
            (GrailsBase.MICRONAUT): 'gorm-neo4j cannot be combined with grails-micronaut: an application with GORM for Neo4j uses grails-neo4j-bom, and no ' +
                    'Grails BOM manages the Neo4j driver of GORM for Neo4j together with Micronaut.',
    ].asImmutable()

    @Override
    void validatePreProcessing(Options options, ApplicationType applicationType, Set<Feature> features) {
        validateNeo4jBomCoversFeatures(features)
    }

    @Override
    void validatePostProcessing(Options options, ApplicationType applicationType, Set<Feature> features) {
        validateNeo4jBomCoversFeatures(features)
    }

    /**
     * @param features the names of the features of an application
     * @return why no Grails BOM covers the features, or {@code null} when one does
     */
    static String notCovered(Collection<String> features) {
        String other = notCoveredWith(features)
        return other == null ? null : NOT_COVERED.get(other)
    }

    /**
     * @param feature the name of a feature an application does not have
     * @param features the names of the features the application has
     * @return what a list of features says of the feature when no Grails BOM covers it with the
     * features of the application, or {@code null} when one does
     */
    static String notCoveredBeside(String feature, Collection<String> features) {
        if (features.contains(feature)) {
            return null
        }
        List<String> with = new ArrayList<String>(features)
        with.add(feature)
        String other = notCoveredWith(with)
        if (other == null) {
            return null
        }
        return "cannot be combined with ${feature == GrailsBase.NEO4J ? other : GrailsBase.NEO4J}: no Grails BOM covers both".toString()
    }

    // the feature GORM for Neo4j is with that no Grails BOM covers it with, or null
    private static String notCoveredWith(Collection<String> features) {
        if (!features.contains(GrailsBase.NEO4J)) {
            return null
        }
        return NOT_COVERED.keySet().find { String each -> features.contains(each) }
    }

    private static void validateNeo4jBomCoversFeatures(Set<Feature> features) {
        String refusal = notCovered(features.collect { Feature feature -> feature.getName() })
        if (refusal != null) {
            throw new IllegalArgumentException(refusal)
        }
    }
}
