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
package org.grails.compiler.injection;

import grails.compiler.ast.AstTransformer;
import grails.compiler.ast.ClassInjector;
import grails.compiler.ast.GlobalClassInjector;
import groovy.lang.GroovyResourceLoader;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.grails.io.support.FileSystemResource;
import org.grails.io.support.PathMatchingResourcePatternResolver;
import org.grails.io.support.Resource;
import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * A Groovy compiler injection operation that uses a specified array of
 * ClassInjector instances to attempt AST injection.
 *
 * @author Graeme Rocher
 * @since 0.6
 */
public class GrailsAwareInjectionOperation extends CompilationUnit.PrimaryClassNodeOperation {

    private static final String INJECTOR_SCAN_PACKAGE = "org.grails.compiler";
    private static final String INJECTOR_CODEHAUS_SCAN_PACKAGE = "org.codehaus.groovy.grails.compiler";

    private static ClassInjector[] classInjectors;
    private static ClassInjector[] globalClassInjectors;
    private ClassInjector[] localClassInjectors;

    public GrailsAwareInjectionOperation() {
        initializeState();
    }

    public GrailsAwareInjectionOperation(ClassInjector[] classInjectors) {
        this();
        localClassInjectors = classInjectors;
    }


    public static ClassInjector[] getClassInjectors() {
        if (classInjectors == null) {
            initializeState();
        }
        return classInjectors;
    }

    public static ClassInjector[] getGlobalClassInjectors() {
        if (classInjectors == null) {
            initializeState();
        }
        return globalClassInjectors;
    }

    public ClassInjector[] getLocalClassInjectors() {
        if (localClassInjectors == null) {
            return getClassInjectors();
        }
        return localClassInjectors;
    }

    private static void initializeState() {
        if (classInjectors != null) {
            return;
        }


        String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                ClassUtils.convertClassNameToResourcePath(INJECTOR_CODEHAUS_SCAN_PACKAGE) + "/**/*.class";

        String pattern2 = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                ClassUtils.convertClassNameToResourcePath(INJECTOR_SCAN_PACKAGE) + "/**/*.class";

        ClassLoader classLoader = GrailsAwareInjectionOperation.class.getClassLoader();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        Resource[] resources;
        try {
            resources = scanForPatterns(resolver, pattern2, pattern);
            if(resources.length == 0) {
                classLoader = Thread.currentThread().getContextClassLoader();
                resolver = new PathMatchingResourcePatternResolver(classLoader);
                resources = scanForPatterns(resolver, pattern2, pattern);
            }
            final List<ClassInjector> injectors = new ArrayList<ClassInjector>();
            final List<ClassInjector> globalInjectors = new ArrayList<ClassInjector>();
            final Set<Class> injectorClasses = new HashSet<Class>();
            for (Resource resource : resources) {
                // ignore not readable classes and closures
                if(!resource.isReadable() || resource.getFilename().contains("$_")) continue;
                InputStream inputStream = resource.getInputStream();
                try {

                    final ClassReader classReader = new ClassReader(inputStream);
                    final String astTransformerClassName = AstTransformer.class.getSimpleName();
                    final ClassLoader finalClassLoader = classLoader;
                    classReader.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                            try {
                                if(visible && desc.contains(astTransformerClassName)) {
                                    Class<?> injectorClass = finalClassLoader.loadClass(classReader.getClassName().replace('/', '.'));
                                    if(injectorClasses.contains(injectorClass)) return super.visitAnnotation(desc, true);
                                    if (ClassInjector.class.isAssignableFrom(injectorClass)) {

                                        injectorClasses.add(injectorClass);
                                        ClassInjector classInjector = (ClassInjector) injectorClass.newInstance();
                                        injectors.add(classInjector);
                                        if(GlobalClassInjector.class.isAssignableFrom(injectorClass)) {
                                            globalInjectors.add(classInjector);
                                        }
                                    }
                                }
                            } catch (ClassNotFoundException e) {
                                // ignore
                            } catch (InstantiationException e) {
                                // ignore
                            } catch (IllegalAccessException e) {
                                // ignore
                            }
                            return super.visitAnnotation(desc, visible);
                        }
                    }, ClassReader.SKIP_CODE);

                } catch (IOException e) {
                    // ignore
                } catch(NoClassDefFoundError e) {
                    // ignore
                }
                finally {
                    inputStream.close();
                }


            }
            Collections.sort(injectors, new Comparator<ClassInjector>() {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                public int compare(ClassInjector classInjectorA, ClassInjector classInjectorB) {
                    if (classInjectorA instanceof Comparable) {
                        return ((Comparable)classInjectorA).compareTo(classInjectorB);
                    }
                    return 0;
                }
            });
            classInjectors = injectors.toArray(new ClassInjector[injectors.size()]);
            globalClassInjectors = globalInjectors.toArray(new ClassInjector[globalInjectors.size()]);
        } catch (IOException e) {
            // ignore
        }


    }

    private static Resource[] scanForPatterns(PathMatchingResourcePatternResolver resolver, String...patterns) throws IOException {
        List<Resource> results = new ArrayList<Resource>();
        for(String pattern : patterns) {
            results.addAll( Arrays.asList(resolver.getResources(pattern)) );
        }
        return results.toArray(new Resource[results.size()]);
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) throws CompilationFailedException {

        URL url = null;
        final String filename = source.getName();
        Resource resource = new FileSystemResource(filename);
        if (resource.exists()) {
            try {
                url = resource.getURL();
            } catch (IOException e) {
                // ignore
            }
        }

        ClassInjector[] classInjectors1 = getLocalClassInjectors();
        if (classInjectors1 == null || classInjectors1.length == 0) {
            classInjectors1 = getClassInjectors();
        }
        for (ClassInjector classInjector : classInjectors1) {
            if (classInjector.shouldInject(url)) {
                classInjector.performInjection(source, context, classNode);
            }
        }
    }
}
