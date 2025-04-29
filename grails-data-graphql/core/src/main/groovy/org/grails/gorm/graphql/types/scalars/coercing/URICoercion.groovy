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
 * Default {@link URI} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class URICoercion implements Coercing<URI, URI> {

    protected Optional<URI> convert(Object input) {
        if (input instanceof URI) {
            Optional.of((URI) input)
        }
        else if (input instanceof String) {
            parseURI((String) input)
        }
        else {
            Optional.empty()
        }
    }

    @Override
    URI serialize(Object input) {
        convert(input).orElseThrow {
            throw new CoercingSerializeException("Could not convert ${input.class.name} to a java.net.URI")
        }
    }

    @Override
    URI parseValue(Object input) {
        convert(input).orElseThrow {
            throw new CoercingParseValueException("Could not convert ${input.class.name} to a java.net.URI")
        }
    }

    @Override
    URI parseLiteral(Object input) {
        if (input instanceof StringValue) {
            parseURI(((StringValue)input).value).orElse(null)
        }
        else {
            null
        }
    }

    protected Optional<URI> parseURI(String value) {
        try {
            Optional.of(new URI(value))
        } catch (Exception e) {
            Optional.empty()
        }
    }
}
