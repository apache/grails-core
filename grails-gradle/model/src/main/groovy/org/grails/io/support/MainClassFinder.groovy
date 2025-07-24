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

package org.grails.io.support

import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

import groovy.transform.CompileStatic

import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import groovyjarjarasm.asm.Type

import grails.util.BuildSettings

/**
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class MainClassFinder {

    private static final Type STRING_ARRAY_TYPE = Type.getType(String[].class)

    private static final Type MAIN_METHOD_TYPE = Type.getMethodType(Type.VOID_TYPE, STRING_ARRAY_TYPE)

    private static final String MAIN_METHOD_NAME = "main"

    static final Map<String, MainClassHolder> mainClasses = new ConcurrentHashMap<>()

    public static final String ROOT_FOLDER_PATH = "build/classes/main"

    /**
     * Searches for the main class relative to the give path that is within the project tree
     *
     * @param path The path as a URI
     * @param supportCaching Whether to cache the result for future calls
     * @return The name of the main class
     */
    static String searchMainClass(URI path, boolean supportCaching = true) {
        if (!path) {
            return null
        }

        def pathStr = path.toString()
        if (supportCaching && mainClasses.containsKey(pathStr)) {
            def holder = mainClasses.get(pathStr)
            if(holder.classFile.exists()) {
                return holder.className
            }
            else {
                mainClasses.remove(pathStr)
            }
        }

        try {
            File file = path ? Paths.get(path).toFile() : null
            def rootDir = findRootDirectory(file)

            def classesDir = BuildSettings.CLASSES_DIR
            Collection<File> searchDirs
            if (classesDir == null) {
                searchDirs = []
            } else {
                searchDirs = [classesDir]
            }

            if (rootDir) {
                def rootClassesDir = new File(rootDir, BuildSettings.BUILD_CLASSES_PATH)
                if (rootClassesDir.exists()) {
                    searchDirs << rootClassesDir
                }

                rootClassesDir = new File(rootDir, "build/classes/groovy/main")
                if (rootClassesDir.exists()) {
                    searchDirs << rootClassesDir
                }
            }

            MainClassHolder holder = null
            for (File dir in searchDirs) {
                holder = findMainClass(dir, supportCaching)
                if (holder) break
            }

            if (supportCaching && holder != null) {
                mainClasses.put(pathStr, holder)
            }

            return holder.className
        } catch (Throwable e) {
            return null
        }
    }

    private static File findRootDirectory(File file) {
        if (file) {
            def parent = file.parentFile

            while (parent != null) {
                if (new File(parent, "build.gradle").exists() || new File(parent, "grails-app").exists()) {
                    return parent
                } else {
                    parent = parent.parentFile
                }
            }
        }
        return null
    }

    static MainClassHolder findMainClass(File rootFolder = BuildSettings.CLASSES_DIR, boolean supportCaching = true) {
        if (rootFolder == null) {
            // try current directory
            rootFolder = new File(ROOT_FOLDER_PATH)
        }

        if (!rootFolder.exists()) {
            return null // nothing to do
        }

        if (!rootFolder.isDirectory()) {
            throw new IllegalArgumentException("Invalid root folder '$rootFolder'")
        }

        final String rootFolderCanonicalPath = rootFolder.canonicalPath
        if (supportCaching && mainClasses.containsKey(rootFolderCanonicalPath)) {
            def holder = mainClasses.get(rootFolderCanonicalPath)
            if(holder.classFile.exists()) {
                return holder
            }
        }
        ArrayDeque<File> stack = new ArrayDeque<>()
        stack.push rootFolder

        while (!stack.empty) {
            final File file = stack.pop()
            if (file.isFile()) {
                InputStream inputStream = file.newInputStream()
                try {
                    def classReader = new ClassReader(inputStream)
                    if (isMainClass(classReader)) {
                        MainClassHolder holder = new MainClassHolder()
                        holder.className = classReader.getClassName().replace('/', '.').replace('\\', '.')
                        holder.classFile = file

                        if(supportCaching) {
                            mainClasses.put(rootFolderCanonicalPath, holder)
                        }

                        return holder
                    }
                } finally {
                    inputStream?.close()
                }
            }
            if (file.isDirectory()) {
                Arrays.stream(file.listFiles())
                        .filter(MainClassFinder::isClassFile)
                        .forEach(stack::push)
            }
        }
        return null
    }

    protected static boolean isClassFile(File f) {
        (f.isDirectory() && !f.name.startsWith('.') && !f.hidden) ||
                (f.isFile() && f.name.endsWith(GrailsResourceUtils.CLASS_EXTENSION))
    }


    protected static boolean isMainClass(ClassReader classReader) {
        if (classReader.superName?.startsWith('grails/boot/config/')) {
            def mainMethodFinder = new MainMethodFinder()
            classReader.accept(mainMethodFinder, ClassReader.SKIP_CODE)
            return mainMethodFinder.found
        }
        return false
    }

    @CompileStatic
    static class MainMethodFinder extends ClassVisitor {

        boolean found = false

        MainMethodFinder() {
            super(Opcodes.ASM9)
        }

        @Override
        MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (!found) {
                if (isAccess(access, Opcodes.ACC_PUBLIC, Opcodes.ACC_STATIC)
                        && MAIN_METHOD_NAME.equals(name)
                        && MAIN_METHOD_TYPE.getDescriptor().equals(desc)) {


                    this.found = true
                }
            }
            return null
        }


        private boolean isAccess(int access, int ... requiredOpsCodes) {
            return !requiredOpsCodes.any { int requiredOpsCode -> (access & requiredOpsCode) == 0 }
        }
    }
}
