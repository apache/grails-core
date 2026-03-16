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

import org.hibernate.MappingException;

public enum GrailsEnumType {
    DEFAULT("default"),
    STRING("string"),
    ORDINAL("ordinal"),
    IDENTITY("identity");

    private final String type;

    GrailsEnumType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
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
}
