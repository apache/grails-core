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
package org.grails.datastore.mapping.query.criteria;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.dao.InvalidDataAccessResourceUsageException;

import org.grails.datastore.mapping.query.Query;

/**
 * This criterion calls a function on the property before apply the appropriate comparison.
 *
 * Example in SQL: year(date) == 2007 where the function is 'year', the property 'date' and the value is '2007'
 */
public class FunctionCallingCriterion extends Query.PropertyNameCriterion {

    /**
     * The functions a where query can call on a property: the ten that the where DSL accepts. A query that runs
     * a function by name, as the bulk statements of {@code updateAll} and {@code deleteAll} and Hibernate 7 do,
     * accepts no other name, so that the name is never text that changes the query.
     */
    public static final Set<String> SUPPORTED_FUNCTIONS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "lower", "upper", "trim", "length", "second", "hour", "minute", "day", "month", "year")));

    /**
     * Rejects a function name that the where DSL does not support.
     *
     * @param functionName the name of the function
     * @throws InvalidDataAccessResourceUsageException when the name is not one of {@link #SUPPORTED_FUNCTIONS}
     */
    public static void requireSupported(String functionName) {
        if (!SUPPORTED_FUNCTIONS.contains(functionName)) {
            throw new InvalidDataAccessResourceUsageException("Unsupported function defined in query [" + functionName +
                    "]. The functions supported are " + SUPPORTED_FUNCTIONS);
        }
    }

    private static final BigDecimal INT_MIN = BigDecimal.valueOf(Integer.MIN_VALUE);
    private static final BigDecimal INT_MAX = BigDecimal.valueOf(Integer.MAX_VALUE);

    /**
     * Whether a number is a whole number that an int holds exactly. The functions a where query can call return an
     * int, so such a number can be compared as an int. Any other number, a fraction or one that is too large, has
     * to be compared as it is: narrowing it changes which rows match. The test is exact, so a decimal a hair past
     * a whole number is not the whole number.
     *
     * @param number the operand of a comparison with a function
     */
    public static boolean isWholeInt(Number number) {
        if (number instanceof Integer || number instanceof Short || number instanceof Byte) {
            return true;
        }
        if (number instanceof Long) {
            long value = number.longValue();
            return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
        }
        if (number instanceof BigInteger) {
            return ((BigInteger) number).bitLength() < Integer.SIZE;
        }
        BigDecimal exact;
        if (number instanceof BigDecimal) {
            exact = (BigDecimal) number;
        }
        else {
            double value = number.doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return false;
            }
            exact = new BigDecimal(value);
        }
        return exact.signum() == 0 ||
                exact.stripTrailingZeros().scale() <= 0 && exact.compareTo(INT_MIN) >= 0 && exact.compareTo(INT_MAX) <= 0;
    }

    /**
     * Whether an operand is a number that {@link #isWholeInt} rejects.
     *
     * @param value the operand of a comparison with a function
     */
    public static boolean isFractional(Object value) {
        return value instanceof Number && !isWholeInt((Number) value);
    }

    private String functionName;
    private Query.PropertyCriterion propertyCriterion;
    private boolean onValue;

    public FunctionCallingCriterion(String functionName, Query.PropertyCriterion propertyCriterion) {
        super(propertyCriterion.getProperty());
        this.functionName = functionName;
        this.propertyCriterion = propertyCriterion;
    }

    public FunctionCallingCriterion(String name, String functionName, Query.PropertyCriterion propertyCriterion, boolean onValue) {
        super(name);
        this.functionName = functionName;
        this.propertyCriterion = propertyCriterion;
        this.onValue = onValue;
    }

    public String getFunctionName() {
        return functionName;
    }

    public Query.PropertyCriterion getPropertyCriterion() {
        return propertyCriterion;
    }

    /**
     * Whether the function is called on the value or on the property
     *
     */
    public boolean isOnValue() {
        return onValue;
    }
}
