/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.converters.jackson;

import java.util.function.Supplier;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import org.springframework.context.MessageSource;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;

import org.grails.web.errors.ValidationErrorEntries;

/** Serializes Spring validation errors without exposing internal binding state. */
final class SpringErrorsJsonSerializer extends ValueSerializer<Errors> {

    private final Supplier<MessageSource> messageSource;

    SpringErrorsJsonSerializer(Supplier<MessageSource> messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public void serialize(Errors errors, JsonGenerator generator, SerializationContext context) throws JacksonException {
        generator.writeStartObject();
        generator.writeName("errors");
        generator.writeStartArray();
        for (ObjectError error : errors.getAllErrors()) {
            // Rejected values are never included here: this path serializes whatever Errors object
            // reaches the mapper, with no opportunity for an application to opt in.
            context.writeValue(generator, ValidationErrorEntries.toEntry(error, false, messageSource.get()));
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }
}
