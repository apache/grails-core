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
package org.springframework.security.access.prepost

import groovy.transform.CompileStatic

import org.springframework.security.access.ConfigAttribute

@CompileStatic
class ExpressionBasedAnnotationConfigAttribute implements ConfigAttribute {

    final String preAuthorizeExpression
    final String preFilterExpression
    final String postAuthorizeExpression
    final String postFilterExpression

    ExpressionBasedAnnotationConfigAttribute(
            String preAuthorizeExpression,
            String preFilterExpression,
            String postAuthorizeExpression,
            String postFilterExpression
    ) {
        this.preAuthorizeExpression = preAuthorizeExpression
        this.preFilterExpression = preFilterExpression
        this.postAuthorizeExpression = postAuthorizeExpression
        this.postFilterExpression = postFilterExpression
    }

    boolean hasPreInvocationExpression() {
        preAuthorizeExpression != null || preFilterExpression != null
    }

    boolean hasPostInvocationExpression() {
        postAuthorizeExpression != null || postFilterExpression != null
    }

    @Override
    String getAttribute() {
        'EXPRESSION_BASED_ANNOTATION'
    }
}
