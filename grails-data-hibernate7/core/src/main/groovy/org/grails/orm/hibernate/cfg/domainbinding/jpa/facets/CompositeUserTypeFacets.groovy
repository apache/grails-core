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
package org.grails.orm.hibernate.cfg.domainbinding.jpa.facets

import groovy.transform.CompileStatic

/**
 * A property mapped with a Hibernate {@code CompositeUserType}, as the domain binder binds it ({@code CompositeUserTypeBinder}):
 * a component of the user type whose properties are the attributes of the user type's {@code embeddable()} class, one column each,
 * in alphabetical attribute order. The generator states it as an {@code @Embedded} field of the value class with
 * {@code @CompositeType} and an {@code @AttributeOverride} per attribute.
 *
 * @since 9.0
 */
@CompileStatic
record CompositeUserTypeFacets(
    Class<?> userType,
    Class<?> embeddable,
    List<CompositeAttributeFacets> attributes) {
}
