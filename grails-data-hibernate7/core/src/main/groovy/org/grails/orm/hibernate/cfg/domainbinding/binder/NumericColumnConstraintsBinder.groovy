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
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.hibernate.dialect.Dialect
import org.hibernate.dialect.H2Dialect
import org.hibernate.mapping.Column

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PropertyConfig

@CompileStatic
@SuppressWarnings('PMD.DataflowAnomalyAnalysis')
class NumericColumnConstraintsBinder {

    private final Dialect dialect

    NumericColumnConstraintsBinder() {
        this(new H2Dialect())
    }

    NumericColumnConstraintsBinder(Dialect dialect) {
        this.dialect = dialect
    }

    void bindNumericColumnConstraints(
            Column column, ColumnConfig cc, PropertyConfig constrainedProperty, Class<?> propertyType) {
        int scale = determineScale(cc, constrainedProperty)
        if (scale > -1) {
            column.scale = scale
        } else {
            scale = org.hibernate.engine.jdbc.Size.DEFAULT_SCALE // Ensure scale is non-negative for calculations
        }
        if (cc != null && cc.precision > -1) {
            column.precision = cc.precision
        } else if (!isApproximateFloatingPoint(propertyType)) {
            int minConstraintValueLength = getConstraintValueLength(constrainedProperty.min, scale)
            int maxConstraintValueLength = getConstraintValueLength(constrainedProperty.max, scale)

            int precision = minConstraintValueLength > 0 && maxConstraintValueLength > 0 ?
                    Math.max(minConstraintValueLength, maxConstraintValueLength) :
                    DefaultGroovyMethods.max([
                        dialect.defaultDecimalPrecision, minConstraintValueLength, maxConstraintValueLength
                    ] as Integer[])
            column.precision = precision
        }
        // else: leave Float/Double precision unset. Hibernate renders FLOAT/DOUBLE DDL as
        // float(precision) where precision is a *bit* count (IEEE-754), converted internally
        // from whatever decimal-digit value column.setPrecision() is given (n * log2(10)). Any
        // decimal-oriented default - Hibernate's own 19/38, or a dialect's getFloatPrecision()/
        // getDoublePrecision(), which are already bit counts and would be converted a second
        // time - overflows H2/PostgreSQL's 53-bit ceiling and produces DDL those dialects reject
        // at execution time (silently: Hibernate only logs the failure, it doesn't throw, so the
        // table is never created - see GH numeric-precision issue). Leaving precision unset lets
        // Hibernate fall back to the dialect's own correct float/double DDL type directly.
    }

    private boolean isApproximateFloatingPoint(Class<?> propertyType) {
        return Float.equals(propertyType) ||
                float.class.equals(propertyType) ||
                Double.equals(propertyType) ||
                double.class.equals(propertyType)
    }

    private int getConstraintValueLength(Comparable<?> min, int scale) {
        if (min instanceof Number) {
            Number number = (Number) min
            return Math.max(countDigits(number), countDigits(number.longValue()) + scale)
        }
        return 0
    }

    private int countDigits(Number number) {
        return Optional.ofNullable(number)
                .map { n -> new BigDecimal(n.toString()).precision() }
                .orElse(0)
    }

    private int determineScale(ColumnConfig cc, PropertyConfig constrainedProperty) {
        if (cc != null && cc.scale > -1) {
            return cc.scale
        }
        if (constrainedProperty != null && constrainedProperty.scale > -1) {
            return constrainedProperty.scale
        }
        return -1
    }

}
