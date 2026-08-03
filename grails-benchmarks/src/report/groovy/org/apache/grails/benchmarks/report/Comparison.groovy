/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.grails.benchmarks.report

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class ComparisonRow {
    String identity
    Benchmark base
    Benchmark head
    Double speedup
    String verdict
    Double allocationDelta
    Double allocationPercent
    boolean allocationCandidate
}

@CompileStatic
@Immutable
class RulerDeviation {
    String shard
    String identity
    double speedup
}

@CompileStatic
@Immutable
class Comparison {
    List<ComparisonRow> rows
    List<String> onlyHead
    List<String> onlyBase
    int malformed
    Map<String, List<Object>> groupSpeedups
    List<List<Object>> rulerSpeedups
    List<String> dropped = []
    List<RulerDeviation> rulerDeviations = []
    List<String> rulerIncomplete = []
    List<String> missingShards = []
    List<String> pairedShards = []
}
