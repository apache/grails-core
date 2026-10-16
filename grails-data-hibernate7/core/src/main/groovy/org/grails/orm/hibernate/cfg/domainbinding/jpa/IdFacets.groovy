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
import org.hibernate.generator.Generator

import org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceGeneratorEnum

/**
 * The identifier generation the domain binder chooses for a root entity: the strategy, the generator class the
 * binder instantiates for it, and the parameters it passes along.
 *
 * <p>{@code parameters} are exactly the identity mapping's {@code params}, never {@code null}.</p>
 *
 * @since 9.0
 */
@CompileStatic
record IdFacets(
    GrailsSequenceGeneratorEnum strategy,
    Class<? extends Generator> generatorClass,
    Map<String, String> parameters) {
}
