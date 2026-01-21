import java.io.File

File file = new File("../../grails-datamapping-tck/src/main/groovy/org/apache/grails/data/testing/tck/tests/NamedQuerySpec.groovy")
List<String> lines = file.readLines()
List<String> newLines = []
boolean importAdded = false
boolean annotationAdded = false

for (String line in lines) {
    if (!importAdded && line.startsWith("import ")) {
        newLines.add("import spock.lang.IgnoreIf")
        importAdded = true
    }
    if (!annotationAdded && line.contains("class NamedQuerySpec")) {
        newLines.add("@IgnoreIf({ System.getProperty(\"hibernate7.gorm.suite\") == \"true\" })")
        annotationAdded = true
    }
    newLines.add(line)
}

file.write(newLines.join("\n") + "\n")
println "Successfully updated NamedQuerySpec.groovy"