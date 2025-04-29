package org.grails.datastore.gorm.utils;

import org.springframework.core.io.Resource;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;

import java.io.IOException;

/**
 * A {@link CachingMetadataReaderFactory} that only reads annotations and not the whole class body
 */
class AnnotationMetadataReaderFactory extends CachingMetadataReaderFactory {
    public AnnotationMetadataReaderFactory(ClassLoader classLoader) {
        super(classLoader);
    }

    @Override
    public MetadataReader getMetadataReader(Resource resource) throws IOException {
        return new AnnotationMetadataReader(resource, getResourceLoader().getClassLoader(), false);
    }
}
