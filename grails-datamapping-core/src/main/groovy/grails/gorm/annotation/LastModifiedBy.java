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
package grails.gorm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A property annotation used to automatically populate a field with the current auditor
 * upon GORM insert and update events. The current auditor is retrieved from an
 * {@link org.grails.datastore.gorm.timestamp.AuditorAware} bean registered in the Spring application context.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * class Book {
 *     @LastModifiedBy
 *     String lastModifiedBy
 *
 *     @LastModifiedBy
 *     User lastModifier
 *
 *     @LastModifiedBy
 *     Long lastModifierId
 * }
 * }</pre>
 *
 * <p>The field type should match the type parameter of your {@link org.grails.datastore.gorm.timestamp.AuditorAware}
 * implementation (e.g., String, Long, User, etc.).</p>
 *
 * @author Scott Murphy Heiberg
 * @since 7.1
 * @see org.grails.datastore.gorm.timestamp.AuditorAware
 * @see CreatedBy
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
public @interface LastModifiedBy {
}
