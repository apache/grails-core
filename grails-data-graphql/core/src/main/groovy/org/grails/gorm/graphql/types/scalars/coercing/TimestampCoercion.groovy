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

package org.grails.gorm.graphql.types.scalars.coercing

import graphql.language.IntValue
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

import java.sql.Timestamp

/**
 * Default {@link Timestamp} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class TimestampCoercion implements Coercing<Timestamp, Timestamp> {

    protected Optional<Timestamp> convert(Object input) {
        if (input instanceof Timestamp) {
            Optional.of((Timestamp) input)
        }
        else if (input instanceof String) {
            parseTimestamp((String) input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    Timestamp serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a java.sql.Timestamp")
        }
    }

    @Override
    Timestamp parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a java.sql.Timestamp")
        }
    }

    @Override
    Timestamp parseLiteral(Object input) {
        if (input instanceof IntValue) {
            new Timestamp(((IntValue) input).value.longValue())
        }
        else if (input instanceof StringValue) {
            parseTimestamp(((StringValue) input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<Timestamp> parseTimestamp(String input) {
        try {
            Optional.of(Timestamp.valueOf(input))
        } catch (Exception e) {
            Optional.empty()
        }
    }
}
