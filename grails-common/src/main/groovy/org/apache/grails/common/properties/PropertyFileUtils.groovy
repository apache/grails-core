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
import java.time.Instant

final class PropertyFileUtils {
    private PropertyFileUtils() {
        // prevent  instantiation
    }

    static void makePropertiesFileReproducible(File factoriesFile) {
        String sourceDateEpoch = System.getenv('SOURCE_DATE_EPOCH')
        if (!sourceDateEpoch) {
            return
        }

        List<String> lines = factoriesFile.readLines(StandardCharsets.ISO_8859_1.name())
        lines[1] = "# SOURCE_DATE_EPOCH = ${sourceDateEpoch}" as String
        factoriesFile.withWriter { BufferedWriter writer ->
            lines.each { String line ->
                writer.writeLine(line)
            }
        }
    }
}
