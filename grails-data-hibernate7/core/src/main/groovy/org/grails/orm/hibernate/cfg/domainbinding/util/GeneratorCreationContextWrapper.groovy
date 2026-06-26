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
package org.grails.orm.hibernate.cfg.domainbinding.util

import groovy.transform.CompileStatic
import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.model.relational.SqlStringGenerationContext
import org.hibernate.generator.GeneratorCreationContext
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Value
import org.hibernate.service.ServiceRegistry
import org.hibernate.type.Type

/**
 * A wrapper for {@link GeneratorCreationContext} that allows overriding the {@link Value}.
 */
@CompileStatic
class GeneratorCreationContextWrapper implements GeneratorCreationContext {

    private final GeneratorCreationContext delegate
    private final Value value

    GeneratorCreationContextWrapper(GeneratorCreationContext delegate, Value value) {
        this.delegate = delegate
        this.value = value
    }

    @Override Database getDatabase() { delegate.database }
    @Override ServiceRegistry getServiceRegistry() { delegate.serviceRegistry }
    @Override String getDefaultCatalog() { delegate.defaultCatalog }
    @Override String getDefaultSchema() { delegate.defaultSchema }
    @Override PersistentClass getPersistentClass() { delegate.persistentClass }
    @Override RootClass getRootClass() { delegate.rootClass }
    @Override Property getProperty() { delegate.getProperty() }
    @Override Type getType() { delegate.type }
    @Override SqlStringGenerationContext getSqlStringGenerationContext() { delegate.sqlStringGenerationContext }

    @Override
    Value getValue() {
        value != null ? value : delegate.getValue()
    }
}
