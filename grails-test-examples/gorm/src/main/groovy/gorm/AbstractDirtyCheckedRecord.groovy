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

import grails.gorm.dirty.checking.DirtyCheck
import groovy.transform.CompileStatic

/**
 * Abstract base class living in {@code src/main/groovy} (i.e. not itself a domain class) annotated with
 * {@link DirtyCheck}. GORM injects {@code id} and {@code version} onto the furthest unresolved parent in the
 * hierarchy, which is this class. This reproduces the conditions of
 * <a href="https://github.com/apache/grails-core/issues/15681">issue 15681</a> where {@code id} and
 * {@code version} would incorrectly leak into the data binding whitelist of the concrete domain subclass.
 */
@DirtyCheck
@CompileStatic
abstract class AbstractDirtyCheckedRecord {
    String description
}
