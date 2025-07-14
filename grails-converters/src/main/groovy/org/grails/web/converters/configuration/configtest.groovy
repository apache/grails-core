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
package org.grails.web.converters.configuration

import grails.converters.JSON

/**
 * @author Siegfried Puchbauer
 */

new ConvertersConfigurationInitializer().initalize()

def defcfg = ConvertersConfigurationHolder.getConverterConfiguration(JSON)
def imcfg = new ImmutableConverterConfiguration<JSON>(defcfg)
def chcfg = new ChainedConverterConfiguration<JSON>(defcfg)

sleep 30

println defcfg
println imcfg
println chcfg
try {
    println defcfg.getMarshaller(new Object())
    println imcfg.getMarshaller(new Object())
    println chcfg.getMarshaller(new Object())
}
catch (e) {
    e.printStackTrace()
}

def map = [immutable: 0, chained: 0, default: 0]

def test = { label, jsonConfig ->
    def start = System.currentTimeMillis()
    30000.times {
        assert jsonConfig.getMarshaller(new Object())
    }
    def time = System.currentTimeMillis() - start
    println "$label --> ${time}ms"
    map[label] = map[label] + time
}

test("default", defcfg)
test("chained", chcfg)
test("immutable", imcfg)

map = [immutable: 0, chained: 0, default: 0]

100.times {
    test("chained", chcfg)
    test("immutable", imcfg)
    test("default", defcfg)
}

println "======"

println map
