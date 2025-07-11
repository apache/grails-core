/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.common.properties

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

final class PropertyFileUtils {

    private PropertyFileUtils() {
        // prevent  instantiation
    }

    // TODO: this is copied from grails-common, but we should consider moving grails-common up to grails-gradle once
    // the publish plugin is moved out of grails-core
    static void makePropertiesFileReproducible(File factoriesFile) {
        String sourceDateEpoch = System.getenv('SOURCE_DATE_EPOCH')
        if (!sourceDateEpoch) {
            return
        }

        Pattern timeRegex = Pattern.compile('^#(?:Sun|Mon|Tue|Wed|Thu|Fri|Sat)(?:,|\\\\s).*$')

        List<String> lines = factoriesFile.readLines(StandardCharsets.ISO_8859_1.name())

        boolean dateReplaced = false
        factoriesFile.withWriter { BufferedWriter writer ->
            lines.each { String line ->
                if (!dateReplaced && timeRegex.matcher(line).matches()) {
                    dateReplaced = true
                    writer.writeLine("# SOURCE_DATE_EPOCH = ${sourceDateEpoch}" as String)
                    return
                }

                writer.writeLine(line)
            }
        }
    }
}
