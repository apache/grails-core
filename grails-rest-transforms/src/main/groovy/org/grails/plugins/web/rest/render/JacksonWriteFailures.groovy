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

import tools.jackson.core.JacksonException
import tools.jackson.databind.exc.InvalidDefinitionException

import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.http.converter.HttpMessageNotWritableException

/**
 * Reports the failure of a response Grails writes with a Jackson mapper itself as Spring MVC's Jackson message
 * converters report theirs, with the messages they give, so that an application handles both alike: a type definition
 * error as an {@link HttpMessageConversionException}, and any other failure, one of the response's writer included, as
 * an {@link HttpMessageNotWritableException}. Spring's Jackson XML converter reports its failures as "Could not write
 * JSON" too.
 *
 * @since 9.0
 */
@CompileStatic
final class JacksonWriteFailures {

    private JacksonWriteFailures() {
    }

    /**
     * @param failure the failure of a write
     * @return the exception Spring MVC's Jackson message converters report it with
     */
    static HttpMessageConversionException reported(Exception failure) {
        if (failure instanceof InvalidDefinitionException) {
            return new HttpMessageConversionException("Type definition error: ${failure.type}", failure)
        }
        String message = failure instanceof JacksonException ? failure.originalMessage : failure.message
        return new HttpMessageNotWritableException("Could not write JSON: ${message}", failure)
    }
}
