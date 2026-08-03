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
package org.apache.grails.benchmarks.databinding;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import grails.databinding.SimpleDataBinder;
import grails.databinding.SimpleMapDataBindingSource;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Measures form and request-map binding into a command-style object. Binding overhead affects
 * controller actions and command objects on every submitted request.
 */
@State(Scope.Benchmark)
@Threads(1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms1g", "-Xmx1g", "-XX:+UseG1GC"})
public class SimpleDataBinderBenchmark {

    private SimpleDataBinder binder;
    private SimpleMapDataBindingSource flatSource;
    private SimpleMapDataBindingSource conversionSource;

    @Setup
    public void setup() {
        binder = new SimpleDataBinder();
        flatSource = new SimpleMapDataBindingSource(Map.of(
                "name", "Ada",
                "age", 42,
                "accountId", 9_001L,
                "status", Status.ACTIVE,
                "createdAt", new Date(1_700_000_000_000L)
        ));
        conversionSource = new SimpleMapDataBindingSource(Map.of(
                "name", "Ada",
                "age", "42",
                "accountId", "9001",
                "status", "ACTIVE",
                "createdAt", new Date(1_700_000_000_000L)
        ));
    }

    @Benchmark
    public BindingTarget bindFlatMap() {
        BindingTarget target = new BindingTarget();
        binder.bind(target, flatSource);
        return target;
    }

    @Benchmark
    public BindingTarget bindMapWithTypeConversion() {
        BindingTarget target = new BindingTarget();
        binder.bind(target, conversionSource);
        return target;
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public static final class BindingTarget {
        private String name;
        private Integer age;
        private Long accountId;
        private Status status;
        private Date createdAt;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public Long getAccountId() {
            return accountId;
        }

        public void setAccountId(Long accountId) {
            this.accountId = accountId;
        }

        public Status getStatus() {
            return status;
        }

        public void setStatus(Status status) {
            this.status = status;
        }

        public Date getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Date createdAt) {
            this.createdAt = createdAt;
        }
    }
}
