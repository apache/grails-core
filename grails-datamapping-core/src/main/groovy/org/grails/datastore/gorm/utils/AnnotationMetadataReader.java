/*
 * Copyright 2016-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.gorm.utils;

import org.springframework.asm.AnnotationVisitor;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.classreading.MetadataReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A more limited version of Spring's annotation reader that only reads annotations on classes
 *
 * @author Graeme Rocher
 * @since 3.1.13
 */
public class AnnotationMetadataReader implements MetadataReader {
    private final Resource resource;

    private final ClassMetadata classMetadata;

    private final AnnotationMetadata annotationMetadata;

    /**
     * Constructs a new annotation metadata reader
     *
     * @param resource The resource
     * @param classLoader The classloader
     * @param readAttributeValues Whether to read the attributes in addition or just the annotation class names
     * @throws IOException
     */
    public AnnotationMetadataReader(Resource resource, ClassLoader classLoader, boolean readAttributeValues) throws IOException {
        InputStream is = new BufferedInputStream(resource.getInputStream());
        ClassReader classReader;
        try {
            classReader = new ClassReader(is);
        }
        catch (IllegalArgumentException ex) {
            throw new IOException("ASM ClassReader failed to parse class file - " +
                    "probably due to a new Java class file version that isn't supported yet: " + resource, ex);
        }
        finally {
            is.close();
        }


        AnnotationMetadataReadingVisitor visitor;

        if(readAttributeValues) {
            visitor = new AnnotationMetadataReadingVisitor(classLoader);
        }
        else {
            visitor = new AnnotationMetadataReadingVisitor(classLoader) {
                @Override
                public AnnotationVisitor visitAnnotation(final String desc, boolean visible) {
                    String className = Type.getType(desc).getClassName();
                    this.annotationSet.add(className);
                    return new EmptyAnnotationVisitor();
                }
            };
        }
        classReader.accept(visitor, ClassReader.SKIP_DEBUG);

        this.annotationMetadata = visitor;
        // (since AnnotationMetadataReadingVisitor extends ClassMetadataReadingVisitor)
        this.classMetadata = visitor;
        this.resource = resource;
    }


    @Override
    public Resource getResource() {
        return this.resource;
    }

    @Override
    public ClassMetadata getClassMetadata() {
        return this.classMetadata;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return this.annotationMetadata;
    }

    private static class EmptyAnnotationVisitor extends AnnotationVisitor {

        EmptyAnnotationVisitor() {
            super(loadAsmVersion());
        }

        private static int loadAsmVersion()  {
            try {
                return (int)SpringAsmInfo.class.getField("ASM_VERSION").get(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            return this;
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return this;
        }
    }
}
