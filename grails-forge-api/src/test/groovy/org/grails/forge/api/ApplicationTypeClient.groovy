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

package org.grails.forge.api

import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpHeaders
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import org.grails.forge.application.ApplicationType
import org.grails.forge.options.FeatureFilter

@Client('/')
interface ApplicationTypeClient extends ApplicationTypeOperations {

    @Get("/application-types/{type}/features{?filter*}")
    @Header(name = HttpHeaders.ACCEPT_LANGUAGE, value = "es")
    FeatureList spanishFeatures(ApplicationType type,
                                @Nullable FeatureFilter filter);

    @Get("/application-types/{type}/features/default{?filter*}")
    @Header(name = HttpHeaders.ACCEPT_LANGUAGE, value = "es")
    FeatureList spanishDefaultFeatures(ApplicationType type,
                                       @Nullable FeatureFilter filter);
}
