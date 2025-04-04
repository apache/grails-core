/*
 * Copyright 2002-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.gorm.utils;

import org.springframework.core.annotation.*;
import org.springframework.core.type.*;
import org.springframework.lang.Nullable;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@link AnnotationMetadata} implementation that uses standard reflection
 * to introspect a given {@link Class}.
 *
 * <p>Note: this class was ported to GORM 9 from Spring Framework 6.1 since the package filter can't be passed to the spring version.</p>
 *
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Chris Beams
 * @author Phillip Webb
 * @author Sam Brannen
 * @since 2.5
 * @deprecated As of Spring Framework 6.1, this class does not allow specifying the annotation filter.  An upstream pull request should be opened so this can be removed.
 */
@Deprecated
public class FilteredAnnotationMetadata extends StandardClassMetadata implements AnnotationMetadata {
    private final MergedAnnotations mergedAnnotations;

    @Nullable
    private Set<String> annotationTypes;


    /**
     * Create a new {@code StandardAnnotationMetadata} wrapper for the given Class.
     * @param introspectedClass the Class to introspect
     */
    public FilteredAnnotationMetadata(Class<?> introspectedClass, AnnotationFilter filter) {
        super(introspectedClass);
        this.mergedAnnotations = MergedAnnotations.from(introspectedClass,
                MergedAnnotations.SearchStrategy.INHERITED_ANNOTATIONS, RepeatableContainers.none(), filter);
    }


    @Override
    public MergedAnnotations getAnnotations() {
        return this.mergedAnnotations;
    }

    @Override
    public Set<String> getAnnotationTypes() {
        Set<String> annotationTypes = this.annotationTypes;
        if (annotationTypes == null) {
            annotationTypes = Collections.unmodifiableSet(AnnotationMetadata.super.getAnnotationTypes());
            this.annotationTypes = annotationTypes;
        }
        return annotationTypes;
    }

    @Override
    @Nullable
    public Map<String, Object> getAnnotationAttributes(String annotationName, boolean classValuesAsString) {
        return AnnotatedElementUtils.getMergedAnnotationAttributes(
                getIntrospectedClass(), annotationName, classValuesAsString, false);
    }

    @Override
    @Nullable
    public MultiValueMap<String, Object> getAllAnnotationAttributes(String annotationName, boolean classValuesAsString) {
        return AnnotatedElementUtils.getAllAnnotationAttributes(
                getIntrospectedClass(), annotationName, classValuesAsString, false);
    }

    @Override
    public boolean hasAnnotatedMethods(String annotationName) {
        if (AnnotationUtils.isCandidateClass(getIntrospectedClass(), annotationName)) {
            try {
                Method[] methods = org.springframework.util.ReflectionUtils.getDeclaredMethods(getIntrospectedClass());
                for (Method method : methods) {
                    if (isAnnotatedMethod(method, annotationName)) {
                        return true;
                    }
                }
            }
            catch (Throwable ex) {
                throw new IllegalStateException("Failed to introspect annotated methods on " + getIntrospectedClass(), ex);
            }
        }
        return false;
    }

    @Override
    public Set<MethodMetadata> getAnnotatedMethods(String annotationName) {
        Set<MethodMetadata> result = new LinkedHashSet<>(4);
        if (AnnotationUtils.isCandidateClass(getIntrospectedClass(), annotationName)) {
            org.springframework.util.ReflectionUtils.doWithLocalMethods(getIntrospectedClass(), method -> {
                if (isAnnotatedMethod(method, annotationName)) {
                    result.add(new StandardMethodMetadata(method));
                }
            });
        }
        return result;
    }

    @Override
    public Set<MethodMetadata> getDeclaredMethods() {
        Set<MethodMetadata> result = new LinkedHashSet<>(16);
        ReflectionUtils.doWithLocalMethods(getIntrospectedClass(), method ->
                result.add(new StandardMethodMetadata(method)));
        return result;
    }


    private static boolean isAnnotatedMethod(Method method, String annotationName) {
        return !method.isBridge() && method.getAnnotations().length > 0 &&
                AnnotatedElementUtils.isAnnotated(method, annotationName);
    }
}
