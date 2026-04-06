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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import org.hibernate.type.StandardBasicTypes;

public interface HibernateToManyCollectionProperty extends HibernateToManyProperty {

    /**
     * Resolves the Hibernate type name for the map/collection element.
     * Derives the type from the component type when available, falling back to
     * the property type name, and ultimately defaulting to {@code "string"}.
     */
    default String getElementTypeName() {
        Class<?> componentType = getComponentType();
        String typeName = componentType != null ? getTypeName(componentType) : null;
        if (typeName == null) {
            typeName = getTypeName();
        }
        if (typeName == null || typeName.equals(Object.class.getName())) {
            typeName = StandardBasicTypes.STRING.getName();
        }
        return typeName;
    }
}
