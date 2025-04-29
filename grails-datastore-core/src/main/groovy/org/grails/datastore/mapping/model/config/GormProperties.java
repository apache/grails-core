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
package org.grails.datastore.mapping.model.config;

/**
 * Reserved static property names used by GORM to evaluate GORM-style syntax.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface GormProperties {
    String IDENTITY = "id";
    String PROPERTIES = "properties";
    String TENANT_IDENTITY = "tenantId";
    String ERRORS = "errors";
    String ATTACHED = "attached";
    String CLASS = "class";
    String META_CLASS = "metaClass";
    String DIRTY = "dirty";
    String DIRTY_PROPERTY_NAMES = "dirtyPropertyNames";
    String VERSION = "version";
    String TRANSIENT = "transients";
    String MAPPING_STRATEGY = "mapWith";
    String MAPPED_BY = "mappedBy";
    String BELONGS_TO = "belongsTo";
    String HAS_MANY = "hasMany";
    String HAS_ONE = "hasOne";
    String DATE_CREATED = "dateCreated";
    String MAPPING = "mapping";
    String NAMED_QUERIES = "namedQueries";
    String LAST_UPDATED = "lastUpdated";
    String EMBEDDED = "embedded";
    String CONSTRAINTS = "constraints";
    String DEFAULT_MAPPING_STRATEGY = "GORM";
}
