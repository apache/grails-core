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
package org.apache.grails.core.cli

import groovy.transform.CompileStatic

import org.apache.grails.gradle.model.cli.ToolResultFile

/**
 * Internal: not an API of Grails, and it may change in any version. The runners of tools use it to give
 * the result a tool sets, {@link ExecutionContext#getResult()}, to the program that started the tool, in
 * the file {@link ToolResultFile} says, beside the file of arguments the tool was started with.
 *
 * @since 9.0
 */
@CompileStatic
final class ToolResults {

    private ToolResults() {
    }

    /**
     * Makes way for the result of a tool before it runs: a file an earlier tool of the same number left
     * is removed, so that what is there after the tool is the result of this one.
     *
     * @param argumentsFile the file of arguments the tool was started with, or {@code null}
     * @return the file the result of the tool goes in, or {@code null} when it gives it to nobody
     */
    static File prepare(File argumentsFile) {
        File resultFile = ToolResultFile.of(argumentsFile)
        if (resultFile != null) {
            ToolResultFile.remove(resultFile)
        }
        resultFile
    }

    /**
     * Gives the result of a tool that has returned. A result that cannot be written is not given, and one
     * line on the standard error says why; what the tool returned is not changed by it.
     *
     * @param name the name of the tool
     * @param resultFile where the result goes ({@link #prepare}), or {@code null}
     * @param result what the tool set as its result, or {@code null} for none
     */
    static void give(String name, File resultFile, Object result) {
        if (resultFile == null || result == null) {
            return
        }
        try {
            ToolResultFile.write(resultFile, result)
        } catch (ToolResultFile.Refused refused) {
            System.err.println("| Warning ${name}: the result it set is not given, since it ${refused.message}")
        } catch (IOException ignored) {
            System.err.println("| Warning ${name}: the result it set is not given, since ${resultFile.name} cannot be written")
        }
    }
}
