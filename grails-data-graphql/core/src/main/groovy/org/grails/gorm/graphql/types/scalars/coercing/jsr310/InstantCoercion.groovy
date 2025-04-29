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

package org.grails.gorm.graphql.types.scalars.coercing.jsr310

import graphql.language.IntValue
import graphql.language.StringValue
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.time.Instant

/**
 * Default {@link Instant} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@InheritConstructors
@CompileStatic
class InstantCoercion implements Coercing<Instant, Instant> {

    protected Optional<Instant> convert(Object input) {
        if (input instanceof Instant) {
            Optional.of((Instant) input)
        }
        else if (input instanceof Number || input instanceof String) {
            parseInstant(input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    Instant serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to an Instant")
        }
    }

    @Override
    Instant parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to an Instant")
        }
    }

    @Override
    Instant parseLiteral(Object input) {

        if (input instanceof Value) {
            Object value
            if (input instanceof IntValue) {
                value = ((IntValue) input).value.longValue()
            }
            else if (input instanceof StringValue) {
                value = ((StringValue) input).value
            }
            parseInstant(value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<Instant> parseInstant(Object input) {
        if (input instanceof Number) {
            Optional.of(Instant.ofEpochSecond(((Number) input).longValue()))
        }
        else if (input instanceof String) {
            Optional.of(Instant.parse((String) input))
        }
        else {
            Optional.empty()
        }
    }

}
