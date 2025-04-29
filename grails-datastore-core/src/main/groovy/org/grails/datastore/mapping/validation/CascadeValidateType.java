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
package org.grails.datastore.mapping.validation;

/**
 * Defines a set of cascade validation options that are propagated to the associated entity.
 */
public enum CascadeValidateType {
    /** By default, cascade for any owned association or with a JPA cascade of PERSIST or MERGE **/
    DEFAULT,

    /** Don't cascade validations at all, only entities that are actually flushed will be validated (similar to deepValidate: false) **/
    NONE,

    /** Only cascade validation for associations which are owned by the parent entity, regardless of the JPA cascade behavior **/
    OWNED,

    /** In addition to the default cascade requirement, only cascade to entities that are DirtyCheckable and hasChanged **/
    DIRTY;

    public static CascadeValidateType fromMappedName(String name) {
        return CascadeValidateType.valueOf(name.toUpperCase());
    }
}
