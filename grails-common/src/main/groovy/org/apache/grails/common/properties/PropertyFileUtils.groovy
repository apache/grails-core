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

        Instant buildInstant = Instant.ofEpochSecond(sourceDateEpoch as Long)
        List<String> lines = factoriesFile.readLines(StandardCharsets.ISO_8859_1.name())
        lines[1] = "# ${Date.from(buildInstant).toString()}" as String
        factoriesFile.withWriter { BufferedWriter writer ->
            lines.each { String line ->
                writer.writeLine(line)
            }
        }
    }
}
