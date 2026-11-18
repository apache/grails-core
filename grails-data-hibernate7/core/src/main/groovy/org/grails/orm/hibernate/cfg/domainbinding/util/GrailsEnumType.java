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

import jakarta.persistence.EnumType;

import org.hibernate.MappingException;
import org.hibernate.mapping.BasicValue;

import org.grails.orm.hibernate.cfg.IdentityEnumType;

public enum GrailsEnumType {
    DEFAULT("default") {
        @Override
        public void configure(BasicValue simpleValue, Class<?> propertyType) {
            STRING.configure(simpleValue, propertyType);
        }
    },
    // Hibernate 7 native string enum mapping: store by Enum.name() as VARCHAR.
    STRING("string") {
        @Override
        public void configure(BasicValue simpleValue, Class<?> propertyType) {
            simpleValue.setImplicitJavaTypeAccess(tc -> propertyType);
            simpleValue.setEnumerationStyle(EnumType.STRING);
        }
    },
    // Hibernate 7 native ordinal enum mapping: store by Enum.ordinal() as INTEGER.
    ORDINAL("ordinal") {
        @Override
        public void configure(BasicValue simpleValue, Class<?> propertyType) {
            simpleValue.setImplicitJavaTypeAccess(tc -> propertyType);
            simpleValue.setEnumerationStyle(EnumType.ORDINAL);
        }
    },
    IDENTITY("identity") {
        @Override
        public void configure(BasicValue simpleValue, Class<?> propertyType) {
            simpleValue.setTypeName(IdentityEnumType.class.getName());
        }
    };

    private final String type;

    GrailsEnumType(String type) {
        this.type = type;
    }

    public static GrailsEnumType fromString(String value) {
        if (value == null || DEFAULT.type.equalsIgnoreCase(value)) {
            return DEFAULT;
        }
        for (GrailsEnumType candidate : values()) {
            if (candidate.type.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new MappingException(
                "Invalid enum type [" + value + "]. Valid values are: default, string, ordinal, identity.");
    }

    public String getType() {
        return type;
    }

    /** Configures the given {@link BasicValue} to store {@code propertyType} per this enum type. */
    public abstract void configure(BasicValue simpleValue, Class<?> propertyType);
}
