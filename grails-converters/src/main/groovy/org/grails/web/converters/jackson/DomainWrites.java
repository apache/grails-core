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
package org.grails.web.converters.jackson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Decisions that the writers of a domain class instance share.
 *
 * @since 9.0
 */
final class DomainWrites {

    private DomainWrites() {
    }

    /**
     * A copy of an association's collection, which loads it and keeps its order.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static Object copied(Object value) {
        if (value instanceof SortedMap map) {
            return new TreeMap(map);
        }
        if (value instanceof SortedSet set) {
            return new TreeSet(set);
        }
        if (value instanceof Set set) {
            return new LinkedHashSet(set);
        }
        if (value instanceof Map map) {
            return new LinkedHashMap(map);
        }
        if (value instanceof Collection collection) {
            return new ArrayList(collection);
        }
        return value;
    }
}
