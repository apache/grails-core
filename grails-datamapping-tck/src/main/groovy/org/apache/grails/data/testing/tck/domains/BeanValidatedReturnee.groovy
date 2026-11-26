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

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Null

import org.hibernate.validator.group.GroupSequenceProvider

import org.apache.grails.data.testing.tck.support.BeanValidatedReturneeSequenceProvider
import org.apache.grails.data.testing.tck.support.RetiredChecks

/**
 * A member of a {@link BeanValidatedRoster} whose {@code beforeValidate} callback brings it back from retirement,
 * the state its {@link BeanValidatedReturneeSequenceProvider} chooses its default group sequence from: validated
 * after the callback, it is an active member, which needs the desk the block requires.
 */
@Entity
@GroupSequenceProvider(BeanValidatedReturneeSequenceProvider)
class BeanValidatedReturnee implements Serializable {

    Long id
    Long version

    @NotBlank
    String name

    boolean retired

    @Null(groups = RetiredChecks)
    String desk

    static belongsTo = [roster: BeanValidatedRoster]

    static constraints = {
        desk nullable: false
    }

    def beforeValidate() {
        retired = false
    }
}
