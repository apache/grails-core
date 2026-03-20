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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import java.math.BigDecimal;
import java.util.Optional;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.mapping.Column;

import org.grails.orm.hibernate.cfg.ColumnConfig;
import org.grails.orm.hibernate.cfg.PropertyConfig;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class NumericColumnConstraintsBinder {

    private final Dialect dialect;

    public NumericColumnConstraintsBinder() {
        this(new H2Dialect());
    }

    public NumericColumnConstraintsBinder(Dialect dialect) {
        this.dialect = dialect;
    }

    public void bindNumericColumnConstraints(Column column, ColumnConfig cc, PropertyConfig constrainedProperty) {
        int scale = determineScale(cc, constrainedProperty);
        if (scale > -1) {
            column.setScale(scale);
        } else {
            scale = org.hibernate.engine.jdbc.Size.DEFAULT_SCALE; // Ensure scale is non-negative for calculations
        }
        if (cc != null && cc.getPrecision() > -1) {
            column.setPrecision(cc.getPrecision());
        } else {
            int minConstraintValueLength = getConstraintValueLength(constrainedProperty.getMin(), scale);
            int maxConstraintValueLength = getConstraintValueLength(constrainedProperty.getMax(), scale);

            int defaultPrecision;
            if (dialect instanceof OracleDialect) {
                defaultPrecision = 126;
            } else {
                // Default to 15 decimal digits which maps to ~50-53 bits in Hibernate 7
                // This avoids float(64) DDL errors in H2 and PostgreSQL
                defaultPrecision = 15;
            }

            int precision = minConstraintValueLength > 0 && maxConstraintValueLength > 0 ?
                    Math.max(minConstraintValueLength, maxConstraintValueLength) :
                    DefaultGroovyMethods.max(new Integer[] {
                        defaultPrecision,
                        minConstraintValueLength,
                        maxConstraintValueLength
                    });
            column.setPrecision(precision);
        }
    }

    private int getConstraintValueLength(Comparable min, int scale) {
        return min instanceof Number number ?
                Math.max(countDigits(number), countDigits((number).longValue()) + scale) :
                0;
    }

    private int countDigits(Number number) {
        return Optional.ofNullable(number)
                .map(n -> new BigDecimal(n.toString()).precision())
                .orElse(0);
    }

    private int determineScale(ColumnConfig cc, PropertyConfig constrainedProperty) {
        if (cc != null && cc.getScale() > -1) {
            return cc.getScale();
        }
        if (constrainedProperty != null && constrainedProperty.getScale() > -1) {
            return constrainedProperty.getScale();
        }
        return -1;
    }
}
