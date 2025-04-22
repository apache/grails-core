package org.grails.cli.profile.commands

import groovy.transform.CompileStatic
import jline.console.completer.Completer
import jline.console.completer.FileNameCompleter
import org.grails.cli.profile.CommandDescription
import org.grails.cli.profile.ExecutionContext
import org.grails.cli.profile.ProjectCommand

import java.awt.Desktop

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @author graemerocher
 */
@CompileStatic
class OpenCommand implements ProjectCommand, Completer {

    public static final String NAME = "open"

    @Override
    String getName() {
        NAME
    }

    CommandDescription description = new CommandDescription(NAME, "Opens a file in the project", "open [FILE PATH]")

    @Override
    boolean handle(ExecutionContext executionContext) {
        def filePath = executionContext.commandLine.remainingArgsString
        if(filePath) {
            if(filePath == 'test-report') {
                filePath = 'build/reports/tests/index.html'
            }
            if(Desktop.isDesktopSupported())  {
                try {
                    Desktop.desktop.open(new File(filePath))
                    return true
                } catch (e) {
                    executionContext.console.error("Error opening file $filePath: $e.message", e)
                }
            }
            else {
                executionContext.console.error("File opening not supported by JVM, use native OS command")
            }
        }
        else {
            return true
        }
        return false
    }

    @Override
    int complete(String buffer, int cursor, List<CharSequence> candidates) {
        return new FileNameCompleter().complete(buffer, cursor, candidates)
    }
}
