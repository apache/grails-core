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
package org.grails.forge.analytics.postgres;

import io.micronaut.http.HttpStatus;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import org.grails.forge.analytics.Generated;

import java.util.List;
import java.util.stream.Collectors;

@Singleton
class AnalyticsReporter {

    private final ApplicationRepository applicationRepository;
    private final FeatureRepository featureRepository;

    AnalyticsReporter(ApplicationRepository applicationRepository, FeatureRepository featureRepository) {
        this.applicationRepository = applicationRepository;
        this.featureRepository = featureRepository;
    }

    @Transactional
    HttpStatus report(Generated generated) {
        Application application = new Application(
                generated.getType(),
                generated.getGorm(),
                generated.getReloading(),
                generated.getJdkVersion(),
                generated.getGrailsVersion()
        );
        Application saved = applicationRepository.save(application);
        List<Feature> features = generated.getSelectedFeatures().stream()
                .map(feature -> new Feature(saved, feature.getName()))
                .collect(Collectors.toList());

        featureRepository.saveAll(features);
        return HttpStatus.ACCEPTED;
    }
}
