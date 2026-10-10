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
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.query.Query

/**
 * Criterion matching a property that equals a value when both are compared in lower case. It is created by
 * {@code eq(property, value, [ignoreCase: true])} in a criteria query. A property that is not a {@link String}
 * is compared with plain equality.
 *
 * @since 8.0.0
 */
@CompileStatic
class EqualsIgnoreCase extends Query.PropertyCriterion {

    EqualsIgnoreCase(String name, Object value) {
        super(name, value)
    }

}
