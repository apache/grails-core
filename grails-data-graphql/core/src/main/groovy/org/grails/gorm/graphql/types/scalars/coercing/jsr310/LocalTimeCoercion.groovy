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

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Default {@link LocalTime} coercion
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@InheritConstructors
@CompileStatic
class LocalTimeCoercion extends Jsr310Coercion<LocalTime> {

    @Override
    LocalTime parse(String value, String format) {
        LocalTime.parse((CharSequence) value, DateTimeFormatter.ofPattern(format))
    }

    @Override
    Class getTypeClass() {
        LocalTime
    }
}
