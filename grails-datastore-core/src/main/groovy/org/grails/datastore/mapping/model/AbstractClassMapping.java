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
package org.grails.datastore.mapping.model;

import org.grails.datastore.mapping.config.Entity;

/**
 * Abstract implementation of the ClassMapping interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractClassMapping<T extends Entity> implements ClassMapping {

    protected PersistentEntity entity;
    protected MappingContext context;
    private IdentityMapping identifierMapping;

    public AbstractClassMapping(PersistentEntity entity, MappingContext context) {
        this.entity = entity;
        this.context = context;
        MappingConfigurationStrategy syntaxStrategy = context.getMappingSyntaxStrategy();
        this.identifierMapping = syntaxStrategy.getIdentityMapping(this);
    }

    public PersistentEntity getEntity() {
        return entity;
    }

    public abstract T getMappedForm();

    public IdentityMapping getIdentifier() {
        return identifierMapping;
    }
}
