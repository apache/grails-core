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

package org.grails.gorm.graphql.entity.dsl.helpers

import groovy.transform.CompileStatic
import org.grails.gorm.graphql.Schema

/**
 * Decorates a class with a builder syntax to provide
 * deprecation data.
 *
 * @param <T> The implementing class
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait Deprecatable<T> {

    boolean deprecated = false
    String deprecationReason

    T deprecated(boolean deprecated) {
        this.deprecated = deprecated
        (T)this
    }

    T deprecationReason(String deprecationReason) {
        this.deprecationReason = deprecationReason
        (T)this
    }

    String getDeprecationReason() {
        deprecationReason ?: (deprecated ? Schema.DEFAULT_DEPRECATION_REASON : null)
    }

}
