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
package com.example.nativeapp

import java.util.stream.LongStream

import groovy.transform.CompileStatic

import grails.boot.config.GrailsAutoConfiguration

/**
 * An application in a package of its own, outside the framework's, naming that package as the one its
 * classes are in.
 */
class NativeApplicationFixture extends GrailsAutoConfiguration {

    @Override
    Collection<String> packageNames() {
        ['com.example.nativeapp']
    }
}

/** A status an application declares, as a domain class would. */
enum OrderStatusFixture {
    OPEN,
    CLOSED
}

/** A trait an application declares. */
trait AuditedFixture {

    String auditedBy = 'system'
}

/** A class implementing it. */
@CompileStatic
class AuditedOrderFixture implements AuditedFixture {
}

/**
 * A closure an application passes where a method takes an interface. {@code LongBinaryOperator} because the
 * framework coerces no closure to it.
 */
@CompileStatic
class TotalsFixture {

    static long total(long upTo) {
        LongStream.range(0, upTo).reduce(0L) { long left, long right -> left + right }
    }
}
