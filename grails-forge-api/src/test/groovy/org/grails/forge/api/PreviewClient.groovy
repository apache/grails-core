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
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import org.grails.forge.api.preview.PreviewDTO
import org.grails.forge.application.ApplicationType
import org.grails.forge.options.BuildTool
import org.grails.forge.options.Language

@Client('/preview')
interface PreviewClient  {
    @Get(uri = "/web/{name}{?features,build,test,lang}", consumes = MediaType.APPLICATION_JSON)
    PreviewDTO previewApp(
            ApplicationType type,
            String name,
            @Nullable List<String> features,
            @Nullable BuildTool build,
            @Nullable TestFramework test,
            @Nullable Language lang
    );
}
