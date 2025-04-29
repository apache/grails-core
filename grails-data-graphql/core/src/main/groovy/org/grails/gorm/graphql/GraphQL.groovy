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

package org.grails.gorm.graphql

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation used to supply metadata to GraphQL. Can be used
 * on entites related to graphl mapped domains even if the
 * domain itself isn't mapped. Also useful to annotate on
 * enumerations because there is no other alternative.
 *
 * The default deprecation reason is "Deprecated"
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@Target([ElementType.TYPE, ElementType.FIELD])
@Retention(RetentionPolicy.RUNTIME)
@interface GraphQL {

    String value() default ''

    boolean deprecated() default false

    String deprecationReason() default ''
}
