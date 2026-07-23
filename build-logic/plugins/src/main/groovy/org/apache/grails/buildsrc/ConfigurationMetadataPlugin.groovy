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
package org.apache.grails.buildsrc

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.RecordComponentVisitor
import org.objectweb.asm.Type

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.stream.Stream

import javax.inject.Inject

/** Generates standard Spring Boot configuration metadata from compiled classes without classloading them. */
class ConfigurationMetadataPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.withId('java') {
            JavaPluginExtension java = project.extensions.getByType(JavaPluginExtension)
            project.tasks.withType(JavaCompile).configureEach { JavaCompile task ->
                if (!task.options.compilerArgs.contains('-parameters')) {
                    task.options.compilerArgs.add('-parameters')
                }
            }
            Project compilerProject = project.rootProject.findProject(':grails-configuration-metadata')
            if (compilerProject == null) {
                throw new IllegalStateException(
                        'The configuration metadata plugin requires the :grails-configuration-metadata compiler project')
            }
            project.dependencies.add('compileOnly', compilerProject)
            def main = java.sourceSets.named('main')
            main.configure { sourceSet ->
                sourceSet.resources.exclude('META-INF/spring-configuration-metadata.json')
            }
            def generate = project.tasks.register('generateConfigurationMetadata', GenerateConfigurationMetadataTask) {
                it.classesDirs.from(main.map { sourceSet -> sourceSet.output.classesDirs })
                it.dependsOn(main.map { sourceSet -> sourceSet.output.classesDirs })
                it.dependsOn(project.tasks.matching { task -> task.name == 'copyAstClasses' })
                def overlay = project.layout.projectDirectory.file(
                        'src/main/resources/META-INF/additional-spring-configuration-metadata.json')
                if (overlay.asFile.isFile()) {
                    it.additionalMetadata.set(overlay)
                }
                it.outputDirectory.set(project.layout.buildDirectory.dir('generated/configurationMetadata'))
            }
            project.tasks.named(main.get().processResourcesTaskName) {
                it.dependsOn(generate)
                it.from(generate)
            }
        }
    }
}

@CacheableTask
abstract class GenerateConfigurationMetadataTask extends DefaultTask {

    static final String CONFIGURATION_PROPERTIES =
            'Lorg/springframework/boot/context/properties/ConfigurationProperties;'
    static final String CONSTRUCTOR_BINDING =
            'Lorg/springframework/boot/context/properties/bind/ConstructorBinding;'
    static final String PAYLOAD_FIELD = '__grailsConfigurationMetadata'

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getClassesDirs()

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getAdditionalMetadata()

    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @Inject
    abstract FileSystemOperations getFileSystemOperations()

    @TaskAction
    void generate() {
        Map<String, ClassModel> models = readModels()
        List<Map<String, Object>> groups = []
        List<Map<String, Object>> properties = []
        models.values().findAll { ClassModel model -> model.prefix != null }.sort { ClassModel model -> model.name }.each {
            ClassModel model ->
            if (model.prefix) {
                groups << [name: model.prefix, type: model.name, sourceType: model.name]
            }
            if (model.payloadProperties != null) {
                model.payloadGroups.each { Map<String, Object> group ->
                    Map<String, Object> entry = new LinkedHashMap<>(group)
                    entry.sourceType = model.name
                    groups << entry
                }
                model.payloadProperties.each { Map<String, Object> property ->
                    Map<String, Object> entry = new LinkedHashMap<>(property)
                    entry.sourceType = model.name
                    properties << entry
                }
            } else {
                GenerateConfigurationMetadataTask.addProperties(
                        model, model.prefix, model.name, models, groups, properties, new LinkedHashSet<String>())
            }
        }

        Map<String, Object> metadata = merge(groups, properties, readOverlay())
        File output = outputDirectory.get().asFile
        fileSystemOperations.delete { it.delete(output) }
        File target = new File(output, 'META-INF/spring-configuration-metadata.json')
        target.parentFile.mkdirs()
        target.setText(JsonOutput.prettyPrint(JsonOutput.toJson(canonical(metadata))) + '\n', StandardCharsets.UTF_8.name())
    }

