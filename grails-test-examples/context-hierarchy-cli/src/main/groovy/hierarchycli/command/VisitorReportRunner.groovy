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

package hierarchycli.command

import groovy.transform.CompileStatic

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator

import hierarchycli.VisitorService

/**
 * Registers the visitors named on the command line and prints a report of everyone registered. With
 * no names it prints how to use the command and asks for a failure exit code instead.
 */
@CompileStatic
class VisitorReportRunner implements ApplicationRunner, ExitCodeGenerator {

    static final String USAGE = 'usage: visitors <name>...'

    private final VisitorService visitorService

    private String report
    private int exitCode

    VisitorReportRunner(VisitorService visitorService) {
        this.visitorService = visitorService
    }

    @Override
    void run(ApplicationArguments args) {
        List<String> names = args.nonOptionArgs
        if (names.isEmpty()) {
            report = USAGE
            exitCode = 1
        }
        else {
            names.each { String name -> visitorService.register(name) }
            report = "${visitorService.count()} visitors: ${visitorService.names().join(', ')}"
            exitCode = 0
        }
        println report
    }

    @Override
    int getExitCode() {
        exitCode
    }

    String getReport() {
        report
    }
}
