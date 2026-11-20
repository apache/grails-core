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
package org.apache.grails.forge.cli.pack

import groovy.transform.CompileStatic
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads one catalog in a virtual machine of its own, whose memory a specification sets, and says what
 * it read and how much of the memory is in use afterwards.
 */
@CompileStatic
class PackCatalogProbe {

    static void main(String[] args) {
        URI address = URI.create(args[0])
        PackCatalog catalog = PackCatalog.parse(Files.readAllBytes(Path.of(address)), address)
        Runtime runtime = Runtime.runtime
        System.gc()
        println "plugins=${catalog.plugins.size()} versions=${catalog.plugins.sum(0) { PackCatalog.Plugin plugin -> plugin.versions.size() }} " +
                "usedMiB=${(runtime.totalMemory() - runtime.freeMemory()).intdiv(1024 * 1024)} maxMiB=${runtime.maxMemory().intdiv(1024 * 1024)}"
    }
}
