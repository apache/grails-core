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
package org.apache.grails.core.aot;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.GroovyObject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.FieldVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ClassUtils;

import org.apache.grails.common.aot.RegistrableTypes;

/**
 * Registers the reflection the framework's own compiled classes imply, read from their bytecode.
 *
 * <p>Some of what a class does at run time is answered by reflection however it was compiled, and an image
 * answers none of it unless it was asked to:</p>
 *
 * <ul>
 * <li>A Groovy enum creates its constants by choosing a constructor through Groovy rather than by calling
 * one, so its constructors have to be visible. {@code grails.util.Environment} is one, and it is the first
 * thing an application touches: without this an image fails before Spring has started, with "Could not
 * find matching constructor".</li>
 * <li>A closure passed where a method takes an interface -- {@code removeIf}, {@code forEach},
 * {@code computeIfAbsent}, a plugin's {@code BeanRegistrar} -- is turned into that interface by a JDK proxy
 * created as the call is made. An image creates a proxy only for an interface it was told about, and
 * refuses the rest with {@code MissingReflectionRegistrationError}.</li>
 * <li>A class implementing a trait initializes it from its constructor by calling the trait's helper class
 * through {@code ScriptBytecodeAdapter.invokeMethodN}, a dynamic call. The framework's traits are on the
 * application class, the controllers, the tag libraries and the domain classes, so without this none of
 * them can be created: the first to fail is the application class itself, with "No signature of static
 * method: $init$".</li>
 * <li>A member annotated for Spring to inject or call -- {@code @Autowired}, {@code @Value},
 * {@code @Inject}, {@code @Resource}, {@code @PostConstruct}, {@code @PreDestroy} -- is set or called
 * reflectively by the post-processor that handles the annotation. Many of the framework's beans are
 * created by a supplier and declared as an interface, so their classes are known only once they exist,
 * and the members are found here instead: the link generator's collaborators were left unset without
 * this.</li>
 * <li>A Groovy object given a property dynamically is first asked, through {@code Class.getMethod}, whether
 * it overrides {@code setProperty}. An image answers that only for a class registered as a type, so every
 * Groovy class is registered as one: without it the bean DSL could not set the first bean's options, with
 * {@code NoSuchMethodException} for a method the class plainly has. Only the type is registered -- calls
 * are made through the interfaces the class implements, which are registered for what they declare.</li>
 * </ul>
 *
 * <p>All of it is found by reading the bytecode while the hints are written, which happens during the build
 * on an ordinary JVM with the full classpath: an enum is a class extending {@code Enum} that implements
 * {@code GroovyObject}, a coercion is an invokedynamic {@code cast} from {@code Closure} to an interface, a
 * trait's helpers are the classes Groovy names {@code $Trait$Helper}, {@code $Trait$FieldHelper} and
 * {@code $Trait$StaticFieldHelper}, an injected member carries its annotation, and a Groovy class is one
 * that implements {@code GroovyObject}, directly or through a superclass. The framework compiles
 * against optional integrations, so a class that cannot be loaded here is skipped rather than registered,
 * as {@link GrailsClosureRuntimeHints} does for closures.</p>
 *
 * @since 9.0
 */
public class BytecodeRuntimeHints implements RuntimeHintsRegistrar {

    private static final Log logger = LogFactory.getLog(BytecodeRuntimeHints.class);

    /** Where the framework's classes are found: its own packages, old and new. */
    private static final String[] PATTERNS = {
        ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "grails/**/*.class",
        ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "org/grails/**/*.class",
        ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + "org/apache/grails/**/*.class"
    };

    private static final String ENUM = "java/lang/Enum";

    private static final String GROOVY_OBJECT = "groovy/lang/GroovyObject";

    private static final String INDY_INTERFACE = "org/codehaus/groovy/vmplugin/v8/IndyInterface";

    private static final String CAST = "cast";

    private static final String CLOSURE = "Lgroovy/lang/Closure;";

    /** How Groovy names the classes that carry a trait's methods and fields. */
    private static final String[] TRAIT_HELPER_SUFFIXES = {
        "$Trait$Helper", "$Trait$FieldHelper", "$Trait$StaticFieldHelper"
    };

