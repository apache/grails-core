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

package myapp

import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import org.bson.types.ObjectId
import org.grails.gorm.graphql.plugin.GraphQLPostProcessor
import org.grails.gorm.graphql.types.GraphQLTypeManager

class MyGraphQLCustomizer extends GraphQLPostProcessor {

    @Override
    void doWith(GraphQLTypeManager typeManager) {
        typeManager.registerType(ObjectId, GraphQLScalarType.newScalar()
                .name("ObjectId").description("Hex representation of a Mongo object id").coercing(new Coercing<ObjectId, ObjectId>() {

            protected Optional<ObjectId> convert(Object input) {
                if (input instanceof ObjectId) {
                    Optional.of((ObjectId) input)
                } else if (input instanceof String) {
                    parseObjectId((String) input)
                } else {
                    Optional.empty()
                }
            }

            @Override
            ObjectId serialize(Object input) {
                convert(input).orElseThrow({
                    throw new CoercingSerializeException("Could not convert ${input.class.name} to an ObjectId")
                })
            }

            @Override
            ObjectId parseValue(Object input) {
                convert(input).orElseThrow({
                    throw new CoercingParseValueException("Could not convert ${input.class.name} to an ObjectId")
                })
            }

            @Override
            ObjectId parseLiteral(Object input) {
                if (input instanceof StringValue) {
                    parseObjectId(((StringValue) input).value).orElse(null)
                } else {
                    null
                }
            }

            protected Optional<ObjectId> parseObjectId(String input) {
                if (ObjectId.isValid(input)) {
                    Optional.of(new ObjectId(input))
                } else {
                    Optional.empty()
                }
            }

        }).build())
    }
}
