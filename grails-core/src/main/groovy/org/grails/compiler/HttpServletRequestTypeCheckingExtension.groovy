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
package org.grails.compiler

import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.transform.stc.GroovyTypeCheckingExtensionSupport

/**
 *
 * @since 3.0.4
 */
class HttpServletRequestTypeCheckingExtension extends GroovyTypeCheckingExtensionSupport.TypeCheckingDSL {

    private dynamicPropertyNames = ['post', 'get', 'xhr']

    @Override
    def run() {
        unresolvedProperty { PropertyExpression expression ->
            def property = expression.property
            if (isConstantExpression(property)) {
                def propertyName = property.value
                if (propertyName in dynamicPropertyNames) {
                    def referenceType = getType(expression.objectExpression)
                    if (referenceType.name == 'jakarta.servlet.http.HttpServletRequest') {
                        return makeDynamic(expression)
                    }
                }
            }
            null
        }
        null
    }
}
