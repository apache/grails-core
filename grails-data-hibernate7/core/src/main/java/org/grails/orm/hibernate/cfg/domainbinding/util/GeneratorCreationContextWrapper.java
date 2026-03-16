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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Value;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

/**
 * A wrapper for {@link GeneratorCreationContext} that allows overriding the {@link Value}.
 */
public class GeneratorCreationContextWrapper implements GeneratorCreationContext {

    private final GeneratorCreationContext delegate;
    private final Value value;

    public GeneratorCreationContextWrapper(GeneratorCreationContext delegate, Value value) {
        this.delegate = delegate;
        this.value = value;
    }

    @Override
    public Database getDatabase() {
        return delegate.getDatabase();
    }

    @Override
    public ServiceRegistry getServiceRegistry() {
        return delegate.getServiceRegistry();
    }

    @Override
    public String getDefaultCatalog() {
        return delegate.getDefaultCatalog();
    }

    @Override
    public String getDefaultSchema() {
        return delegate.getDefaultSchema();
    }

    @Override
    public PersistentClass getPersistentClass() {
        return delegate.getPersistentClass();
    }

    @Override
    public RootClass getRootClass() {
        return delegate.getRootClass();
    }

    @Override
    public Property getProperty() {
        return delegate.getProperty();
    }

    @Override
    public Value getValue() {
        return value != null ? value : delegate.getValue();
    }

    @Override
    public Type getType() {
        return delegate.getType();
    }

    @Override
    public SqlStringGenerationContext getSqlStringGenerationContext() {
        return delegate.getSqlStringGenerationContext();
    }
}