    /** The annotations a member is injected or called through, as they appear in bytecode. */
    private static final Set<String> SPRING_ANNOTATIONS = Set.of(
            "Lorg/springframework/beans/factory/annotation/Autowired;",
            "Lorg/springframework/beans/factory/annotation/Value;",
            "Ljakarta/inject/Inject;",
            "Ljakarta/annotation/Resource;",
            "Ljakarta/annotation/PostConstruct;",
            "Ljakarta/annotation/PreDestroy;");

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        registerFoundIn(hints, classLoader, PATTERNS);
    }

    /**
     * Registers the reflection the classes the given patterns match imply. Also used for the application's
     * own packages, which an application's classes are in and the framework's patterns do not cover.
     *
     * @param hints where the hints are registered
     * @param classLoader the loader the classes are read and loaded from
     * @param patterns resource patterns matching class files
     */
    static void registerFoundIn(RuntimeHints hints, @Nullable ClassLoader classLoader, String... patterns) {
        ClassLoader loader = (classLoader != null) ? classLoader : ClassUtils.getDefaultClassLoader();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
        Findings findings = new Findings();
        for (String pattern : patterns) {
            Resource[] resources;
            try {
                resources = resolver.getResources(pattern);
            }
            catch (IOException ex) {
                logger.warn("Unable to scan for classes matching " + pattern, ex);
                continue;
            }
            for (Resource resource : resources) {
                read(resource, findings);
            }
        }
        findings.register(hints, loader);
    }

    private static void read(Resource resource, Findings findings) {
        try (InputStream input = resource.getInputStream()) {
            new ClassReader(input).accept(new FindingClassVisitor(findings, resource),
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        catch (IOException | RuntimeException ex) {
            logger.debug("Unable to read " + resource, ex);
        }
    }

    /**
     * The type, or none if it cannot be loaded here: registering a type that does not load fails the image
     * build, and a closure cast to an abstract class is not proxied by the JDK, so only an interface is.
     */
    @Nullable
    private static Class<?> load(String type, ClassLoader loader) {
        if (!RegistrableTypes.loads(type, loader)) {
            return null;
        }
        try {
            return ClassUtils.forName(type, loader);
        }
        catch (ClassNotFoundException | LinkageError ex) {
            return null;
        }
    }

    /**
     * Whether the class is a Groovy class that can be registered: one implementing {@code GroovyObject}, that
     * loads here and whose bytecode names nothing absent here. A closure is left to
     * {@link GrailsClosureRuntimeHints}, which registers more of it.
     */
    private static boolean isGroovyClass(String className, Resource resource, ClassLoader loader) {
        if (className.contains("$_")) {
            return false;
        }
        Class<?> type = load(className, loader);
        if (type == null || type.isInterface() || !GroovyObject.class.isAssignableFrom(type)) {
            return false;
        }
        try {
            return RegistrableTypes.referencesLoad(resource.getInputStream(), loader);
        }
        catch (IOException ex) {
            return false;
        }
    }

    /** A method by its name and the names of its parameter types. */
    private record MethodSignature(String name, List<TypeReference> parameterTypes) {
    }

    /**
     * What the bytecode was found to need, by the names it gives the types. Registered by name rather than
     * by class: a trait's helper is declared nested in the trait under a name containing a {@code $}, so a
     * name rebuilt from the class -- the trait's, a {@code $}, the helper's simple name -- drops the
     * {@code Trait} in the middle and names a class that does not exist.
     */
    private static final class Findings {

        private final Set<String> enums = new LinkedHashSet<>();

        private final Set<String> coercedTo = new LinkedHashSet<>();

        private final Set<String> traitHelpers = new LinkedHashSet<>();

        private final Map<String, Set<String>> injectedFields = new LinkedHashMap<>();

        private final Map<String, Set<MethodSignature>> invokedMethods = new LinkedHashMap<>();

        /** Every class read, with where it was read from, to be asked whether it is a Groovy class. */
        private final Map<String, Resource> classes = new LinkedHashMap<>();

        private void register(RuntimeHints hints, ClassLoader loader) {
            for (String type : enums) {
                if (load(type, loader) != null) {
                    hints.reflection().registerType(TypeReference.of(type), MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
                }
            }
            for (String type : coercedTo) {
                Class<?> coerced = load(type, loader);
                if (coerced != null && coerced.isInterface()) {
                    hints.proxies().registerJdkProxy(TypeReference.of(type));
                }
            }
            for (String type : traitHelpers) {
                if (load(type, loader) != null) {
                    hints.reflection().registerType(TypeReference.of(type), MemberCategory.INVOKE_DECLARED_METHODS);
                }
            }
            injectedFields.forEach((type, fields) -> {
                if (load(type, loader) != null) {
                    hints.reflection().registerType(TypeReference.of(type), builder -> fields.forEach(builder::withField));
                }
            });
            invokedMethods.forEach((type, methods) -> {
                if (load(type, loader) != null) {
                    hints.reflection().registerType(TypeReference.of(type), builder -> methods.forEach(method ->
                            builder.withMethod(method.name(), method.parameterTypes(), ExecutableMode.INVOKE)));
                }
            });
            int groovyClasses = 0;
            for (Map.Entry<String, Resource> found : classes.entrySet()) {
                if (isGroovyClass(found.getKey(), found.getValue(), loader)) {
                    hints.reflection().registerType(TypeReference.of(found.getKey()));
                    groovyClasses++;
                }
            }
            logger.debug("Registered " + groovyClasses + " Groovy classes, " + enums.size() + " Groovy enums, " +
                    traitHelpers.size() + " trait helpers, " +
                    "proxies for " + coercedTo.size() + " interfaces closures are coerced to, and the injected " +
                    "members of " + (injectedFields.size() + invokedMethods.size()) + " classes");
        }
    }

    /**
     * Collects whether a class is a Groovy enum or a trait's helper, the interfaces its closures are cast to,
     * and the members it has annotated for Spring.
     */
    private static final class FindingClassVisitor extends ClassVisitor {

        private final Findings findings;

        private final Resource resource;

        private String className = "";

        private FindingClassVisitor(Findings findings, Resource resource) {
            super(SpringAsmInfo.ASM_VERSION);
            this.findings = findings;
            this.resource = resource;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            className = ClassUtils.convertResourcePathToClassName(name);
            findings.classes.put(className, resource);
            if (ENUM.equals(superName) && interfaces != null && Arrays.asList(interfaces).contains(GROOVY_OBJECT)) {
                findings.enums.add(className);
            }
            for (String suffix : TRAIT_HELPER_SUFFIXES) {
                if (className.endsWith(suffix)) {
                    findings.traitHelpers.add(className);
                }
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return new FieldVisitor(SpringAsmInfo.ASM_VERSION) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    if (SPRING_ANNOTATIONS.contains(annotation)) {
                        findings.injectedFields.computeIfAbsent(className, type -> new LinkedHashSet<>()).add(name);
                    }
                    return null;
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            return new MethodVisitor(SpringAsmInfo.ASM_VERSION) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    if (SPRING_ANNOTATIONS.contains(annotation)) {
                        List<TypeReference> parameterTypes = new ArrayList<>();
                        for (Type parameter : Type.getArgumentTypes(descriptor)) {
                            parameterTypes.add(TypeReference.of(parameter.getClassName()));
                        }
                        findings.invokedMethods.computeIfAbsent(className, type -> new LinkedHashSet<>())
                                .add(new MethodSignature(name, parameterTypes));
                    }
                    return null;
                }

                @Override
                public void visitInvokeDynamicInsn(String methodName, String methodDescriptor, Handle bootstrap,
                        Object... bootstrapArguments) {
                    if (!CAST.equals(methodName) || !INDY_INTERFACE.equals(bootstrap.getOwner())) {
                        return;
                    }
                    Type[] arguments = Type.getArgumentTypes(methodDescriptor);
                    Type target = Type.getReturnType(methodDescriptor);
                    if (arguments.length == 1 && CLOSURE.equals(arguments[0].getDescriptor()) &&
                            target.getSort() == Type.OBJECT) {
                        findings.coercedTo.add(target.getClassName());
                    }
                }
            };
        }

    }

}
