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
package org.grails.orm.hibernate.query

import groovy.transform.CompileStatic

/**
 * Represents a reference to a persistent property inside a where-DSL closure.
 * Supports Groovy arithmetic operators so that expressions like {@code price * 10}
 * produce a {@link PropertyArithmetic} instead of being evaluated as a literal.
 */
@CompileStatic
class PropertyReference {

    final String propertyName

    PropertyReference(String propertyName) {
        this.propertyName = propertyName
    }

    PropertyArithmetic multiply(Number operand) {
        new PropertyArithmetic(propertyName, PropertyArithmetic.Operator.MULTIPLY, operand)
    }

    PropertyArithmetic plus(Number operand) {
        new PropertyArithmetic(propertyName, PropertyArithmetic.Operator.ADD, operand)
    }

    PropertyArithmetic minus(Number operand) {
        new PropertyArithmetic(propertyName, PropertyArithmetic.Operator.SUBTRACT, operand)
    }

    PropertyArithmetic div(Number operand) {
        new PropertyArithmetic(propertyName, PropertyArithmetic.Operator.DIVIDE, operand)
    }
}
