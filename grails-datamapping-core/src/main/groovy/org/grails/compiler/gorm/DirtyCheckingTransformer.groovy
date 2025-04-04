package org.grails.compiler.gorm

import grails.gorm.dirty.checking.DirtyCheck
import grails.gorm.dirty.checking.DirtyCheckedProperty
import groovy.transform.CompilationUnitAware
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.transform.sc.StaticCompilationVisitor
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.reflect.AstUtils
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.datastore.mapping.reflect.NameUtils
import org.grails.datastore.mapping.reflect.ReflectionUtils
import org.springframework.validation.annotation.Validated

import java.lang.reflect.Modifier

import static java.lang.reflect.Modifier.*
import static org.codehaus.groovy.ast.tools.GeneralUtils.*
import static org.grails.datastore.mapping.reflect.AstUtils.*

/**
 *
 * Transforms a domain class making it possible for the domain class to take responsibility of tracking changes to itself, thus removing the responsibility from the ORM system which would have to maintain parallel state
 * and compare the state of the domain class to the stored state. With this transformation the storage of the state is not necessary as the state is kept in the domain class itself
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class DirtyCheckingTransformer implements CompilationUnitAware {
    private static final String VOID = "void";
    private static final Class<?>[] EMPTY_JAVA_CLASS_ARRAY = [];
    private static final Class<?>[] OBJECT_CLASS_ARG = [Object.class];

    private static final ClassNode VALIDATION_CONSTRAINT_NODE
    public static final String METHOD_NAME_MARK_DIRTY = "markDirty"
    public static final ConstantExpression CONSTANT_NULL = new ConstantExpression(null)
    public static final ClassNode DIRTY_CHECKED_PROPERTY_CLASS_NODE = ClassHelper.make(DirtyCheckedProperty)
    public static final ClassNode DIRTY_CHECK_CLASS_NODE = ClassHelper.make(DirtyCheck)
    public static final AnnotationNode DIRTY_CHECKED_PROPERTY_ANNOTATION_NODE = new AnnotationNode(DIRTY_CHECKED_PROPERTY_CLASS_NODE)

    static {
        if(ClassUtils.isPresent("jakarta.validation.Constraint")) {
            try {
                VALIDATION_CONSTRAINT_NODE = ClassHelper.make(Class.forName("jakarta.validation.Constraint"))
            } catch (Throwable e) {
                VALIDATION_CONSTRAINT_NODE = null
            }
        }
        else {
            VALIDATION_CONSTRAINT_NODE = null
        }
    }


    protected CompilationUnit compilationUnit

    void setCompilationUnit(CompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit
    }

    void performInjectionOnAnnotatedClass(SourceUnit source, ClassNode classNode, Class traitToInject = DirtyCheckable) {
        // First add a local field that will store the change tracking state. The field is a simple list of property names that have changed
        // the field is only added to root clauses that extend from java.lang.Object
        final ClassNode changeTrackableClassNode = new ClassNode(traitToInject).getPlainNodeReference()
        if (traitToInject != DirtyCheckable) {
            changeTrackableClassNode.setSuperClass(new ClassNode(DirtyCheckable).getPlainNodeReference())
        }
        final MethodNode markDirtyMethodNode = changeTrackableClassNode.getMethod(METHOD_NAME_MARK_DIRTY, new Parameter(ClassHelper.STRING_TYPE, "propertyName"), new Parameter(ClassHelper.OBJECT_TYPE, "newValue"))


        ClassNode superClass = classNode.getSuperClass()
        boolean shouldWeave = superClass.equals(OBJECT_CLASS_NODE)

        ClassNode dirtyCheckableTrait = ClassHelper.make(traitToInject).getPlainNodeReference()
        if (traitToInject != DirtyCheckable) {
            dirtyCheckableTrait.setSuperClass(new ClassNode(DirtyCheckable).getPlainNodeReference())
        }
        
        while(!shouldWeave) {
            if(isDomainClass(superClass) || !superClass.getAnnotations(DIRTY_CHECK_CLASS_NODE).isEmpty()) {
                break
            }
            superClass = superClass.getSuperClass()
            if(superClass == null || superClass.equals(OBJECT_CLASS_NODE)) {
                shouldWeave = true
                break
            }
        }

        if(shouldWeave ) {

            classNode.addInterface(dirtyCheckableTrait)
            if(compilationUnit != null) {
                org.codehaus.groovy.transform.trait.TraitComposer.doExtendTraits(classNode, source, compilationUnit);
            }

        }

        PropertyNode transientPropertyNode = classNode.getProperty("transients")

        // Now we go through all the properties, if the property is a persistent property and change tracking has been initiated then we add to the setter of the property
        // code that will mark the property as dirty. Note that if the property has no getter we have to add one, since only adding the setter results in a read-only property
        final propertyNodes = classNode.getProperties()
        def staticCompilationVisitor = new StaticCompilationVisitor(source, classNode)
        Map<String, GetterAndSetter> gettersAndSetters = [:]
        boolean isJavaValidateable = false

        for (MethodNode mn in classNode.methods) {
            final methodName = mn.name
            if(!mn.isPublic() || mn.isStatic() || mn.isSynthetic() || mn.isAbstract()) continue

            if (isSetter(methodName, mn)) {
                String propertyName = NameUtils.getPropertyNameForGetterOrSetter(methodName)
                GetterAndSetter getterAndSetter = getGetterAndSetterForPropertyName(gettersAndSetters, propertyName)
                getterAndSetter.setter = mn
            } else if (isGetter(methodName, mn)) {
                String propertyName = NameUtils.getPropertyNameForGetterOrSetter(methodName)

                // if there are any jakarta.validation constraints present
                def annotationNodes = mn.annotations
                if(!isJavaValidateable && isAnnotatedWithJavaValidationApi(annotationNodes)) {
                    addAnnotationIfNecessary(classNode, Validated)
                    isJavaValidateable = true
                }

                GetterAndSetter getterAndSetter = getGetterAndSetterForPropertyName(gettersAndSetters, propertyName)
                getterAndSetter.getter = mn
            }
        }

        boolean hasVersion = false
        for (PropertyNode pn in propertyNodes) {
            final propertyName = pn.name
            if (!pn.isStatic() && pn.isPublic() && !NameUtils.isConfigurational(propertyName)) {
                if(isTransient(pn.modifiers) || isDefinedInTransientsNode(propertyName, transientPropertyNode) || isFinal(pn.modifiers)) continue

                // don't dirty check id or version
                if(propertyName == GormProperties.IDENTITY) {
                    continue
                }
                else if(propertyName == GormProperties.VERSION) {
                    hasVersion = true
                    continue
                }

                final GetterAndSetter getterAndSetter = gettersAndSetters[propertyName]
                final FieldNode propertyField = pn.getField()
                final List<AnnotationNode> allAnnotationNodes = pn.annotations + propertyField.annotations
                if(getterAndSetter?.getter != null) {
                    allAnnotationNodes.addAll(getterAndSetter.getter.annotations)
                }


                if(hasAnnotation(allAnnotationNodes, GormEntityTransformation.JPA_ID_ANNOTATION_NODE)) {
                    if(!propertyName.equals(GormProperties.IDENTITY) ) {
                        // if the property is a JPA @Id but the property name is not id add a transient getter to retrieve the id called getId
                        if(classNode.getField(GormProperties.IDENTITY) == null && gettersAndSetters[GormProperties.IDENTITY] == null) {
                            def getIdMethod = new MethodNode(
                                    "getId",
                                    Modifier.PUBLIC,
                                    pn.type.plainNodeReference,
                                    ZERO_PARAMETERS,
                                    null,
                                    GeneralUtils.returnS(GeneralUtils.varX(propertyField))
                            )
                            classNode.addMethod(getIdMethod)
                            getIdMethod.addAnnotation(GormEntityTransformation.JPA_TRANSIENT_ANNOTATION_NODE)
                        }
                    }

                    // skip dirty checking for JPA @Id
                    continue
                }
                if(hasAnnotation( allAnnotationNodes, GormEntityTransformation.JPA_VERSION_ANNOTATION_NODE)) {
                    hasVersion = true
                    // if the property is a JPA @Version but the property name is not version add a transient getter to retrieve the version called getVersion
                    if(classNode.getField(GormProperties.VERSION) == null && gettersAndSetters[GormProperties.VERSION] == null) {
                        def getVersionMethod = new MethodNode(
                                "getVersion",
                                Modifier.PUBLIC,
                                pn.type.plainNodeReference,
                                ZERO_PARAMETERS,
                                null,
                                GeneralUtils.returnS(GeneralUtils.varX(propertyField))
                        )
                        classNode.addMethod(getVersionMethod)
                        getVersionMethod.addAnnotation(GormEntityTransformation.JPA_TRANSIENT_ANNOTATION_NODE)
                    }
                    // skip dirty checking for JPA @Version
                    continue
                }

                // if there is no explicit getter and setter then one will be generated by Groovy, so we must add these to track changes
                if(getterAndSetter == null) {

                    if(!isJavaValidateable && isAnnotatedWithJavaValidationApi(allAnnotationNodes)) {
                        addAnnotationIfNecessary(classNode, Validated)
                        isJavaValidateable = true
                    }


                    // first add the getter
                    ClassNode returnType = resolvePropertyReturnType(pn, classNode)
                    boolean booleanProperty = ClassHelper.boolean_TYPE.getName().equals(returnType.getName()) || ClassHelper.Boolean_TYPE.getName().equals(returnType.getName())
                    String fieldName = propertyField.getName()
                    String getterName = NameUtils.getGetterName(propertyName, false)

                    MethodNode getter = classNode.getMethod(getterName, ZERO_PARAMETERS)
                    if(getter == null) {

                        getter = classNode.addMethod(getterName, PUBLIC, returnType, ZERO_PARAMETERS, null, returnS(varX(fieldName)))

                        getter.addAnnotation(DIRTY_CHECKED_PROPERTY_ANNOTATION_NODE)
                        staticCompilationVisitor.visitMethod(
                                getter
                        )
                        if(booleanProperty) {
                            classNode.addMethod(NameUtils.getGetterName(propertyName, true), PUBLIC, returnType, ZERO_PARAMETERS, null, returnS(varX(fieldName)))
                        }
                    }

                    // now add the setter that tracks changes. Each setters becomes:
                    // void setFoo(String foo) { markDirty("foo", foo); this.foo = foo }
                    addDirtyCheckingSetter(classNode, propertyName, fieldName, returnType, markDirtyMethodNode, staticCompilationVisitor)
                }
                else if(getterAndSetter.hasBoth()) {
                    // if both a setter and getter are present, we get hold of the setter and weave the markDirty method call into it
                    weaveIntoExistingSetter(propertyName, getterAndSetter, markDirtyMethodNode)
                    gettersAndSetters.remove(propertyName)
                }
                else {
                    if(getterAndSetter.setter != null) {
                        weaveIntoExistingSetter(propertyName, getterAndSetter, markDirtyMethodNode)
                        // there isn't both a getter and a setter then this is not a candidate for persistence, so we eliminate it from change tracking
                        gettersAndSetters.remove(propertyName)
                    }
                    else if(getterAndSetter.getter != null) {
                        String fieldName = propertyField.getName()
                        ClassNode returnType = resolvePropertyReturnType(pn, classNode)
                        addDirtyCheckingSetter(classNode, propertyName, fieldName, returnType, markDirtyMethodNode, staticCompilationVisitor)
                    }
                    else {
                        gettersAndSetters.remove(propertyName)
                    }
                }
            }
        }

        if(!hasVersion && ClassUtils.isPresent("grails.artefact.Artefact") && !classNode.getAnnotations(GormEntityTransformation.JPA_ENTITY_CLASS_NODE).isEmpty()) {
            // if the entity is a JPA and has no version property then add a transient one as a stub, this is more to satisfy Grails
            def getVersionMethod = new MethodNode(
                    "getVersion",
                    Modifier.PUBLIC,
                    ClassHelper.make(Long),
                    ZERO_PARAMETERS,
                    null,
                    GeneralUtils.returnS(GeneralUtils.constX(0))
            )
            classNode.addMethod(getVersionMethod)
            getVersionMethod.addAnnotation(GormEntityTransformation.JPA_TRANSIENT_ANNOTATION_NODE)
        }

        // We also need to search properties that are represented as getters with setters. This requires going through all the methods and finding getter/setter pairs that are public
        gettersAndSetters.each { String propertyName, GetterAndSetter getterAndSetter ->
            if(!NameUtils.isConfigurational(propertyName) && getterAndSetter.hasBoth()) {
                weaveIntoExistingSetter(propertyName, getterAndSetter, markDirtyMethodNode)
            }
        }
    }

    /**
     * Check if the property is defined in static transients block.
     *
     * @param propertyName The given name of property
     * @param transientPropertyNode The property node representing static transients
     * @return If the property is transient
     */
    private boolean isDefinedInTransientsNode(String propertyName, PropertyNode transientPropertyNode) {
        if (transientPropertyNode) {
            transientPropertyNode.isStatic() &&
                    transientPropertyNode.initialExpression instanceof ListExpression &&
                    ((ListExpression) transientPropertyNode.initialExpression).expressions.find { it instanceof ConstantExpression && it.value == propertyName }
        }
    }

    private ClassNode resolvePropertyReturnType(PropertyNode pn, ClassNode classNode) {
        ClassNode originalReturnType = pn.getType()
        ClassNode returnType
        if (!originalReturnType.getNameWithoutPackage().equals(VOID)) {
            if (ClassHelper.isPrimitiveType(originalReturnType.redirect())) {
                returnType = originalReturnType.getPlainNodeReference()
            } else {
                returnType = alignReturnType(classNode, originalReturnType);
            }
        } else {
            returnType = originalReturnType
        }
        returnType
    }

    private void addDirtyCheckingSetter(ClassNode classNode, String propertyName, String fieldName, ClassNode returnType, MethodNode markDirtyMethodNode, StaticCompilationVisitor staticCompilationVisitor) {
        final String setterName = NameUtils.getSetterName(propertyName)
        final Parameter setterParameter = param(returnType, propertyName)
        MethodNode setter = classNode.getMethod(setterName, setterParameter)
        if (setter == null) {
            final BlockStatement setterBody = new BlockStatement()
            MethodCallExpression markDirtyMethodCall = createMarkDirtyMethodCall(markDirtyMethodNode, propertyName, setterParameter)
            setterBody.addStatement(stmt(markDirtyMethodCall))
            setterBody.addStatement(assignS(propX(varX("this"), fieldName), varX(setterParameter)))

            setter = classNode.addMethod(setterName, PUBLIC, ClassHelper.VOID_TYPE, params(setterParameter), null, setterBody)
            setter.addAnnotation(DIRTY_CHECKED_PROPERTY_ANNOTATION_NODE)
            staticCompilationVisitor.visitMethod(
                    setter
            )
        }
    }

    protected boolean isAnnotatedWithJavaValidationApi(List<AnnotationNode> annotationNodes) {
        VALIDATION_CONSTRAINT_NODE != null && annotationNodes.any { AnnotationNode an -> an.classNode.getAnnotations(VALIDATION_CONSTRAINT_NODE) }
    }

    void performInjection(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if (classNode.annotations.any { AnnotationNode an -> an.classNode.name == 'grails.artefact.Artefact'}) return;

        performInjectionOnAnnotatedClass(source, classNode)
    }

    void performInjection(SourceUnit source, ClassNode classNode) {
        performInjection(source, null, classNode)
    }

    public boolean shouldInject(URL url) {
        return AstUtils.isDomainClass(url);
    }

    void performInjectionOnAnnotatedEntity(ClassNode classNode) {
        performInjectionOnAnnotatedClass(null, classNode)
    }


    private static ClassNode alignReturnType(final ClassNode receiver, final ClassNode originalReturnType) {
        ClassNode copiedReturnType
        if(originalReturnType.isGenericsPlaceHolder()) {
            copiedReturnType = originalReturnType.getPlainNodeReference();
            copiedReturnType.setName( originalReturnType.getName() )
            copiedReturnType.setGenericsPlaceHolder(true)
        }
        else {
            copiedReturnType = originalReturnType.getPlainNodeReference();
        }

        final genericTypes = originalReturnType.getGenericsTypes()
        if (genericTypes) {
            List<GenericsType> newGenericTypes = []

            for(GenericsType gt in genericTypes) {
                ClassNode[] upperBounds = null
                if (gt.upperBounds) {
                    upperBounds = gt.upperBounds.collect { ClassNode cn -> cn.plainNodeReference } as ClassNode[]
                }
                newGenericTypes << new GenericsType(gt.type.plainNodeReference, upperBounds, gt.lowerBound?.plainNodeReference)
            }
            copiedReturnType.setGenericsTypes(newGenericTypes as GenericsType[])
        }
        return copiedReturnType
    }
    protected void weaveIntoExistingSetter(String propertyName, GetterAndSetter getterAndSetter, MethodNode markDirtyMethodNode) {
        final MethodNode setterMethod = getterAndSetter.setter
        if(setterMethod.annotations.any { AnnotationNode an -> an.classNode.name == 'grails.persistence.PersistenceMethod'} ) return

        if(!setterMethod.getAnnotations(DIRTY_CHECKED_PROPERTY_CLASS_NODE)) {
            setterMethod.addAnnotation(DIRTY_CHECKED_PROPERTY_ANNOTATION_NODE)
        }
        else {
            // already woven
            return
        }
        MethodNode getter = getterAndSetter.getter
        if(getter != null && !getter.getAnnotations(DIRTY_CHECKED_PROPERTY_CLASS_NODE)) {
            getter.addAnnotation(DIRTY_CHECKED_PROPERTY_ANNOTATION_NODE)
        }
        final currentBody = setterMethod.code
        final setterParameter = setterMethod.getParameters()[0]
        MethodCallExpression markDirtyMethodCall = createMarkDirtyMethodCall(markDirtyMethodNode, propertyName, setterParameter)
        final newBody = block(
            stmt( markDirtyMethodCall ),
            currentBody
        )
        setterMethod.code = newBody
    }

    protected MethodCallExpression createMarkDirtyMethodCall(MethodNode markDirtyMethodNode, String propertyName, Variable value) {
        def args = args(constX(propertyName), varX(value))
        final markDirtyMethodCall = callX(varX("this"), markDirtyMethodNode.name, args)
        markDirtyMethodCall.methodTarget = markDirtyMethodNode
        return markDirtyMethodCall
    }

    protected GetterAndSetter getGetterAndSetterForPropertyName(LinkedHashMap<String, GetterAndSetter> gettersAndSetters, String propertyName) {
        def getterAndSetter = gettersAndSetters[propertyName]
        if (getterAndSetter == null) {
            getterAndSetter = new GetterAndSetter()
            gettersAndSetters[propertyName] = getterAndSetter
        }
        return getterAndSetter
    }



    private boolean isSetter(String methodName, MethodNode declaredMethod) {
        return declaredMethod.getParameters().length == 1 && ReflectionUtils.isSetter(methodName, OBJECT_CLASS_ARG);
    }

    private boolean isGetter(String methodName, MethodNode declaredMethod) {
        return declaredMethod.getParameters().length == 0 && ReflectionUtils.isGetter(methodName, EMPTY_JAVA_CLASS_ARRAY);
    }

    String[] getArtefactTypes() {
        return ["Domain"] as String[];
    }

    @CompileStatic
    class GetterAndSetter {
        MethodNode getter
        MethodNode setter

        boolean hasBoth() {
            getter && setter
        }

        boolean hasNeither() {
            !getter && !setter
        }
    }
}
