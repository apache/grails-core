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
package org.grails.openapi.springdoc

import groovy.transform.CompileStatic

import org.springdoc.core.customizers.OpenApiBuilderCustomizer
import org.springdoc.core.service.OpenAPIService
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

import org.grails.openapi.GrailsModelConverter

/**
 * Starts recording, as springdoc starts building a document, the classes it resolves for its own
 * endpoints, which the Grails description of that document takes. Where the document is built for
 * a request, the record is dropped as the request completes too, so a build that fails before the
 * Grails description takes it leaves nothing on a pooled thread.
 */
@CompileStatic
class ResolvedNamesRecorder implements OpenApiBuilderCustomizer {

    private static final String DROP_RECORD = ResolvedNamesRecorder.name + '.DROP_RECORD'

    @Override
    void customise(OpenAPIService openApiService) {
        GrailsModelConverter.recordResolvedNames()
        RequestContextHolder.getRequestAttributes()?.registerDestructionCallback(DROP_RECORD,
                { GrailsModelConverter.takeResolvedNames() } as Runnable, RequestAttributes.SCOPE_REQUEST)
    }
}
