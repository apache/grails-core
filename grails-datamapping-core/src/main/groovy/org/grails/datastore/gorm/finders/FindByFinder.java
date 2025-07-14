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
package org.grails.datastore.gorm.finders;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.MappingContext;

import java.util.regex.Pattern;

/**
 * Finder used to return a single result
 */
public class FindByFinder extends AbstractFindByFinder {

    private static final String METHOD_PATTERN = "(findBy)([A-Z]\\w*)";

    public FindByFinder(final Datastore datastore) {
        super(Pattern.compile(METHOD_PATTERN), datastore);
    }

    public FindByFinder(MappingContext mappingContext) {
        super(Pattern.compile(METHOD_PATTERN), mappingContext);
    }
}
