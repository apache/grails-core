package org.grails.plugins.databasemigration.testing

import groovy.transform.TupleConstructor
import org.grails.plugins.databasemigration.testing.annotation.OutputCapture
import org.spockframework.runtime.IStandardStreamsListener
import org.spockframework.runtime.InvalidSpecException
import org.spockframework.runtime.StandardStreamsCapturer
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FieldInfo
import org.spockframework.runtime.model.SpecInfo

class OutputCaptureExtension implements IAnnotationDrivenExtension<OutputCapture> {

    private final Map<FieldInfo, ByteArrayOutputStream> fieldBuffers = new HashMap<FieldInfo, ByteArrayOutputStream>(1);

    @Override
    void visitFieldAnnotation(OutputCapture annotation, FieldInfo field) {
        if (!field.type.isAssignableFrom(Object.class)) {
            throw new InvalidSpecException("""Wrong type for field %s.
                |@OutputCapture can only be placed on fields assignableFrom Object.
                |For example
                |@OutputCapture Object output
                |""".stripMargin()).withArgs(field.name)
        }
        this.fieldBuffers[field] = new ByteArrayOutputStream()
    }


    @Override
    void visitSpec(SpecInfo spec) {
        def capturer = new StandardStreamsCapturer()
        capturer.addStandardStreamsListener(new Listener(fieldBuffers))
        capturer.start()
        spec.addSharedInitializerInterceptor({ IMethodInvocation invocation ->
            fieldBuffers.keySet().each { field ->
                if (field.shared) {
                    fieldBuffers[field] = new ByteArrayOutputStream()
                    invocation.instance.metaClass.setProperty(invocation.instance, field.reflection.name, createNewOutput(fieldBuffers[field]))
                }
            }
            invocation.proceed()
        })
        spec.addInitializerInterceptor({ IMethodInvocation invocation ->
            fieldBuffers.keySet().each { field ->
                if (!field.shared) {
                    fieldBuffers[field] = new ByteArrayOutputStream()
                    invocation.instance.metaClass.setProperty(invocation.instance, field.reflection.name, createNewOutput(fieldBuffers[field]))
                }
            }
            invocation.proceed()
        })
        spec.addCleanupSpecInterceptor({ IMethodInvocation invocation ->
            capturer.stop()
            invocation.proceed()
        })
    }

    private Object createNewOutput(ByteArrayOutputStream baos) {
        new Object() {

            boolean contains(CharSequence s) {
                this.toString().contains(s)
            }

            @Override
            String toString() {
                return baos.toString("UTF-8")
            }
        }
    }

    @TupleConstructor(includeFields = true)
    static class Listener implements IStandardStreamsListener {

        private Map<FieldInfo, ByteArrayOutputStream> fieldBuffers

        @Override
        void standardOut(String message) {
            fieldBuffers.values().each { baos ->
                new PrintStream(baos).append(message)
            }
        }

        @Override
        void standardErr(String message) {
            fieldBuffers.values().each { baos ->
                new PrintStream(baos).append(message)
            }
        }

    }
}
