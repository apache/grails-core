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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.io.Serial;
import java.util.Map;

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

@SuppressWarnings("PMD.NonSerializableClass")
public class MapSecondPass implements org.hibernate.boot.spi.SecondPass, GrailsSecondPass, java.io.Serializable {
    @Serial
    private static final long serialVersionUID = -3244991685626409031L;

    private final MapSecondPassBinder mapSecondPassBinder;
    protected final HibernateToManyProperty property;

    public MapSecondPass(
            MapSecondPassBinder mapSecondPassBinder,
            HibernateToManyProperty property) {
        this.mapSecondPassBinder = mapSecondPassBinder;
        this.property = property;
    }

    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        mapSecondPassBinder.bindMapSecondPass(
                property, persistentClasses, (org.hibernate.mapping.Map) property.getCollection());
        createCollectionKeys(property.getCollection());
    }
}
