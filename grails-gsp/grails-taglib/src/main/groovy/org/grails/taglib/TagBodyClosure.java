/*
 * Copyright 2004-2005 Graeme Rocher
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
package org.grails.taglib;

import groovy.lang.Binding;
import groovy.lang.Closure;
import org.grails.taglib.encoder.OutputContext;
import org.grails.taglib.encoder.OutputEncodingStack;
import org.springframework.util.Assert;

import java.io.Writer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the body of a tag and captures its output returning the result
 * when invoked.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
@SuppressWarnings("rawtypes")
public class TagBodyClosure extends Closure {
    private static final long serialVersionUID = 4396762064131558457L;
    private static final Class[] PARAMETER_TYPES = new Class[]{Map.class};
    private Closure<?> bodyClosure;
    private OutputContext outputContext;

    public TagBodyClosure(Object owner, OutputContext outputContext, Closure<?> bodyClosure) {
        this(owner, outputContext, bodyClosure, false);
    }

    public TagBodyClosure(Object owner, OutputContext outputContext,
                          Closure<?> bodyClosure, boolean changeBodyClosureOwner) {
        super(owner);

        Assert.notNull(bodyClosure, "Argument [bodyClosure] cannot be null!");
        Assert.notNull(outputContext, "Argument [outputContext] cannot be null!");

        if (changeBodyClosureOwner && bodyClosure != null && !(bodyClosure instanceof TagOutput.ConstantClosure)) {
            this.bodyClosure = bodyClosure.rehydrate(bodyClosure.getDelegate(), owner, bodyClosure.getThisObject());
            this.bodyClosure.setResolveStrategy(OWNER_ONLY);
        } else {
            this.bodyClosure = bodyClosure;
        }
        this.outputContext = outputContext;
    }

    private Object captureClosureOutput(Object args, boolean hasArgument) {
        final GroovyPageTagWriter capturedOut = new GroovyPageTagWriter();
        Binding currentBinding = outputContext.getBinding();
        Map<String,Object> savedVariablesMap = null;
        Object originalIt = null;
        try {
            pushCapturedOut(capturedOut);

            Object bodyResult;

            if (currentBinding != null) {
                if (hasArgument) {
                    originalIt = saveItVariable(currentBinding, args);
                }

                if (args instanceof Map && ((Map)args).size() > 0) {
                    // The body can be passed a set of variables as a map that
                    // are then made available in the binding. This allows the
                    // contents of the body to reference any of these variables
                    // directly.
                    //
                    // For example, body(foo: 1, bar: 'test') would allow this
                    // GSP fragment to work:
                    //
                    // <td>Foo: ${foo} and bar: ${bar}</td>
                    //
                    // Note that any variables with the same name as one of the
                    // new ones will be overridden for the scope of the host
                    // tag's body.

                    // GRAILS-2675: Copy the current binding so that we can
                    // restore
                    // it to its original state.

                    // Binding is only changed currently when body gets a map
                    // argument
                    savedVariablesMap = addAndSaveVariables(currentBinding, (Map)args);
                }
            }
            bodyResult = executeClosure(args);

            if (!capturedOut.isUsed() && bodyResult != null && !(bodyResult instanceof Writer)) {
                return bodyResult;
            }
            return capturedOut.getBuffer();
        }
        finally {
            if (currentBinding != null) {
                restoreVariables(currentBinding, savedVariablesMap);
                if (hasArgument) {
                    restoreItVariable(currentBinding, originalIt);
                }
            }
            popCapturedOut();
        }
    }

    /**
     * Sets "it" variable to binding and returns the previous value.
     *
     * changing "it" variable is required to support refering to body argument with "it"; that was supported pre Grails 2.0
     * "it" is in binding because g:each loops are converted to ordinary for loops in Grails 2.0 with a generated variable name (if no variable name is specified)
     */
    @SuppressWarnings("unchecked")
    private Object saveItVariable(Binding currentBinding, Object args) {
        Object originalIt;
        Map<String,Object> variablesMap = (currentBinding instanceof AbstractTemplateVariableBinding) ? ((AbstractTemplateVariableBinding)currentBinding)
                .getVariablesMap() : currentBinding.getVariables();
        originalIt = variablesMap.get("it");
        variablesMap.put("it", args);
        return originalIt;
    }

    /**
     * Restores "it" variable to binding.
     */
    @SuppressWarnings("unchecked")
    private void restoreItVariable(Binding currentBinding, Object originalIt) {
        Map<String,Object> variablesMap = (currentBinding instanceof AbstractTemplateVariableBinding) ? ((AbstractTemplateVariableBinding)currentBinding)
                .getVariablesMap() : currentBinding.getVariables();
        variablesMap.put("it", originalIt);
    }

    /**
     * Adds variables to binding and returns a map with previous values.
     */
    @SuppressWarnings("unchecked")
    private Map<String,Object> addAndSaveVariables(Binding binding, Map args) {
        Map<String,Object> savedVariablesMap = new LinkedHashMap<String,Object>();
        for (Iterator<Object> i = args.keySet().iterator(); i.hasNext(); ) {
            String varname = String.valueOf(i.next());
            savedVariablesMap.put(varname, binding.getVariable(varname));
        }
        if (binding instanceof AbstractTemplateVariableBinding) {
            ((AbstractTemplateVariableBinding)binding).addMap(args);
        } else {
            for (Iterator<Map.Entry> i = args.entrySet().iterator(); i.hasNext(); ) {
                Map.Entry entry = i.next();
                binding.setVariable(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return savedVariablesMap;
    }

    /**
     * Restores variables to binding from a map with previous values.
     */
    private void restoreVariables(Binding binding, Map<String, Object> savedVariablesMap) {
        if (savedVariablesMap != null) {
            for (Map.Entry<String, Object> entry : savedVariablesMap.entrySet()) {
                binding.setVariable(entry.getKey(), entry.getValue());
            }
        }
    }

    private void popCapturedOut() {
        if (outputContext != null) {
            OutputEncodingStack.currentStack(outputContext).pop();
        }
        else {
            OutputEncodingStack.currentStack().pop();
        }
    }

    private void pushCapturedOut(GroovyPageTagWriter capturedOut) {
        if (outputContext != null) {
            OutputEncodingStack.currentStack(outputContext).push(capturedOut);
        }
        else {
            OutputEncodingStack.currentStack().push(capturedOut);
        }
    }

    private Object executeClosure(Object args) {
        if (args != null && bodyClosure.getMaximumNumberOfParameters() > 0) {
            return bodyClosure.call(args);
        }
        return bodyClosure.call();
    }

    public Object doCall() {
        return captureClosureOutput(null, false);
    }

    public Object doCall(Object arguments) {
        return captureClosureOutput(arguments, true);
    }

    @Override
    public Object call() {
        return captureClosureOutput(null, false);
    }

    @Override
    public Object call(Object... args) {
        return captureClosureOutput(args, args != null && args.length > 0);
    }

    @Override
    public Object call(Object arguments) {
        return captureClosureOutput(arguments, true);
    }

    @Override
    public int getMaximumNumberOfParameters() {
        return 1;
    }

    @Override
    public Class[] getParameterTypes() {
        return PARAMETER_TYPES;
    }

    public Closure<?> getBodyClosure() {
        return bodyClosure;
    }
}
