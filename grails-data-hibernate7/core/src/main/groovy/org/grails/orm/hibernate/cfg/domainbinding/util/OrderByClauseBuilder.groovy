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
package org.grails.orm.hibernate.cfg.domainbinding.util

import groovy.transform.CompileStatic
import org.hibernate.boot.model.internal.BinderHelper
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.SingleTableSubclass
import org.hibernate.mapping.Selectable

import org.grails.datastore.mapping.model.DatastoreConfigurationException

/** Utility class to build SQL order by clauses from HQL-style order by strings. */
@CompileStatic
@SuppressWarnings('PMD.DataflowAnomalyAnalysis')
class OrderByClauseBuilder {

    String buildOrderByClause(
            String hqlOrderBy, PersistentClass associatedClass, String role, String defaultOrder) {
        if (hqlOrderBy == null) {
            return null
        }

        if (hqlOrderBy.isEmpty()) {
            List<String> parts = []
            for (Selectable selectable : associatedClass.identifier.selectables) {
                parts.add(selectable.text + ' asc')
            }
            return parts.join(', ')
        }

        List<SortEntry> entries = parseSortEntries(hqlOrderBy, role, defaultOrder)

        List<String> parts = []
        for (SortEntry entry : entries) {
            parts.add(buildPropertyOrderBy(entry, associatedClass))
        }
        return parts.join(', ')
    }

    private List<SortEntry> parseSortEntries(String hqlOrderBy, String role, String defaultOrder) {
        String[] tokens = hqlOrderBy.split('[ ,]+')
        List<SortEntry> entries = new ArrayList<>()
        SortEntry currentEntry = null

        for (String token : tokens) {
            if (token.isEmpty()) {
                continue
            }

            if (isDirectionToken(token)) {
                if (currentEntry == null || currentEntry.direction != null) {
                    throw new DatastoreConfigurationException(
                            "Error while parsing sort clause: ${hqlOrderBy} (${role})".toString())
                }
                currentEntry.direction = token.toLowerCase(Locale.ROOT)
            }
            else {
                if (currentEntry != null && currentEntry.direction == null) {
                    currentEntry.direction = 'asc'
                }
                currentEntry = new SortEntry(token)
                entries.add(currentEntry)
            }
        }

        if (currentEntry != null && currentEntry.direction == null) {
            currentEntry.direction = defaultOrder
        }

        return entries
    }

    private String buildPropertyOrderBy(SortEntry entry, PersistentClass associatedClass) {
        Property p = BinderHelper.findPropertyByName(associatedClass, entry.property)
        if (p == null) {
            throw new DatastoreConfigurationException(
                    "property from sort clause not found: ${associatedClass.entityName}.${entry.property}".toString())
        }

        String tablePrefix = getTablePrefix(p, associatedClass)
        String direction = entry.direction

        List<String> parts = []
        for (Selectable selectable : p.selectables) {
            parts.add(tablePrefix + selectable.text + ' ' + direction)
        }
        return parts.join(', ')
    }

    private String getTablePrefix(Property p, PersistentClass associatedClass) {
        PersistentClass pc = p.persistentClass
        if (pc == null ||
                pc == associatedClass ||
                (associatedClass instanceof SingleTableSubclass &&
                        pc.mappedClass.isAssignableFrom(associatedClass.mappedClass))) {
            return ''
        }
        return pc.table.quotedName + '.'
    }

    private boolean isDirectionToken(String token) {
        return 'asc'.equalsIgnoreCase(token) || 'desc'.equalsIgnoreCase(token)
    }

    private static class SortEntry {

        final String property
        String direction

        SortEntry(String property) {
            this.property = property
        }

    }

}
