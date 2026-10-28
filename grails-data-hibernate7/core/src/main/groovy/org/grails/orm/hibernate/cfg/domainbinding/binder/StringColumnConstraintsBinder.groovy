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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import groovy.transform.CompileStatic
import org.hibernate.Length
import org.hibernate.mapping.Column

import org.grails.datastore.mapping.config.Property

@CompileStatic
class StringColumnConstraintsBinder {

    /**
     * Binds a String/byte[] column's length from the property's {@code maxSize}/{@code inList}
     * constraints. When neither is present and the resolved Hibernate type name is {@code text},
     * the column is left unbounded via Hibernate's capacity-dependent DDL type mechanism -
     * {@code Length.LONG32} is the documented way to obtain each dialect's native unbounded string
     * type (text/longtext/varchar(max)/clob) instead of a bounded VARCHAR - see GH-16010.
     *
     * @param column the column to bind the length onto
     * @param mappedForm the property's constraints (maxSize/inList)
     * @param typeName the resolved Hibernate type name, or {@code null} if not relevant
     */
    void bindStringColumnConstraints(Column column, Property mappedForm, String typeName) {
        Number maxSize = mappedForm.maxSize
        Integer number = maxSize != null ? maxSize.intValue() : getMax(mappedForm).orElse(0)
        if (number > 0) {
            column.length = number
        }
        else if (isUnboundedTextType(typeName)) {
            column.length = Length.LONG32
        }
    }

    private static boolean isUnboundedTextType(String typeName) {
        return 'text'.equalsIgnoreCase(typeName)
    }

    private Optional<Integer> getMax(Property mappedForm) {
        List<String> inList = mappedForm.inList
        if (inList == null) {
            return Optional.empty()
        }
        Integer max = null
        for (String value : inList) {
            Integer parsed = parseInt(value)
            if (parsed != null && (max == null || parsed > max)) {
                max = parsed
            }
        }
        return Optional.ofNullable(max)
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value)
        }
        catch (NumberFormatException e) {
            return null
        }
    }

}
