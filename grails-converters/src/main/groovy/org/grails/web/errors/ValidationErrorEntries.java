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
package org.grails.web.errors;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * Builds the JSON-friendly entry for one validation error.
 *
 * <p>Shared so that the {@code errors} extension of an RFC 9457 problem and a directly serialized
 * {@code Errors} object describe a failure the same way. Two shapes for the same concept in one
 * response format is a wire-compatibility hazard, so both paths build entries here.</p>
 *
 * @since 8.0
 */
public final class ValidationErrorEntries {

    private ValidationErrorEntries() {
    }

    /**
     * @param error the validation error
     * @param includeRejectedValue whether to include the submitted value, which may be sensitive
     * @return a stable, ordered entry describing the error
     */
    public static Map<String, Object> toEntry(ObjectError error, boolean includeRejectedValue) {
        return toEntry(error, includeRejectedValue, null);
    }

    /**
     * @param error the validation error
     * @param includeRejectedValue whether to include the submitted value, which may be sensitive
     * @param messageSource resolves the message for the current locale; when null the error's
     * default message is used verbatim, which still contains its {@code {0}} argument placeholders
     * @return a stable, ordered entry describing the error
     */
    public static Map<String, Object> toEntry(ObjectError error, boolean includeRejectedValue,
            MessageSource messageSource) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("object", error.getObjectName());
        if (error instanceof FieldError fieldError) {
            entry.put("field", fieldError.getField());
            if (includeRejectedValue) {
                entry.put("rejectedValue", fieldError.getRejectedValue());
            }
        }
        String[] codes = error.getCodes();
        entry.put("codes", codes == null ? List.of() : Arrays.asList(codes));
        entry.put("message", resolveMessage(error, messageSource));
        return entry;
    }

    private static String resolveMessage(ObjectError error, MessageSource messageSource) {
        if (messageSource == null) {
            return error.getDefaultMessage();
        }
        try {
            // Resolves the error's codes, and substitutes the arguments into whichever message
            // wins -- including the default message, which is a template until this runs.
            return messageSource.getMessage(error, LocaleContextHolder.getLocale());
        }
        catch (NoSuchMessageException ignored) {
            return error.getDefaultMessage();
        }
    }
}