    private Map<String, ClassModel> readModels() {
        Map<String, ClassModel> models = [:]
        classesDirs.files.findAll { File file -> file.isDirectory() }.sort { File file -> file.absolutePath }.each {
            File directory ->
            Stream<java.nio.file.Path> paths = Files.walk(directory.toPath())
            try {
                paths.filter { java.nio.file.Path path -> Files.isRegularFile(path) && path.fileName.toString().endsWith('.class') }
                        .sorted()
                        .forEach { java.nio.file.Path path ->
                            ClassModel model = GenerateConfigurationMetadataTask.readClass(Files.readAllBytes(path))
                            ClassModel previous = models.put(model.name, model)
                            if (previous != null && previous != model) {
                                throw new IllegalArgumentException(
                                        "Duplicate compiled class '${model.name}' in configuration metadata inputs")
                            }
                        }
            } finally {
                paths.close()
            }
        }
        models
    }

    static ClassModel readClass(byte[] bytes) {
        ClassModel model = new ClassModel()
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                model.name = name.replace('/', '.')
                model.superName = superName?.replace('/', '.')
                model.interfaces = interfaces.collect { String interfaceName -> interfaceName.replace('/', '.') }
            }

            @Override
            AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor != CONFIGURATION_PROPERTIES) {
                    return null
                }
                model.prefix = ''
                new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    void visit(String name, Object value) {
                        if (name == 'prefix' || name == 'value') {
                            model.prefix = String.valueOf(value)
                        }
                    }
                }
            }

            @Override
            FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                int payloadAccess = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC
                if (name == PAYLOAD_FIELD && value instanceof String && (access & payloadAccess) == payloadAccess) {
                    model.payload = value as String
                }
                null
            }

            @Override
            RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
                model.properties[name] = new PropertyModel(
                        name: name,
                        type: fieldType(descriptor, signature),
                        constructorBound: true,
                        readable: true)
                null
            }

            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                Type method = Type.getMethodType(descriptor)
                if (name == '<init>' && (access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC)) == 0) {
                    ConstructorModel constructor = new ConstructorModel()
                    model.constructors << constructor
                    Type[] argumentTypes = method.argumentTypes
                    List<String> argumentTypeNames = methodArgumentTypes(descriptor, signature)
                    return new MethodVisitor(Opcodes.ASM9) {
                        private int parameterIndex

                        @Override
                        void visitParameter(String parameterName, int parameterAccess) {
                            if (parameterName && parameterIndex < argumentTypes.length &&
                                    (parameterAccess & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_MANDATED)) == 0) {
                                constructor.properties[parameterName] = new PropertyModel(
                                        name: parameterName,
                                        type: argumentTypeNames[parameterIndex],
                                        constructorBound: true)
                            }
                            parameterIndex++
                        }

                        @Override
                        AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                            constructor.selected |= annotationDescriptor == CONSTRUCTOR_BINDING
                            null
                        }
                    }
                }
                if ((access & Opcodes.ACC_PUBLIC) == 0 ||
                        (access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)) != 0 || name.contains('$')) {
                    return null
                }
                if (name.startsWith('get') && name.length() > 3 && method.argumentTypes.length == 0 &&
                        method.returnType.sort != Type.VOID) {
                    addAccessor(model, decapitalize(name.substring(3)), method.returnType.descriptor,
                            methodReturnSignature(signature), false)
                } else if (name.startsWith('is') && name.length() > 2 && method.argumentTypes.length == 0 &&
                        method.returnType.sort == Type.BOOLEAN) {
                    addAccessor(model, decapitalize(name.substring(2)), method.returnType.descriptor,
                            methodReturnSignature(signature), false)
                } else if (name.startsWith('set') && name.length() > 3 && method.argumentTypes.length == 1) {
                    addAccessor(model, decapitalize(name.substring(3)), method.argumentTypes[0].descriptor,
                            methodFirstArgumentSignature(signature), true)
                }
                null
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES)

        if (model.payload != null) {
            Map payload = new JsonSlurper().parseText(model.payload) as Map
            model.prefix = payload.get('prefix') as String
            model.name = payload.get('sourceType') as String
            model.payloadGroups = ((payload.get('groups') ?: []) as List).collect { Map group ->
                new LinkedHashMap<String, Object>(group)
            }
            model.payloadProperties = ((payload.get('properties') ?: []) as List).collect { Map property ->
                new LinkedHashMap<String, Object>(property)
            }
        } else {
            List<ConstructorModel> selectedConstructors = model.constructors.findAll { ConstructorModel constructor ->
                constructor.selected
            }
            ConstructorModel bindingConstructor = selectedConstructors.size() == 1 ? selectedConstructors[0] :
                    (model.constructors.size() == 1 && !model.constructors[0].properties.isEmpty() ?
                            model.constructors[0] : null)
            bindingConstructor?.properties?.each { String name, PropertyModel constructorProperty ->
                PropertyModel property = model.properties.computeIfAbsent(name) { new PropertyModel(name: name) }
                property.type = property.type ?: constructorProperty.type
                property.constructorBound = true
            }
            model.properties = model.properties.findAll { String name, PropertyModel property ->
                property.writable || property.collectionOrMap() || property.constructorBound
            }
        }
        model
    }

    private static void addAccessor(ClassModel model, String name, String descriptor, String signature, boolean writable) {
        PropertyModel property = model.properties.computeIfAbsent(name) { new PropertyModel(name: name) }
        if (property.type == null || signature != null) {
            property.type = fieldType(descriptor, signature)
        }
        property.writable |= writable
        property.readable |= !writable
    }

    static void addProperties(ClassModel model, String prefix, String sourceType,
                               Map<String, ClassModel> models, List<Map<String, Object>> groups,
                               List<Map<String, Object>> properties,
                               Set<String> visiting) {
        if (!visiting.add(model.name)) {
            return
        }
        propertiesFor(model, models, new LinkedHashSet<String>()).values()
                .sort { PropertyModel property -> property.name }.each { PropertyModel property ->
            String name = prefix ? "${prefix}.${property.name}" : property.name
            ClassModel nested = models[property.rawType()]
            if (nested != null && !propertiesFor(nested, models, new LinkedHashSet<String>()).isEmpty()) {
                groups << [name: name, type: property.type, sourceType: sourceType]
                addProperties(nested, name, sourceType, models, groups, properties, visiting)
            } else {
                properties << [name: name, type: property.type, sourceType: sourceType]
            }
        }
        visiting.remove(model.name)
    }

    private static Map<String, PropertyModel> propertiesFor(ClassModel model, Map<String, ClassModel> models,
                                                             Set<String> visited) {
        if (model == null || !visited.add(model.name)) {
            return [:]
        }
        Map<String, PropertyModel> properties = [:]
        properties.putAll(propertiesFor(models[model.superName], models, visited))
        model.interfaces.each { String interfaceName ->
            properties.putAll(propertiesFor(models[interfaceName], models, visited))
        }
        properties.putAll(model.properties)
        properties
    }

    private Map readOverlay() {
        File file = additionalMetadata.asFile.orNull
        file?.isFile() ? new JsonSlurper().parse(file, StandardCharsets.UTF_8.name()) as Map : [:]
    }

    private static Map<String, Object> merge(List<Map<String, Object>> groups,
                                             List<Map<String, Object>> properties, Map overlay) {
        Map<String, Object> result = [:]
        result['groups'] = mergeNamed(groups, (overlay.get('groups') ?: []) as List, 'groups')
        result['properties'] = mergeNamed(properties, (overlay.get('properties') ?: []) as List, 'properties')
        if (overlay.containsKey('hints')) {
            result['hints'] = mergeNamed([], overlay.get('hints') as List, 'hints')
        }
        overlay.each { Object keyValue, Object value ->
            String key = keyValue.toString()
            if (!(key in ['groups', 'properties', 'hints', 'ignored'])) {
                result[key] = value
            }
        }
        if (overlay.containsKey('ignored')) {
            Map ignored = new LinkedHashMap((overlay.get('ignored') ?: [:]) as Map)
            if (ignored.containsKey('properties')) {
                ignored['properties'] = mergeNamed([], ignored.get('properties') as List, 'ignored.properties')
            }
            result['ignored'] = ignored
        }
        result
    }

    private static List<Object> mergeNamed(List generated, List overlay, String category) {
        Map<String, Object> generatedByName = indexByName(generated, category, 'generated')
        Map<String, Object> overlayByName = indexByName(overlay, category, 'overlay')
        Map<String, Object> merged = new LinkedHashMap<>(generatedByName)
        overlayByName.each { String name, Object value ->
            if (merged[name] instanceof Map && value instanceof Map) {
                merged[name] = new LinkedHashMap((Map) merged[name]) + (Map) value
            } else {
                merged[name] = value
            }
        }
        merged.keySet().sort().collect { String name -> merged[name] }
    }

    private static Map<String, Object> indexByName(List source, String category, String sourceName) {
        Map<String, Object> indexed = [:]
        source.each { Object entry ->
            String name = entry instanceof Map ? ((Map) entry).name as String : entry as String
            if (!name) {
                throw new IllegalArgumentException("${category} entry has no name")
            }
            if (indexed.containsKey(name) && indexed[name] != entry) {
                throw new IllegalArgumentException("Conflicting ${sourceName} ${category} metadata for '${name}'")
            }
            indexed[name] = entry
        }
        indexed
    }

    private static Object canonical(Object value) {
        if (value instanceof Map) {
            Map<String, Object> sorted = new LinkedHashMap<>()
            ((Map) value).keySet().collect { Object key -> key.toString() }.sort().each { String key ->
                sorted[key] = canonical(((Map) value)[key])
            }
            return sorted
        }
        if (value instanceof List) {
            return ((List) value).collect { Object entry -> canonical(entry) }
        }
        value
    }

    private static String fieldType(String descriptor, String signature) {
        signature ? new TypeSignatureParser(signature).parse() : Type.getType(descriptor).className
    }

    private static String methodReturnSignature(String signature) {
        signature ? signature.substring(signature.indexOf(')') + 1) : null
    }

    private static String methodFirstArgumentSignature(String signature) {
        signature ? signature.substring(signature.indexOf('(') + 1) : null
    }

    private static List<String> methodArgumentTypes(String descriptor, String signature) {
        signature?.startsWith('(') ? new TypeSignatureParser(signature).parseMethodArguments() :
                Type.getArgumentTypes(descriptor).collect { Type argument -> argument.className }
    }

    private static String decapitalize(String value) {
        value.length() > 1 && Character.isUpperCase(value.charAt(0)) && Character.isUpperCase(value.charAt(1)) ?
                value : value[0].toLowerCase(Locale.ROOT) + value.substring(1)
    }

    private static class ClassModel {
        String name
        String superName
        List<String> interfaces = []
        String prefix
        String payload
        List<Map<String, Object>> payloadGroups = []
        List<Map<String, Object>> payloadProperties
        List<ConstructorModel> constructors = []
        Map<String, PropertyModel> properties = [:]
    }

    private static class ConstructorModel {
        boolean selected
        Map<String, PropertyModel> properties = [:]
    }

    private static class PropertyModel {
        String name
        String type
        boolean constructorBound
        boolean readable
        boolean writable

        String rawType() {
            type.replaceFirst(/<.*/, '').replace('[]', '')
        }

        boolean collectionOrMap() {
            rawType() in [
                    'java.util.Collection', 'java.util.List', 'java.util.Set', 'java.util.SortedSet',
                    'java.util.NavigableSet', 'java.util.Queue', 'java.util.Deque', 'java.util.Map',
                    'java.util.SortedMap', 'java.util.NavigableMap'
            ]
        }

    }

    private static class TypeSignatureParser {
        private static final Map<Character, String> PRIMITIVES = [
                (('B' as char)): 'byte', (('C' as char)): 'char', (('D' as char)): 'double',
                (('F' as char)): 'float', (('I' as char)): 'int', (('J' as char)): 'long',
                (('S' as char)): 'short', (('Z' as char)): 'boolean', (('V' as char)): 'void'
        ]

        private final String signature
        private int position

        TypeSignatureParser(String signature) {
            this.signature = signature
        }

        String parse() {
            parseType()
        }

        List<String> parseMethodArguments() {
            if (signature.charAt(position++) != '(' as char) {
                throw new IllegalArgumentException("Not a JVM method signature '${signature}'")
            }
            List<String> arguments = []
            while (signature.charAt(position) != ')' as char) {
                arguments << parseType()
            }
            arguments
        }

        private String parseType() {
            char token = signature.charAt(position++)
            if (PRIMITIVES.containsKey(token)) {
                return PRIMITIVES[token]
            }
            if (token == '[' as char) {
                return parseType() + '[]'
            }
            if (token == 'T' as char) {
                return readUntil(';' as char)
            }
            if (token == '*' as char) {
                return '?'
            }
            if (token == '+' as char) {
                return '? extends ' + parseType()
            }
            if (token == '-' as char) {
                return '? super ' + parseType()
            }
            if (token != 'L' as char) {
                throw new IllegalArgumentException("Unsupported JVM type signature '${signature}'")
            }
            StringBuilder name = new StringBuilder()
            while (position < signature.length()) {
                char current = signature.charAt(position++)
                if (current == ';' as char) {
                    return name.toString().replace('/', '.')
                }
                if (current == '<' as char) {
                    List<String> arguments = []
                    while (signature.charAt(position) != '>' as char) {
                        arguments << parseType()
                    }
                    position++
                    name.append('<').append(arguments.join(',')).append('>')
                } else {
                    name.append(current == '.' as char ? '$' : current)
                }
            }
            throw new IllegalArgumentException("Incomplete JVM type signature '${signature}'")
        }

        private String readUntil(char delimiter) {
            int end = signature.indexOf(delimiter as int, position)
            String value = signature.substring(position, end)
            position = end + 1
            value
        }
    }
}
