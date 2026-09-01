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
package grails.rest.render.errors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import org.grails.web.errors.ValidationErrorEntries;

/**
 * Creates an RFC 9457 problem detail from Spring validation errors.
 *
 * <p>Rejected field values are omitted by default because they may contain secrets or other
 * sensitive request data. Applications can explicitly include them by using
 * {@link #ValidationProblemDetailFactory(boolean)}.</p>
 *
 * @since 8.0
 */
public final class ValidationProblemDetailFactory {

    public static final String ERRORS_PROPERTY = "errors";

    private final boolean includeRejectedValues;

    private final MessageSource messageSource;

    public ValidationProblemDetailFactory() {
        this(false, null);
    }

    public ValidationProblemDetailFactory(boolean includeRejectedValues) {
        this(includeRejectedValues, null);
    }

    /**
     * @param includeRejectedValues whether to expose submitted values, which may be sensitive
     * @param messageSource resolves each error message for the current locale; without one the
     * errors carry their unresolved default messages, argument placeholders included
     */
    public ValidationProblemDetailFactory(boolean includeRejectedValues, MessageSource messageSource) {
        this.includeRejectedValues = includeRejectedValues;
        this.messageSource = messageSource;
    }

    /**
     * Creates an HTTP 422 problem containing stable, JSON-friendly validation error entries.
     *
     * @param errors the validation errors
     * @return the validation problem detail
     */
    public ProblemDetail create(Errors errors) {
        return create(errors, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    /**
     * Creates a problem for the given status containing stable, JSON-friendly validation error
     * entries. RFC 9457 requires the {@code status} member to match the HTTP status code of the
     * response, so callers that respond with a status other than 422 must pass it here.
     *
     * @param errors the validation errors
     * @param status the status the response is sent with
     * @return the validation problem detail
     */
    public ProblemDetail create(Errors errors, HttpStatusCode status) {
        Objects.requireNonNull(errors, "errors");
        Objects.requireNonNull(status, "status");

        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle("Validation failed");
        problem.setDetail(detailFor(errors.getErrorCount()));

        List<Map<String, Object>> entries = new ArrayList<>(errors.getErrorCount());
        for (ObjectError error : errors.getAllErrors()) {
            entries.add(ValidationErrorEntries.toEntry(error, this.includeRejectedValues, this.messageSource));
        }
        problem.setProperty(ERRORS_PROPERTY, entries);
        return problem;
    }

    private static String detailFor(int errorCount) {
        return errorCount == 1 ?
                "Request validation failed with 1 error." :
                "Request validation failed with " + errorCount + " errors.";
    }
}
