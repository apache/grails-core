/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.example.ejb3.customid

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table

import org.hibernate.annotations.NativeGenerator

/**
 * Entity using @NativeGenerator, matching the user's UserB pattern.
 */
@Entity
@Table(name = 'native_gen_entity')
class NativeGenEntity {

    @Id
    @Column(name = 'id', nullable = false, updatable = false)
    @NativeGenerator(sequenceForm = @SequenceGenerator(
            name = 'native_gen_seq',
            sequenceName = 'native_gen_seq',
            allocationSize = 22,
            initialValue = 3
    ))
    Long id

    @Column(name = 'name', nullable = false, length = 100)
    String name

}
