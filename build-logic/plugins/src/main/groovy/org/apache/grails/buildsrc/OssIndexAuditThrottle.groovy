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

package org.apache.grails.buildsrc

import groovy.transform.CompileStatic

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * Shared build service used solely as a concurrency gate for {@code ossIndexAudit} tasks.
 *
 * <p>The Sonatype OSS Index Gradle plugin backs every audit task with a single shared
 * on-disk response cache guarded by a file lock. When Gradle runs audit tasks in parallel
 * (the default with {@code org.gradle.parallel=true}), the tasks contend for that lock and
 * fail with {@link java.nio.channels.OverlappingFileLockException}, surfaced by the plugin
 * as the opaque {@code Could not audit the project: null}.</p>
 *
 * <p>Registered with {@code maxParallelUsages = 1}, this service forces Gradle to run at most
 * one audit task at a time while leaving parallel execution enabled for the rest of the build.</p>
 */
@CompileStatic
abstract class OssIndexAuditThrottle implements BuildService<BuildServiceParameters.None> {
}
