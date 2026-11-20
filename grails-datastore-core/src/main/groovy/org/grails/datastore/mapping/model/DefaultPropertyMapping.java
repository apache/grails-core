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

import org.grails.datastore.mapping.config.Property;

/**
 * Default implementation of the {@link PropertyMapping} interface
 *
 * @param <T> The mapped form type
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultPropertyMapping<T extends Property> implements PropertyMapping<T> {

    private final ClassMapping classMapping;
    private final T mappedForm;

    public DefaultPropertyMapping(ClassMapping classMapping, T mappedForm) {
        this.classMapping = classMapping;
        this.mappedForm = mappedForm;
    }

    @Override
    public ClassMapping getClassMapping() {
        return classMapping;
    }

    @Override
    public T getMappedForm() {
        return mappedForm;
    }
}
