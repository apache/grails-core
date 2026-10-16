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

import java.util.concurrent.atomic.AtomicLong

import org.hibernate.HibernateException
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.id.IdentifierGenerator

/**
 * Custom ID generator implementing the legacy {@link IdentifierGenerator} interface.
 * <p>
 * This test generator simply returns incrementing {@code long} values using an in-memory
 * counter and does not interact with any {@code GeneratorCreationContext} or its properties.
 */
class SnowflakeIdGenerator implements IdentifierGenerator {

    private static final AtomicLong counter = new AtomicLong(1)

    @Override
    Serializable generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
        return counter.getAndIncrement()
    }

}
