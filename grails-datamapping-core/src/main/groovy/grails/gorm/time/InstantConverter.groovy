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

package grails.gorm.time

import java.time.Instant

import groovy.transform.CompileStatic

/**
 * A trait to convert a {@link java.time.Instant} to and from a long
 *
 * @author James Kleeh
 */
@CompileStatic
trait InstantConverter {

    Long convert(Instant value) {
        value.toEpochMilli()
    }

    Instant convert(Long value) {
        Instant.ofEpochMilli(value)
    }
}
