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

/**
 * Shared fixture domain class for unit-testing the {@code finders} package's classes
 * (MethodExpression, DynamicFinder and its subclasses) against a real {@code PersistentEntity}
 * shape rather than a fully mocked one. Not persisted anywhere - the finders package only
 * translates method calls into {@code Query.Criterion}/{@code Query} objects, it never touches
 * a real datastore itself.
 */
class FinderTestEntity {
    Long id
    String name
    String title
    String author
    Integer age
    BigDecimal price
    Boolean active
    List tags

    // Not a real GORM enhancement - FinderTestEntity is a plain Groovy class, never registered
    // with a datastore. This stand-in exists purely so FindOrSaveByFinderSpec can observe that
    // FindOrSaveByFinder actually invokes save() on the instance it constructs (via
    // metaClass.invokeMethod(result, "save", null)), which would otherwise fail with a
    // MissingMethodException against an unenhanced class.
    boolean saved = false

    def save(args = null) {
        saved = true
        this
    }
}
