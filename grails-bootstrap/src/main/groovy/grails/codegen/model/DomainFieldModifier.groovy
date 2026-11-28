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
package grails.codegen.model

import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit

/**
 * Utility class for modifying domain class source files to add properties and fields.
 *
 * <p>The source is parsed, and where the class, its properties and its {@code constraints} block are
 * is what the parser says and not what the lines look like: a brace in a comment or in a string, a
 * method that returns a {@code String} and a local variable are not taken for the class or for a
 * property. Lines are added to the text and no line that is there is written anew, so the line
 * endings, the indentation and the end of the file stay what they were. A source that cannot be
 * changed that way is refused with an {@link IllegalStateException} that says why, and nothing is
 * written: the text that is written was parsed again before, and has the member where it was meant
 * to be.</p>
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

    private static final String CONSTRAINTS = 'constraints'
    private static final String DEFAULT_INDENT = '    '
    private static final String CLOSE = '}'
    private static final Pattern LINE = Pattern.compile(/[^\r\n]*(?:\r\n|\n|\r)|[^\r\n]+$/)
    private static final Pattern IMPORT = Pattern.compile(/^\s*import\s+(?!static\b)([\w.]+(?:\.\*)?)\s*;?\s*(?:\/\/.*)?$/)
    private static final Pattern PACKAGE = Pattern.compile(/^\s*package\s+[\w.]+\s*;?\s*(?:\/\/.*)?$/)

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
            // by their paths in the project: the message names nothing of the computer
            String paths = matches.collect { File match -> projectDir.toPath().relativize(match.toPath()).join('/') }.sort().join(', ')
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
     * @throws IllegalStateException when the file cannot be parsed or is not text in UTF-8
     */
    boolean memberExists(File domainFile, String memberName) {
        if (!domainFile?.exists()) {
            return false
        }

        ClassNode classNode = parseClass(domainFile.name, read(domainFile))
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

        false
    }

    /**
     * Adds a member (property or field) to the domain class file. The file is written once, with the
     * whole of the change, and is not written at all when the change is refused.
     *
     * @param domainFile the domain class file
     * @param member the member definition to add
     * @throws IllegalStateException if the file cannot be modified: the message says why
     */
    void addMember(File domainFile, AbstractMemberDefinition member) {
        if (!domainFile?.exists()) {
            throw new IllegalStateException("Domain file does not exist: ${domainFile}")
        }

        String changed = withMember(domainFile.name, read(domainFile), member)
        Files.write(domainFile.toPath(), changed.getBytes(StandardCharsets.UTF_8))
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
     * The source of a domain class with a member added to it: the member after the last property or
     * field the class declares (after the line the class begins with when it has none), its
     * constraint as the last entry of the {@code constraints} block of the class (in a new block
     * after the member when the class has none), and its annotations with their imports. Every
     * line of the source is kept as it is, with its line ending; the lines that are added have the
     * line ending and the indentation the source has.
     *
     * @param fileName the name of the file, which names the class that is meant when the source has several
     * @param source the source of the domain class
     * @param member the member definition to add
     * @return the source with the member
     * @throws IllegalStateException when the source cannot be parsed, is an interface, or is written
     * in a way the member cannot be added to line by line; the message says which
     * @since 9.0
     */
    String withMember(String fileName, String source, AbstractMemberDefinition member) {
        member.validate()

        ClassNode classNode = parseClass(fileName, source)
        if (classNode == null) {
            throw refused(member, fileName, 'the file declares no class')
        }
        if (classNode.isInterface()) {
            // a property written into an interface is a constant that has no value, which does not compile
            throw refused(member, fileName, "${classNode.nameWithoutPackage} is an interface, and an interface has no properties")
        }
        String separator = source.contains('\r\n') ? '\r\n' : source.contains('\n') ? '\n' : source.contains('\r') ? '\r' : '\n'
        List<String> lines = split(source)

        int classEnd = classNode.lastLineNumber - 1
        int closingBrace = classNode.lastColumnNumber - 2
        int[] opening = bodyStart(lines, classNode, classEnd)
        if (opening == null || !isAt(lines, classEnd, closingBrace, CLOSE)) {
            throw refused(member, fileName, 'where the class begins and ends could not be told')
        }
        int classStart = opening[0]
        if (classStart == classEnd) {
            String line = lines[classEnd]
            if (!line.substring(opening[1] + 1, closingBrace).trim().isEmpty()) {
                throw refused(member, fileName, 'the class is written on one line; write its closing brace on a line of its own')
            }
            // a class without anything, as class Book {}: its closing brace goes to a line of its own
            lines[classEnd] = line.substring(0, opening[1] + 1) + separator
            lines.add(classEnd + 1, leading(line) + line.substring(closingBrace))
            return withMember(fileName, lines.join(''), member)
        }

        String classIndent = leading(lines[classStart])
        // the properties and the fields that are written in the class: the field of a property is one the compiler adds
        List<ASTNode> declared = []
        declared.addAll(classNode.properties.findAll { PropertyNode property -> !property.isStatic() && property.lineNumber > 0 })
        declared.addAll(classNode.fields.findAll { FieldNode field ->
            !field.isStatic() && !field.isSynthetic() && field.lineNumber > 0 && !field.name.startsWith(SYNTHETIC_FIELD_PREFIX) && !field.name.startsWith(TRAIT_FIELD_PREFIX)
        })
        int memberAt
        String indent
        if (declared) {
            ASTNode last = declared.max { ASTNode node -> node.lastLineNumber }
            if (last.lastLineNumber - 1 >= classEnd || last.lineNumber - 1 <= classStart) {
                String name = last instanceof PropertyNode ? ((PropertyNode) last).name : ((FieldNode) last).name
                throw refused(member, fileName, "the property ${name} is on the line the class begins or ends with; write it on a line of its own")
            }
            memberAt = last.lastLineNumber
            indent = leading(lines[last.lineNumber - 1])
        } else {
            String after = content(lines[classStart]).substring(opening[1] + 1).trim()
            if (!after.isEmpty() && !after.startsWith('//')) {
                throw refused(member, fileName, 'there is something after the brace the class begins with, on its line; write it on a line of its own')
            }
            memberAt = classStart + 1
            // the indentation of what the class has, of whatever kind, and four spaces in a class that has nothing
            List<Integer> written = classNode.fields*.lineNumber + classNode.methods*.lineNumber + classNode.declaredConstructors*.lineNumber
            written.addAll(classNode.innerClasses.collect { ClassNode inner -> inner.lineNumber })
            Integer first = written.findAll { int number -> number - 1 > classStart && number - 1 < classEnd }.min()
            indent = first == null ? classIndent + DEFAULT_INDENT : leading(lines[first - 1])
        }
        String step = indent.startsWith(classIndent) && indent.length() > classIndent.length() ? indent.substring(classIndent.length()) : DEFAULT_INDENT

        // each change: the line it is made at, how many lines it takes away there, and the lines it puts there
        List<List> changes = []
        List<String> memberLines = []
        if (member.usesJakartaAnnotations()) {
            member.toAnnotations().each { String annotation -> memberLines << indent + annotation }
        }
        memberLines << indent + member.toDeclaration()

        String constraint = member.usesGrailsConstraints() ? member.toConstraintLine() : null
        if (constraint) {
            FieldNode constraints = classNode.getDeclaredField(CONSTRAINTS)
            if (constraints == null) {
                memberLines << '' << indent + 'static constraints = {' << indent + step + constraint << indent + CLOSE
                String next = content(lines[memberAt]).trim()
                if (!next.isEmpty() && !next.startsWith(CLOSE)) {
                    memberLines << ''
                }
            } else {
                changes << constraintChange(lines, constraints, constraint, step, separator, member, fileName)
            }
        }
        changes << [memberAt, 0, memberLines.collect { String line -> line + separator }]

        if (member.usesJakartaAnnotations()) {
            List importChange = importChange(lines, classStart, member, separator)
            if (importChange != null) {
                changes << importChange
            }
        }

        // from the end of the file to its beginning, so that the place of one is not moved by another
        changes.sort { List change -> -((int) change[0]) }
        for (List change : changes) {
            int at = (int) change[0]
            ((int) change[1]).times { lines.remove(at) }
            lines.addAll(at, (List<String>) change[2])
        }
        String changed = lines.join('')
        verify(fileName, classNode, changed, member, constraint != null)
        changed
    }

    // the entry of a member in the constraints block the class has, as the last one of the block
    private static List constraintChange(List<String> lines, FieldNode constraints, String constraint, String step, String separator,
                                         AbstractMemberDefinition member, String fileName) {
        Expression value = constraints.getInitialExpression()
        if (!constraints.isStatic() || !(value instanceof ClosureExpression) || value.lineNumber < 1) {
            throw refused(member, fileName, 'the constraints of the class are not a block that is written in it, as static constraints = { }, so the constraint has no place')
        }
        int first = value.lineNumber - 1
        int open = value.columnNumber - 1
        int last = value.lastLineNumber - 1
        int close = value.lastColumnNumber - 2
        if (!isAt(lines, first, open, '{') || !isAt(lines, last, close, CLOSE)) {
            throw refused(member, fileName, 'where the constraints block begins and ends could not be told')
        }
        String blockIndent = leading(lines[constraints.lineNumber - 1])
        String entryIndent = blockIndent + step
        for (int i = last - 1; i > first; i--) {
            if (!content(lines[i]).trim().isEmpty()) {
                entryIndent = leading(lines[i])
                break
            }
        }
        String line = lines[last]
        String entry = entryIndent + constraint + separator
        if (first == last) {
            // a block on one line, as static constraints = {}: it is opened, and what it has is its first entry
            String inner = line.substring(open + 1, close).trim()
            List<String> opened = [line.substring(0, open + 1) + separator]
            if (!inner.isEmpty()) {
                opened << entryIndent + inner + separator
            }
            opened << entry << blockIndent + line.substring(close)
            return [last, 1, opened]
        }
        String before = line.substring(0, close)
        if (before.trim().isEmpty()) {
            return [last, 0, [entry]]
        }
        // the brace that ends the block is on the line of its last entry
        return [last, 1, [before.replaceAll(/\s+$/, '') + separator, entry, blockIndent + line.substring(close)]]
    }

    // the imports the annotations of a member need that the source does not have, after the imports or the package it has
    private static List importChange(List<String> lines, int classStart, AbstractMemberDefinition member, String separator) {
        Set<String> existing = [] as Set
        int lastImport = -1
        int packageLine = -1
        for (int i = 0; i < classStart; i++) {
            String line = content(lines[i])
            Matcher imported = IMPORT.matcher(line)
            if (imported.matches()) {
                existing << imported.group(1)
                lastImport = i
            } else if (packageLine < 0 && PACKAGE.matcher(line).matches()) {
                packageLine = i
            }
        }
        List<String> added = member.getRequiredImports().findAll { String name ->
            !(name in existing) && !((name.substring(0, name.lastIndexOf('.')) + '.*') in existing)
        }.collect { String name -> 'import ' + name }
        if (added.isEmpty()) {
            return null
        }
        int at = lastImport >= 0 ? lastImport + 1 : packageLine + 1
        if (lastImport < 0 && packageLine >= 0) {
            added.add(0, '')
        }
        if (at < lines.size() && !content(lines[at]).trim().isEmpty()) {
            added << ''
        }
        [at, 0, added.collect { String line -> line + separator }]
    }

    // What is written is parsed again: it has the member, everything the class had, and the constraint in the block
    private static void verify(String fileName, ClassNode before, String changed, AbstractMemberDefinition member, boolean constrained) {
        ClassNode after
        try {
            after = parseClass(fileName, changed)
        } catch (IllegalStateException ignored) {
            after = null
        }
        boolean kept = after != null && after.name == before.name && after.getDeclaredField(member.name) != null &&
                after.fields*.name.containsAll(before.fields*.name) && after.methods.size() == before.methods.size()
        if (kept && constrained) {
            Expression block = after.getDeclaredField(CONSTRAINTS)?.getInitialExpression()
            Statement code = block instanceof ClosureExpression ? ((ClosureExpression) block).getCode() : null
            kept = code instanceof BlockStatement && ((BlockStatement) code).getStatements().any { Statement statement ->
                statement instanceof ExpressionStatement && ((ExpressionStatement) statement).getExpression() instanceof MethodCallExpression &&
                        ((MethodCallExpression) ((ExpressionStatement) statement).getExpression()).getMethodAsString() == member.name
            }
        }
        if (!kept) {
            throw refused(member, fileName, 'the class is written in a way the lines of the member could not be put into')
        }
    }

    private static IllegalStateException refused(AbstractMemberDefinition member, String fileName, String reason) {
        new IllegalStateException("Cannot add '${member.name}' to '${fileName}': ${reason}. The file was not changed.")
    }

    // the text of a file, which is UTF-8 or is not read: bytes that are no text would be written back as other bytes
    private static String read(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8)
        } catch (CharacterCodingException e) {
            throw new IllegalStateException("'${file.name}' is not text in UTF-8, and was not changed.", e)
        }
    }

    // the lines of a text, each with the line ending it has
    private static List<String> split(String text) {
        List<String> lines = []
        Matcher line = LINE.matcher(text)
        while (line.find()) {
            lines << line.group()
        }
        lines
    }

    private static String content(String line) {
        line.replaceAll(/[\r\n]+$/, '')
    }

    private static String leading(String line) {
        Matcher indentation = line =~ /^[ \t]*/
        indentation.find() ? indentation.group() : ''
    }

    private static boolean isAt(List<String> lines, int line, int column, String expected) {
        line >= 0 && line < lines.size() && column >= 0 && content(lines[line]).startsWith(expected, column)
    }

    // the line and the column of the brace the body of a class begins with: the first one after its name
    private static int[] bodyStart(List<String> lines, ClassNode classNode, int classEnd) {
        Pattern named = Pattern.compile('\\b(?:class|trait|interface|enum|record)\\s+' + Pattern.quote(classNode.nameWithoutPackage) + '\\b')
        for (int i = Math.max(0, classNode.lineNumber - 1); i <= classEnd && i < lines.size(); i++) {
            Matcher declaration = named.matcher(content(lines[i]))
            if (!declaration.find()) {
                continue
            }
            for (int j = i; j <= classEnd && j < lines.size(); j++) {
                int brace = content(lines[j]).indexOf('{', j == i ? declaration.end() : 0)
                if (brace >= 0) {
                    return [j, brace] as int[]
                }
            }
        }
        null
    }

    /**
     * Parses a Groovy source and returns the class the file is named after, or the first class of
     * the source when none has that name; {@code null} for a source that declares no class.
     */
    private static ClassNode parseClass(String fileName, String source) {
        ModuleNode moduleNode
        try {
            CompilerConfiguration config = new CompilerConfiguration()
            config.tolerance = 10

            CompilationUnit compilationUnit = new CompilationUnit(config)
            SourceUnit sourceUnit = compilationUnit.addSource(fileName, source)
            compilationUnit.compile(Phases.CONVERSION)
            moduleNode = sourceUnit.getAST()
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to parse domain class '${fileName}'. Please fix any syntax errors and try again.", e
            )
        }

        List<ClassNode> classes = (moduleNode?.classes ?: []).findAll { ClassNode each -> !each.isScript() && each.outerClass == null }
        String expected = fileName.endsWith('.groovy') ? fileName.substring(0, fileName.length() - '.groovy'.length()) : fileName
        classes.find { ClassNode each -> each.nameWithoutPackage == expected } ?: (classes ? classes[0] : null)
    }
}
