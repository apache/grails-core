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

package gorm

/**
 * Concrete GORM domain class (located in {@code grails-app/domain}) that extends an abstract
 * {@code @DirtyCheck} base which declares an explicit {@code id bindable: true} constraint. The subclass
 * declares no constraint of its own, so the bindable constraint must be inherited and {@code id} must be
 * bound by data binding. See PR 15699.
 */
class BindableIdRecord extends AbstractBindableIdRecord {

    static constraints = {
        description nullable: true
    }
}
