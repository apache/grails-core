/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.codegen.model

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Utility class for modifying domain class source files to add properties and fields.
 *
 * @since 7.1
 */
@CompileStatic
class DomainFieldModifier {

    /**
     * Prefix for Groovy synthetic fields (e.g., closure fields, metaclass references).
     * See also: org.grails.datastore.mapping.reflect.NameUtils.DOLLAR_SEPARATOR
     */
    private static final String SYNTHETIC_FIELD_PREFIX = '$'

    /**
     * Prefix for Groovy trait fields (format: traitClassName__fieldName).
     * See: org.grails.datastore.mapping.reflect.FieldEntityAccess.getTraitFieldName()
     */
    private static final String TRAIT_FIELD_PREFIX = '__'

    /**
     * Finds the domain class file for the given class name.
     *
     * @param projectDir the project root directory
     * @param className the simple class name or fully qualified class name
     * @return the domain class file, or null if not found
     */
    File findDomainFile(File projectDir, String className) {

        File domainDir = new File(projectDir, 'grails-app/domain')
        if (!domainDir.exists()) {
            return null
        }

        String fileName = className.replace('.', '/') + '.groovy'
        File exactMatch = new File(domainDir, fileName)
        if (exactMatch.exists()) {
            return exactMatch
        }

        String simpleClassName = className.contains('.') ? className.substring(className.lastIndexOf('.') + 1) : className
        List<File> matches = []

        domainDir.eachFileRecurse { File file ->
            if (file.name == "${simpleClassName}.groovy") {
                matches.add(file)
            }
        }

        if (matches.size() > 1) {
            String paths = matches.collect { it.path }.join(', ')
            throw new IllegalStateException(
                "Multiple domain classes found with name '${simpleClassName}': ${paths}. " +
                'Please specify the fully qualified class name.'
            )
        }

        matches.empty ? null : matches[0]
    }

