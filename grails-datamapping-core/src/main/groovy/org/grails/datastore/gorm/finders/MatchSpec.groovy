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

package org.grails.datastore.gorm.finders

import groovy.transform.CompileStatic

/**
 * A match spec details a matched finder
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class MatchSpec {
    /**
     * The full method name
     */
    final String methodName

    /**
     * The prefix (for example "findBy")
     */
    final String prefix
    /**
     * The query expression without the prefix i.e. methodName - prefix
     */
    final String queryExpression
    /**
     * The required arguments
     */
    final int requiredArguments
    /**
     * The method call expressions
     */
    final List<MethodExpression> methodCallExpressions

    MatchSpec(String methodName, String prefix, String queryExpression, int requiredArguments, List<MethodExpression> methodCallExpressions) {
        this.methodName = methodName
        this.prefix = prefix
        this.queryExpression = queryExpression
        this.requiredArguments = requiredArguments
        this.methodCallExpressions = methodCallExpressions
    }

    Collection<String> getPropertyNames() {
        methodCallExpressions.collect() { MethodExpression me ->
            me.propertyName
        }
    }
}
