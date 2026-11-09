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
package org.apache.grails.data.neo4j.core

import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.springframework.validation.Validator

/**
 * Base Spock spec for Neo4j GORM tests.
 *
 * Subclasses register only the domain classes they need via their own {@code setupSpec},
 * calling {@code manager.registerDomainClasses(...)} - the modern replacement for the old
 * {@code grails.gorm.tests.GormDatastoreSpec#getDomainClasses()} override. Subclasses that need
 * non-default datastore configuration (e.g. {@code grails.gorm.markDirty: false}) override
 * {@link #getConfiguration()}, mirroring the old base spec's override point.
 */
abstract class Neo4jGormDatastoreSpec extends GrailsDataTckSpec<GrailsDataNeo4jTckManager> {

    void setupSpec() {
        manager.configuration = getConfiguration()
    }

    Map getConfiguration() {
        [:]
    }

    void setupValidator(Class entityClass, Validator validator = null) {
        manager.setupValidator(entityClass, validator)
    }
}
