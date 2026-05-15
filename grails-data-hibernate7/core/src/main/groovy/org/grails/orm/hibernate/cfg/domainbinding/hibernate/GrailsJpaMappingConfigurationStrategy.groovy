/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import groovy.transform.CompileStatic

import org.springframework.validation.Errors

import org.grails.datastore.mapping.model.MappingFactory
import org.grails.datastore.mapping.model.config.JpaMappingConfigurationStrategy

/**
 * A {@link JpaMappingConfigurationStrategy} for Grails/Hibernate that excludes
 * Spring {@link Errors} from being treated as custom types.
 */
@CompileStatic
class GrailsJpaMappingConfigurationStrategy extends JpaMappingConfigurationStrategy {

    GrailsJpaMappingConfigurationStrategy(MappingFactory propertyFactory) {
        super(propertyFactory)
    }

    @Override
    protected boolean supportsCustomType(Class<?> propertyType) {
        !Errors.isAssignableFrom(propertyType)
    }
}