    /**
     * Checks if a member (property or field) with the given name already exists in the domain class.
     *
     * @param domainFile the domain class file
     * @param memberName the member name to check
     * @return true if the member exists, false otherwise
     */
    boolean memberExists(File domainFile, String memberName) {

        if (!domainFile?.exists()) {
            return false
        }

        try {
            ClassNode classNode = parseClass(domainFile)
            if (classNode == null) {
                return false
            }

            for (PropertyNode prop : classNode.properties) {
                if (prop.name == memberName) {
                    return true
                }
            }

            for (FieldNode field : classNode.fields) {
                if (field.name == memberName && !field.name.startsWith(SYNTHETIC_FIELD_PREFIX) && !field.name.startsWith(TRAIT_FIELD_PREFIX)) {
                    return true
                }
            }

            return false
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to parse domain class '${domainFile.name}'. Please fix any syntax errors and try again.", e
            )
        }
    }

    /**
     * Adds a member (property or field) to the domain class file.
     *
     * @param domainFile the domain class file
     * @param member the member definition to add
     * @throws IllegalStateException if the file cannot be modified
     */
    void addMember(File domainFile, AbstractMemberDefinition member) {

        if (!domainFile?.exists()) {
            throw new IllegalStateException("Domain file does not exist: ${domainFile}")
        }

        member.validate()

        List<String> lines = Files.readAllLines(domainFile.toPath(), StandardCharsets.UTF_8)
        InsertionPoints points = findInsertionPoints(lines)

        int linesAdded = 0

        if (member.usesJakartaAnnotations()) {
            Set<String> requiredImports = member.getRequiredImports()
            Set<String> existingImports = findExistingImports(lines)

            int importsAddedCount = 0
            for (String importClass : requiredImports) {
                if (!existingImports.contains(importClass)) {
                    String importLine = 'import ' + importClass
                    lines.add(points.importInsertLine + linesAdded, importLine)
                    linesAdded++
                    importsAddedCount++
                }
            }

            if (importsAddedCount > 0) {
                int lineAfterImports = points.importInsertLine + linesAdded
                if (lineAfterImports < lines.size()) {
                    String nextLine = lines.get(lineAfterImports).trim()
                    if (!nextLine.isEmpty() && !nextLine.startsWith('import ')) {
                        lines.add(lineAfterImports, '')
                        linesAdded++
                    }
                }
            }
        }

        int memberInsertIndex = points.fieldInsertLine + linesAdded

        if (member.usesJakartaAnnotations()) {
            List<String> annotations = member.toAnnotations()
            for (String annotation : annotations) {
                lines.add(memberInsertIndex, '    ' + annotation)
                memberInsertIndex++
                linesAdded++
            }
        }

        String memberDeclaration = "    ${member.toDeclaration()}"
        lines.add(memberInsertIndex, memberDeclaration)
        linesAdded++

        if (member.usesGrailsConstraints()) {
            String constraintLine = member.toConstraintLine()
            if (constraintLine) {
                if (points.hasConstraintsBlock) {
                    int constraintInsertIndex = points.constraintInsertLine + linesAdded
                    lines.add(constraintInsertIndex, '        ' + constraintLine)
                } else {
                    int constraintBlockIndex = memberInsertIndex + 1
                    lines.add(constraintBlockIndex, '')
                    lines.add(constraintBlockIndex + 1, '    static constraints = {')
                    lines.add(constraintBlockIndex + 2, '        ' + constraintLine)
                    lines.add(constraintBlockIndex + 3, '    }')
                }
            }
        }

        Files.write(domainFile.toPath(), lines, StandardCharsets.UTF_8)
    }

    /**
     * Adds a property to the domain class file.
     *
     * @param domainFile the domain class file
     * @param property the property definition to add
     * @throws IllegalStateException if the file cannot be modified
     */
    void addProperty(File domainFile, PropertyDefinition property) {

        addMember(domainFile, property)
    }

    /**
     * Adds a field to the domain class file.
     *
     * @param domainFile the domain class file
     * @param field the field definition to add
     * @throws IllegalStateException if the file cannot be modified
     */
    void addField(File domainFile, FieldDefinition field) {

        addMember(domainFile, field)
    }

    /**
     * Finds existing import statements in the file.
     */
    private Set<String> findExistingImports(List<String> lines) {
        Set<String> imports = [] as Set
        for (String line : lines) {
            String trimmed = line.trim()
            if (trimmed.startsWith('import ') && !trimmed.startsWith('import static')) {
                String importClass = trimmed.substring(7).replace(';', '').trim()
                imports.add(importClass)
            }
        }
        imports
    }

    /**
     * Parses the Groovy source file and returns the main class node.
     */
    private ClassNode parseClass(File sourceFile) {

        CompilerConfiguration config = new CompilerConfiguration()
        config.tolerance = 10

        CompilationUnit compilationUnit = new CompilationUnit(config)
        compilationUnit.addSource(sourceFile)
        compilationUnit.compile(Phases.CONVERSION)

        List<ModuleNode> modules = compilationUnit.getAST()?.getModules()
        if (modules && !modules.isEmpty()) {
            ModuleNode moduleNode = modules[0]
            if (moduleNode?.classes) {
                return (ClassNode) moduleNode.classes[0]
            }
        }

        null
    }

    /**
     * Finds the insertion points for fields and constraints in the source file.
     */
    private InsertionPoints findInsertionPoints(List<String> lines) {
        InsertionPoints points = new InsertionPoints()

        int packageLine = -1
        int lastImportLine = -1
        int classOpenBrace = -1
        int classCloseBrace = -1
        int lastFieldLine = -1
        int constraintsStart = -1
        int constraintsEnd = -1
        int braceDepth = 0
        boolean inClass = false
        boolean inConstraints = false
        int constraintsBraceDepth = 0

        for (int i = 0; i < lines.size(); i++) {
            String line = lines[i]
            String trimmed = line.trim()

            if (trimmed.startsWith('package ')) {
                packageLine = i
            }

            if (trimmed.startsWith('import ')) {
                lastImportLine = i
            }

            if (!inClass && trimmed.matches(/^class\s+\w+.*\{.*$/)) {
                inClass = true
                classOpenBrace = i
                braceDepth = 1
                continue
            }

            if (!inClass) {
                if (trimmed.matches(/^class\s+\w+.*$/)) {
                    for (int j = i + 1; j < lines.size(); j++) {
                        if (lines[j].contains('{')) {
                            inClass = true
                            classOpenBrace = j
                            braceDepth = 1
                            break
                        }
                    }
                }
                continue
            }

            int openBraces = line.count('{')
            int closeBraces = line.count('}')

            if (trimmed.startsWith('static constraints') || trimmed.startsWith('static constraints =')) {
                inConstraints = true
                constraintsStart = i
                constraintsBraceDepth = 0
            }

            if (inConstraints) {
                constraintsBraceDepth += openBraces - closeBraces
                if (constraintsBraceDepth <= 0 && constraintsStart != i) {
                    constraintsEnd = i
                    inConstraints = false
                    points.hasConstraintsBlock = true
                }
            }

            if (!inConstraints && !trimmed.startsWith('static ') && !trimmed.startsWith('//') &&
                !trimmed.startsWith('/*') && !trimmed.startsWith('*') && !trimmed.startsWith('@') &&
                !trimmed.isEmpty() && !trimmed.startsWith('}') && !trimmed.startsWith('{')) {

                if (trimmed.matches(/^[A-Z]\w*\s+\w+.*$/) || trimmed.matches(/^(def|var)\s+\w+.*$/)) {
                    lastFieldLine = i
                }
            }

            braceDepth += openBraces - closeBraces

            if (braceDepth == 0 && inClass) {
                classCloseBrace = i
                break
            }
        }

        if (lastImportLine >= 0) {
            points.importInsertLine = lastImportLine + 1
        } else if (packageLine >= 0) {
            points.importInsertLine = packageLine + 2
        } else {
            points.importInsertLine = 0
        }

        if (lastFieldLine >= 0) {
            points.fieldInsertLine = lastFieldLine + 1
        } else if (classOpenBrace >= 0) {
            points.fieldInsertLine = classOpenBrace + 1
        } else {
            points.fieldInsertLine = lines.size() - 1
        }

        if (constraintsStart >= 0 && constraintsEnd >= 0) {
            points.constraintInsertLine = constraintsEnd
        } else {
            points.constraintInsertLine = points.fieldInsertLine + 1
        }

        points.classCloseLine = classCloseBrace

        points
    }

    /**
     * Internal class to hold insertion point information.
     */
    private static class InsertionPoints {

        int importInsertLine = 0
        int fieldInsertLine = 0
        int constraintInsertLine = 0
        int classCloseLine = -1
        boolean hasConstraintsBlock = false
    }
}
