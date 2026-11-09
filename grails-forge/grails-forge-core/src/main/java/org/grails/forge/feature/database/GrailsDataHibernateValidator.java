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
package org.grails.forge.feature.database;

import java.util.Set;

import jakarta.inject.Singleton;

import org.grails.forge.application.ApplicationType;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.validation.FeatureValidator;
import org.grails.forge.options.Options;

@Singleton
public class GrailsDataHibernateValidator implements FeatureValidator {

    @Override
    public void validatePreProcessing(Options options, ApplicationType applicationType, Set<Feature> features) {
        validateOnlyOneHibernateImplementation(features);
    }

    @Override
    public void validatePostProcessing(Options options, ApplicationType applicationType, Set<Feature> features) {
        validateOnlyOneHibernateImplementation(features);
    }

    private static void validateOnlyOneHibernateImplementation(Set<Feature> features) {
        if (features.stream().anyMatch(GrailsDataHibernate5.class::isInstance)
                && features.stream().anyMatch(GrailsDataHibernate7.class::isInstance)) {
            throw new IllegalArgumentException("Only one Grails Data for Hibernate implementation can be selected: gorm-hibernate5 or gorm-hibernate7");
        }
    }
}
