/* Copyright 2013 the original author or authors.
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
package org.grails.orm.hibernate.cfg;

import java.lang.reflect.Field;

import org.hibernate.cfg.Configuration;
import org.hibernate.id.SequenceGenerator;
import org.hibernate.id.factory.internal.DefaultIdentifierGeneratorFactory;
import org.springframework.util.ReflectionUtils;

/**
 * Hibernate IdentifierGeneratorFactory that prefers sequence-identity generator over sequence generator
 * 
 * @author Lari Hotari
 */
public class GrailsIdentifierGeneratorFactory extends DefaultIdentifierGeneratorFactory {
    private static final long serialVersionUID = 1L;

    @Override
    public Class getIdentifierGeneratorClass(String strategy) {
        Class generatorClass = super.getIdentifierGeneratorClass(strategy);
        if("native".equals(strategy) && generatorClass == SequenceGenerator.class) {
            generatorClass = super.getIdentifierGeneratorClass("sequence-identity");
        }
        return generatorClass;
    }
    
    public static void applyNewInstance(Configuration cfg) throws IllegalArgumentException, IllegalAccessException {
        Field field = ReflectionUtils.findField(Configuration.class, "identifierGeneratorFactory");
        field.setAccessible(true);
        field.set(cfg, new GrailsIdentifierGeneratorFactory());
    }
}
