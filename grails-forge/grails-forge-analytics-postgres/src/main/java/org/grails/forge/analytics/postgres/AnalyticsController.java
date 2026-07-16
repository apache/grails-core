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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import org.grails.forge.analytics.Generated;

import java.util.List;

@Controller("/analytics")
@ExecuteOn(TaskExecutors.IO)
public class AnalyticsController {

    private final FeatureRepository featureRepository;
    private final AnalyticsReporter analyticsReporter;

    public AnalyticsController(
            FeatureRepository featureRepository,
            AnalyticsReporter analyticsReporter) {
        this.featureRepository = featureRepository;
        this.analyticsReporter = analyticsReporter;
    }

    @Get("top/features")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<TotalDTO> topFeatures() {
        return featureRepository.topFeatures();
    }

    @Get("top/jdks")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<TotalDTO> topJdks() {
        return featureRepository.topJdkVersion();
    }

    @Get("top/buildTools")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<TotalDTO> topBuilds() {
        return featureRepository.topBuildTools();
    }

    @Get("top/gorm")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<TotalDTO> topGorm() {
        return featureRepository.topGorm();
    }

    @Get("top/reloading")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public List<TotalDTO> topReloading() {
        return featureRepository.topReloading();
    }

    /**
     * Report analytics.
     * @param generated The generated data
     * @return A future
     */
    @Post("report")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    @ExecuteOn(TaskExecutors.IO)
    public HttpStatus applicationGenerated(@NonNull @Body Generated generated) {
        return analyticsReporter.report(generated);
    }
}
