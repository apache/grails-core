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

package org.grails.gorm.graphql.entity.fields

import graphql.schema.GraphQLInputType
import graphql.schema.GraphQLOutputType
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.dsl.helpers.Deprecatable
import org.grails.gorm.graphql.entity.dsl.helpers.Describable
import org.grails.gorm.graphql.entity.dsl.helpers.Named
import org.grails.gorm.graphql.entity.dsl.helpers.Nullable
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Generic class used to represent a field in a custom object. Used
 * in arguments, operations, and custom properties.
 *
 * @param <T> The implementing class
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
abstract class Field<T> implements Named<T>, Describable<T>, Deprecatable<T>, Nullable<T> {

	Object defaultValue
	boolean input = true
	boolean output = true

	T defaultValue(Object defaultValue) {
		this.defaultValue = defaultValue
		(T)this
	}

	T input(boolean input) {
		this.input = input
		(T)this
	}

	T output(boolean output) {
		this.output = output
		(T)this
	}

	abstract GraphQLOutputType getType(GraphQLTypeManager typeManager, MappingContext mappingContext)

	abstract GraphQLInputType getInputType(GraphQLTypeManager typeManager, MappingContext mappingContext)

	void validate() {
		if (name == null) {
            throw new IllegalArgumentException('A name is required for a custom field')
        }
	}
}
