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
package org.grails.core.artefact;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;

/**
 * Detects annotated domain classes for EJB3 style mappings.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class AnnotationDomainClassArtefactHandler extends DomainClassArtefactHandler {

    private static final String JPA_MAPPING_STRATEGY = "JPA";

    private Set<String> jpaClassNames = new HashSet<String>();

    public Set<String> getJpaClassNames() {
        return jpaClassNames;
    }

    @Override
    public boolean isArtefactClass(@SuppressWarnings("rawtypes") Class clazz) {
        final boolean isJpaDomainClass = isJPADomainClass(clazz);
        if (isJpaDomainClass) {
            jpaClassNames.add(clazz.getName());
        }
        return super.isArtefactClass(clazz);
    }

    public static boolean isJPADomainClass(Class<?> clazz) {
        return clazz != null && clazz.getAnnotation(Entity.class) != null;
    }
}
