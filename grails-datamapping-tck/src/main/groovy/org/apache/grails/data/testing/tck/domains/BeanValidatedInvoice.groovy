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

package org.apache.grails.data.testing.tck.domains

import grails.persistence.Entity
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * An entity constrained with Bean Validation annotations and a constraints block, owning annotated lines.
 */
@Entity
class BeanValidatedInvoice implements Serializable {

    Long id
    Long version

    @NotNull
    String reference

    @Size(max = 5)
    String code

    String note

    static hasMany = [lines: BeanValidatedInvoiceLine]

    static constraints = {
        note(nullable: false)
    }
}
