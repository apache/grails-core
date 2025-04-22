/*
 * Copyright 2014 the original author or authors.
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
package org.grails.compiler.injection;

import grails.compiler.ast.SupportsClassNode;
import grails.compiler.traits.TraitInjector;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.trait.TraitComposer;
import org.grails.core.io.support.GrailsFactoriesLoader;

import java.util.*;

/**
 *
 * @author Jeff Brown
 * @since 3.0
 *
 */
public class TraitInjectionUtils {

    private static List<TraitInjector> traitInjectors;

    private TraitInjectionUtils() {
    }

    private static void extendTraits(CompilationUnit unit, SourceUnit source, ClassNode classNode) {
        if(unit.getPhase() != CompilePhase.SEMANTIC_ANALYSIS.getPhaseNumber()) {
            TraitComposer.doExtendTraits(classNode, source, unit);
        }
    }

    private static boolean addTrait(ClassNode classNode, Class trait) {
        boolean traitsAdded = false;
        boolean implementsTrait = false;
        boolean traitNotLoaded = false;
        ClassNode traitClassNode = ClassHelper.make(trait);
        try {
            implementsTrait = classNode.declaresInterface(traitClassNode);
        } catch (Throwable e) {
            // if we reach this point, the trait injector could not be loaded due to missing dependencies (for example missing servlet-api). This is ok, as we want to be able to compile against non-servlet environments.
            traitNotLoaded = true;
        }
        if (!implementsTrait && !traitNotLoaded) {
            final GenericsType[] genericsTypes = traitClassNode.getGenericsTypes();
            final Map<String, ClassNode> parameterNameToParameterValue = new LinkedHashMap<String, ClassNode>();
            if(genericsTypes != null) {
                for(GenericsType gt : genericsTypes) {
                    parameterNameToParameterValue.put(gt.getName(), classNode);
                }
            }
            classNode.addInterface(GrailsASTUtils.replaceGenericsPlaceholders(traitClassNode, parameterNameToParameterValue, classNode));
            traitsAdded = true;
        }
        return traitsAdded;
    }

    public static void injectTrait(CompilationUnit unit, SourceUnit source, ClassNode classNode, Class trait) {
        boolean traitsAdded = addTrait(classNode, trait);
        if(traitsAdded) {
            extendTraits(unit, source, classNode);
        }
    }

    private static void doInjectionInternal(CompilationUnit unit, SourceUnit source, ClassNode classNode,
            List<TraitInjector> injectorsToUse) {
        boolean traitsAdded = false;

        for (TraitInjector injector : injectorsToUse) {
            Class<?> trait = injector.getTrait();
            if (addTrait(classNode, trait)) {
                traitsAdded = true;
            }
        }
        if(traitsAdded) {
            extendTraits(unit, source, classNode);
        }
    }

    private static List<TraitInjector> getTraitInjectors() {
        if(traitInjectors == null) {
            traitInjectors = GrailsFactoriesLoader.loadFactories(TraitInjector.class);

            traitInjectors = TraitInjectionSupport.resolveTraitInjectors(traitInjectors);
        }
        if(traitInjectors != null) {
            return Collections.unmodifiableList(traitInjectors);
        }
        else {
            return Collections.emptyList();
        }
    }
    
    public static void processTraitsForNode(final SourceUnit sourceUnit, 
                                            final ClassNode cNode,
                                            final String artefactType, 
                                            final CompilationUnit compilationUnit) {
        final List<TraitInjector> traitInjectors = getTraitInjectors();
        final List<TraitInjector> injectorsToUse = new ArrayList<TraitInjector>();
        for (final TraitInjector injector : traitInjectors) {
            final List<String> artefactTypes = Arrays.asList(injector.getArtefactTypes());

            boolean supportsClassNode = true;

            if (injector instanceof SupportsClassNode) {
                supportsClassNode = ((SupportsClassNode)injector).supports(cNode);
            }

            if (artefactTypes.contains(artefactType) && supportsClassNode) {
                injectorsToUse.add(injector);
            }
        }
        try {
            if(injectorsToUse.size() > 0) {
                doInjectionInternal(compilationUnit, sourceUnit, cNode, injectorsToUse);
            }
        } catch (RuntimeException e) {
            try {
                System.err.println("Error occurred calling Trait injector ["+TraitInjectionUtils.class.getName()+"]: "
                        + e.getMessage());
                e.printStackTrace();
            } catch (Throwable t) {
                // ignore it
            }
            throw e;
        }
    }
}
