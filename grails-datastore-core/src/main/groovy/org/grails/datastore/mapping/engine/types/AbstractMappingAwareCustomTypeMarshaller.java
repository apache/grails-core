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
package org.grails.datastore.mapping.engine.types;

import org.springframework.dao.InvalidDataAccessResourceUsageException;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.engine.internal.MappingUtils;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.query.Query;

/**
 * Abstract implementation of CustomTypeMarshaller interface that handles the details of getting the correct mapped key for a property
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractMappingAwareCustomTypeMarshaller<T, N, Q> implements CustomTypeMarshaller<T, N, Q>{

    private Class<T> targetType;

    public AbstractMappingAwareCustomTypeMarshaller(Class<T> targetType) {
        this.targetType = targetType;
    }

    @Override
    public boolean supports(Datastore datastore) {
        return true;
    }

    public boolean supports(MappingContext context) {
        return true;
    }

    public Class<T> getTargetType() {
        return this.targetType;
    }

    public Object write(PersistentProperty property, T value, N nativeTarget) {
        String targetName = MappingUtils.getTargetKey(property);
        return writeInternal(property, targetName,value,nativeTarget);
    }

    protected abstract Object writeInternal(PersistentProperty property, String key, T value, N nativeTarget);

    public Q query(PersistentProperty property, Query.PropertyCriterion criterion, Q nativeQuery) {
        String targetName = MappingUtils.getTargetKey(property);
        queryInternal(property, targetName, criterion, nativeQuery);
        return nativeQuery;
    }

    protected void queryInternal(PersistentProperty property, String key, Query.PropertyCriterion value, Q nativeQuery) {
        throw new InvalidDataAccessResourceUsageException("Custom type [" + getTargetType().getName() +
                "] does not support querying");
    }

    public T read(PersistentProperty property, N source) {
        String targetName = MappingUtils.getTargetKey(property);
        return readInternal(property, targetName, source);
    }

    protected abstract T readInternal(PersistentProperty property, String key, N nativeSource);
}
