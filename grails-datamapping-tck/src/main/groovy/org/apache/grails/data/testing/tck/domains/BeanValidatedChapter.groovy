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

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.groups.ConvertGroup

import org.apache.grails.data.testing.tck.support.ArchiveChecks
import org.apache.grails.data.testing.tck.support.PublishChecks

/**
 * A chapter of a {@link BeanValidatedManuscript}, validated in {@link PublishChecks} then {@link ArchiveChecks},
 * whose attachment converts the former to the latter.
 */
@Entity
class BeanValidatedChapter implements Serializable {

    Long id
    Long version

    @NotBlank(groups = PublishChecks)
    String heading

    @NotBlank(groups = ArchiveChecks)
    String tag

    @Valid
    @ConvertGroup(from = PublishChecks, to = ArchiveChecks)
    BeanValidatedAttachment attachment

    static belongsTo = [BeanValidatedManuscript]
}
