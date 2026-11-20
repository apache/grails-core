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

package org.grails.datastore.mapping.mongo.connections

import com.mongodb.MongoClientSettings

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link MongoClientSettings.Builder} used to construct the underlying
 * {@link com.mongodb.client.MongoClient} before it is created.
 *
 * <p>Any beans of this type found in the application context are applied, in order,
 * to the builder for the default connection source. This is the supported extension
 * point for settings that cannot be expressed through {@code grails.mongodb.*}
 * configuration — for example registering a driver
 * {@link com.mongodb.event.CommandListener} for metrics/tracing, tuning the connection
 * pool, or configuring read/write concerns programmatically.</p>
 *
 * <pre class="code">
 * &#64;Bean
 * MongoClientSettingsBuilderCustomizer mongoMetrics(MeterRegistry registry) {
 *     return { builder -> builder.addCommandListener(new MongoMetricsCommandListener(registry)) }
 * }
 * </pre>
 *
 * <p>Mirrors the semantics of Spring Boot's
 * {@code MongoClientSettingsBuilderCustomizer}.</p>
 *
 * @since 7.2
 */
@FunctionalInterface
interface MongoClientSettingsBuilderCustomizer {

    /**
     * Customize the {@link MongoClientSettings.Builder}.
     *
     * @param builder the builder to customize, never {@code null}
     */
    void customize(MongoClientSettings.Builder builder)
}
