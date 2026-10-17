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
package org.grails.plugins.web.rest.render

import groovy.transform.CompileStatic

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail

import grails.rest.render.RenderContext

/**
 * Prepares the response to a {@link ProblemDetail}, as the JSON and XML renderers write it: the response takes the
 * status the problem carries, and a problem without an {@code instance} is given the path of the resource.
 *
 * @since 9.0
 */
@CompileStatic
class ProblemDetailResponse {

    private ProblemDetailResponse() {
    }

    /**
     * @param problem the problem a response is written for
     * @param context the context of the response
     */
    static void prepare(ProblemDetail problem, RenderContext context) {
        setStatus(context, problem.status)
        if (problem.instance == null && context.resourcePath) {
            problem.instance = URI.create(context.resourcePath)
        }
    }

    /**
     * A problem may carry any status code, as in Spring MVC, while {@link RenderContext} takes an
     * {@link HttpStatus}; a code without a constant is written to the servlet response directly.
     */
    private static void setStatus(RenderContext context, int code) {
        HttpStatus status = HttpStatus.resolve(code)
        if (status != null) {
            context.setStatus(status)
        }
        else if (context instanceof ServletRenderContext) {
            ((ServletRenderContext) context).webRequest.currentResponse.setStatus(code)
        }
        else {
            throw new IllegalArgumentException(
                    "Status [${code}] has no HttpStatus constant, which ${context.getClass().name} requires.")
        }
    }
}
