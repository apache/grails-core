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

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import groovy.transform.CompileStatic

/**
 * Default {@link UUID} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class UUIDCoercion implements Coercing<UUID, UUID> {

    protected Optional<UUID> convert(Object input) {
        if (input instanceof UUID) {
            Optional.of((UUID) input)
        }
        else if (input instanceof String) {
            parseUUID((String) input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    UUID serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a java.util.UUID")
        }
    }

    @Override
    UUID parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a java.util.UUID")
        }
    }

    @Override
    UUID parseLiteral(Object input) {
        if (input instanceof StringValue) {
            parseUUID(((StringValue)input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<UUID> parseUUID(String value) {
        try {
            Optional.of(UUID.fromString(value))
        } catch (Exception e) {
            Optional.empty()
        }
    }
}
