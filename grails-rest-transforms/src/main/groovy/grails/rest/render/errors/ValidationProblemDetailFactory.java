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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
 * {@link #ValidationProblemDetailFactory(boolean, MessageSource)}, which resolves the messages through the
 * application's {@code MessageSource}.</p>
 *
 * @since 9.0
 */
public final class ValidationProblemDetailFactory {

    public static final String ERRORS_PROPERTY = "errors";

    /**
     * The message code of the problem's title, which the {@code MessageSource} resolves for the current locale, and
     * {@code Validation failed} otherwise.
     */
    public static final String TITLE_MESSAGE_CODE = "grails.validation.problem.title";

    /**
     * The message code of the problem's detail, which the {@code MessageSource} resolves for the current locale with the
     * number of errors as its argument, and {@code Request validation failed with 2 errors.} otherwise.
     */
    public static final String DETAIL_MESSAGE_CODE = "grails.validation.problem.detail";

    private static final String DEFAULT_TITLE = "Validation failed";

    // {0,number,0} rather than {0,number,#}: the choice format of Java 17 and 21 reads a # in a nested format as a limit
    private static final String DEFAULT_DETAIL =
            "Request validation failed with {0,choice,0#0 errors|1#1 error|1<{0,number,0} errors}.";

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
        Locale locale = LocaleContextHolder.getLocale();
        problem.setTitle(message(TITLE_MESSAGE_CODE, new Object[0], DEFAULT_TITLE, locale));
        problem.setDetail(message(DETAIL_MESSAGE_CODE, new Object[] {errors.getErrorCount()}, DEFAULT_DETAIL, locale));

        List<Map<String, Object>> entries = new ArrayList<>(errors.getErrorCount());
        for (ObjectError error : errors.getAllErrors()) {
            entries.add(ValidationErrorEntries.toEntry(error, this.includeRejectedValues, this.messageSource));
        }
        problem.setProperty(ERRORS_PROPERTY, entries);
        return problem;
    }

    /**
     * The message of the code for the locale, or else the default message, formatted with the arguments: in English, as
     * the default message is, so that its numbers are written in the digits of its words whatever the locale.
     */
    private String message(String code, Object[] arguments, String defaultMessage, Locale locale) {
        if (messageSource != null) {
            String message = messageSource.getMessage(code, arguments, null, locale);
            if (message != null) {
                return message;
            }
        }
        return new MessageFormat(defaultMessage, Locale.ENGLISH).format(arguments);
    }
}
