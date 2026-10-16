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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import groovy.transform.CompileStatic

/**
 * The explicit Hibernate type the domain binder gives a basic property, in the two forms an annotated class can
 * state it: a {@code UserType} class with its parameters ({@code @Type}), the JDBC type of a registered legacy
 * type name such as {@code text} ({@code @JdbcTypeCode}), or the converter of a registered type name that converts its
 * value, such as {@code yes_no} ({@code @Convert} together with the {@code @JdbcTypeCode} of the registered type, which can differ
 * from the one the converter alone resolves to: {@code numeric_boolean} is a TINYINT, the converter alone an INTEGER).
 *
 * <p>A converter named by its class ({@code type: 'org.hibernate.type.YesNoConverter'}) is {@code converter} alone: no {@code userType} and no
 * {@code jdbcTypeCode}, which the converter resolves itself. Otherwise exactly one of {@code userType} and {@code jdbcTypeCode} is set, and
 * {@code converter} only next to {@code jdbcTypeCode}. {@code parameters} is only meaningful for a user type and is never {@code null}.
 * {@code javaType} is the Java type the generated field has when it is not the property's own: the type a registered type maps for an
 * identifier (its value comes from a generator, so the mapping may name a type for a value the property's class does not hold), the
 * interface of a registered type the property's class implements ({@code serializable}), or the type a converter converts when the property's
 * class is not one it accepts.</p>
 *
 * @since 9.0
 */
@CompileStatic
record TypeFacets(
    Class<?> userType,
    Integer jdbcTypeCode,
    Map<String, String> parameters,
    Class<?> javaType = null,
    Class<?> converter = null) {
}
