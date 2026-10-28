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
package org.grails.orm.hibernate.query;

/**
 * Represents a property path combined with a scalar arithmetic operand,
 * e.g. {@code price * 10} in a where-DSL expression.
 * <p>
 * At query-build time {@link PredicateGenerator} resolves this into the
 * appropriate JPA {@code CriteriaBuilder} arithmetic expression
 * ({@code cb.prod}, {@code cb.sum}, {@code cb.diff}, {@code cb.quot}).
 */
public record PropertyArithmetic(String propertyName, Operator operator, Number operand) {

    public enum Operator {
        MULTIPLY, ADD, SUBTRACT, DIVIDE
    }

}
