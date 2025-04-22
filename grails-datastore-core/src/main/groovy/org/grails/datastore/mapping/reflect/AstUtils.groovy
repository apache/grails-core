/*
 * Copyright 2015 original authors
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
package org.grails.datastore.mapping.reflect

import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.transform.TypeChecked
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotatedNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CodeVisitorSupport
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.tools.GeneralUtils
import org.codehaus.groovy.classgen.VariableScopeVisitor
import org.codehaus.groovy.control.Janitor
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.runtime.MetaClassHelper
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types
import org.codehaus.groovy.transform.trait.Traits
import org.springframework.util.StringUtils
import static org.codehaus.groovy.ast.tools.GenericsUtils.correctToGenericsSpecRecurse

import jakarta.persistence.Entity
import java.lang.annotation.Annotation
import java.lang.reflect.Modifier
import java.util.regex.Pattern

import static org.codehaus.groovy.ast.ClassHelper.int_TYPE
import static org.codehaus.groovy.ast.ClassHelper.make
import static org.codehaus.groovy.ast.tools.GenericsUtils.correctToGenericsSpecRecurse


/**
 * Utility methods for dealing with Groovy ASTs
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class AstUtils {
    private static final String SPEC_CLASS = "spock.lang.Specification"
    private static final Class<?>[] EMPTY_JAVA_CLASS_ARRAY = []
    private static final Class<?>[] OBJECT_CLASS_ARG = [Object.class]

    public static final ClassNode COMPILE_STATIC_TYPE = ClassHelper.make(CompileStatic)
    public static final ClassNode TYPE_CHECKED_TYPE = ClassHelper.make(TypeChecked)
    public static final Object TRANSFORM_APPLIED_MARKER = new Object()
    public static final String DOMAIN_TYPE = "Domain"
    public static final Parameter[] ZERO_PARAMETERS = new Parameter[0]
    public static final ClassNode[] EMPTY_CLASS_ARRAY = new ClassNode[0]
    public static final Token ASSIGNMENT_OPERATOR = Token.newSymbol(Types.ASSIGNMENT_OPERATOR, 0, 0)
    public static final ArgumentListExpression ZERO_ARGUMENTS = new ArgumentListExpression()
    public static final ClassNode OBJECT_CLASS_NODE = new ClassNode(Object.class).getPlainNodeReference()

    private static final Set<String> TRANSFORMED_CLASSES = new HashSet<String>()
    private static final Set<String> ENTITY_ANNOTATIONS = ["grails.persistence.Entity", "grails.gorm.annotation.Entity", Entity.class.getName()] as Set<String>

    /**
     * @return The names of the transformed entities for this context
     */
    static Collection<String> getKnownEntityNames() {
        return Collections.unmodifiableCollection( TRANSFORMED_CLASSES )
    }

    /**
     * @param name Adds the name of a transformed entity
     */
    static void addTransformedEntityName(String name) {
        TRANSFORMED_CLASSES.add(name)
    }
    /**
     * The name of the Grails application directory
     */

    public static final String GRAILS_APP_DIR = "grails-app"

    public static final String REGEX_FILE_SEPARATOR = "[\\\\/]" // backslashes need escaping in regexes
    /*
     Domain path is always matched against the normalized File representation of an URL and
     can therefore work with slashes as separators.
     */
    public static Pattern DOMAIN_PATH_PATTERN = Pattern.compile(".+" + REGEX_FILE_SEPARATOR + GRAILS_APP_DIR + REGEX_FILE_SEPARATOR + "domain" + REGEX_FILE_SEPARATOR + "(.+)\\.(groovy|java)")

    private static final Map<String, ClassNode> emptyGenericsPlaceHoldersMap = Collections.emptyMap()

    /**
     * Checks whether the file referenced by the given url is a domain class
     *
     * @param url The URL instance
     * @return true if it is a domain class
     */
    static boolean isDomainClass(URL url) {
        if (url == null) return false

        return DOMAIN_PATH_PATTERN.matcher(url.getFile()).find()
    }

    /**
     * Finds all the abstract methods for the give class node
     * @param classNode The class node
     * @return The abstract method
     */
    static List<MethodNode> findPublicAbstractMethods(ClassNode classNode) {
        List<MethodNode> methods = []
        findAbstractMethodsInternal(classNode, methods, false)
        return methods
    }

    /**
     * Finds all the abstract methods for the give class node
     * @param classNode The class node
     * @return The abstract method
     */
    static List<MethodNode> findAllAbstractMethods(ClassNode classNode) {
        List<MethodNode> methods = []
        findAbstractMethodsInternal(classNode, methods, true)
        return methods
    }

    /**
     * Finds all the abstract methods for the give class node
     * @param classNode The class node
     * @return The abstract method
     */
    static List<MethodNode> findAllUnimplementedAbstractMethods(ClassNode classNode) {
        List<MethodNode> methods = []
        findAbstractMethodsInternal(classNode, methods, true)
        return methods.findAll() { MethodNode mn ->
            def method = classNode.getMethod(mn.name, mn.parameters)
            return method == null || method.isAbstract()
        }
    }

    protected static void findAbstractMethodsInternal(ClassNode classNode, List<MethodNode> methods, boolean includeProtected) {
        if(classNode == null || classNode == ClassHelper.GROOVY_OBJECT_TYPE || Traits.isTrait(classNode) || classNode.name.indexOf('$') > -1) {
            return
        }
        if (classNode.isInterface()) {
            methods.addAll(classNode.methods)
        }
        else {
            for(MethodNode m in classNode.getMethods()) {
                int modifiers = m.modifiers
                def traitBridge = findAnnotation(m, Traits.TraitBridge)
                boolean isInternal = m.name.indexOf('$') > -1
                if(traitBridge != null || isInternal) {
                    continue
                }
                if(Modifier.isAbstract(modifiers) && (Modifier.isPublic(modifiers) || (Modifier.isProtected(modifiers) && includeProtected))  && !m.isSynthetic()) {
                    methods.add(m)
                }
            }
            ClassNode superClass = classNode.getSuperClass()
            if(superClass != ClassHelper.OBJECT_TYPE) {
                findAbstractMethodsInternal(superClass, methods, includeProtected)
            }
        }
        for(i in classNode.getInterfaces()) {
            findAbstractMethodsInternal(i, methods, includeProtected)
        }
    }

    /**
     * Build static direct call to getter of a property
     *
     * @param objectExpression
     * @param propertyName
     * @param targetClassNode
     * @return The method call expression
     */
    static MethodCallExpression buildGetPropertyExpression(final Expression objectExpression, final String propertyName, final ClassNode targetClassNode) {
        return buildGetPropertyExpression(objectExpression, propertyName, targetClassNode, false)
    }

    /**
     * Build static direct call to getter of a property
     *
     * @param objectExpression
     * @param propertyName
     * @param targetClassNode
     * @param useBooleanGetter
     * @return The method call expression
     */
    static MethodCallExpression buildGetPropertyExpression(final Expression objectExpression, final String propertyName, final ClassNode targetClassNode, final boolean useBooleanGetter) {
        String methodName = (useBooleanGetter ? "is" : "get") + MetaClassHelper.capitalize(propertyName)
        MethodCallExpression methodCallExpression = new MethodCallExpression(objectExpression, methodName, MethodCallExpression.NO_ARGUMENTS)
        MethodNode getterMethod = targetClassNode.getGetterMethod(methodName)
        if(getterMethod != null) {
            methodCallExpression.setMethodTarget(getterMethod)
        }
        return methodCallExpression
    }

    /**
     * Build static direct call to setter of a property
     *
     * @param objectExpression
     * @param propertyName
     * @param targetClassNode
     * @param valueExpression
     * @return The method call expression
     */
    static MethodCallExpression buildSetPropertyExpression(final Expression objectExpression, final String propertyName, final ClassNode targetClassNode, final Expression valueExpression) {
        String methodName = "set" + MetaClassHelper.capitalize(propertyName)
        MethodCallExpression methodCallExpression = new MethodCallExpression(objectExpression, methodName, new ArgumentListExpression(valueExpression))
        MethodNode setterMethod = targetClassNode.getSetterMethod(methodName)
        if(setterMethod != null) {
            methodCallExpression.setMethodTarget(setterMethod)
        }
        return methodCallExpression
    }

    static void processVariableScopes(SourceUnit source, ClassNode classNode, MethodNode methodNode) {
        VariableScopeVisitor scopeVisitor = new VariableScopeVisitor(source)
        if(methodNode == null) {
            scopeVisitor.visitClass(classNode)
        } else {
            scopeVisitor.prepareVisit(classNode)
            scopeVisitor.visitMethod(methodNode)
        }
    }

    /**
     * Returns true if MethodNode is marked with annotationClass
     * @param methodNode A MethodNode to inspect
     * @param annotationClass an annotation to look for
     * @return true if classNode is marked with annotationClass, otherwise false
     */
    static boolean hasAnnotation(final MethodNode methodNode, final Class<? extends Annotation> annotationClass) {
        AstAnnotationUtils.hasAnnotation(methodNode, annotationClass)
    }

    static boolean hasAnnotation(MethodNode methodNode, ClassNode annotationClassNode) {
        AstAnnotationUtils.hasAnnotation(methodNode, annotationClassNode)
    }

    /**
     * Whether the class is a Spock test
     *
     * @param classNode The class node
     * @return True if it is
     */
    static boolean isSpockTest(ClassNode classNode) {
        return isSubclassOf(classNode, SPEC_CLASS)
    }

    /**
     * Whether the method node has any JUnit annotations
     *
     * @param md The method node
     * @return True if it does
     */
    static boolean hasJunitAnnotation(MethodNode md) {
        AstAnnotationUtils.hasJunitAnnotation(md)
    }


    /**
     * Returns true if MethodNode is marked with annotationClass
     * @param methodNode A MethodNode to inspect
     * @param annotationClass an annotation to look for
     * @return true if classNode is marked with annotationClass, otherwise false
     */
    static boolean hasAnnotation(final MethodNode methodNode, String annotationClassName) {
        AstAnnotationUtils.hasAnnotation(methodNode, annotationClassName)
    }

    static boolean hasAnnotation(List<AnnotationNode> annotationNodes, AnnotationNode annotationNode) {
        AstAnnotationUtils.hasAnnotation(annotationNodes, annotationNode)
    }


    /**
     * @param classNode a ClassNode to search
     * @param annotationsToLookFor Annotations to look for
     * @return true if classNode is marked with any of the annotations in annotationsToLookFor
     */
    static boolean hasAnyAnnotations(final ClassNode classNode, final Class<? extends Annotation>... annotationsToLookFor) {
        AstAnnotationUtils.hasAnyAnnotations(classNode, annotationsToLookFor)
    }

    /**
     * Returns true if classNode is marked with annotationClass
     * @param classNode A ClassNode to inspect
     * @param annotationClass an annotation to look for
     * @return true if classNode is marked with annotationClass, otherwise false
     */
    static boolean hasAnnotation(final ClassNode classNode, final Class<? extends Annotation> annotationClass) {
        AstAnnotationUtils.hasAnnotation(classNode, annotationClass)
    }

    static Parameter[] copyParameters(Parameter[] parameterTypes) {
        return copyParameters(parameterTypes, null)
    }

    static Parameter[] copyParameters(Parameter[] parameterTypes, Map<String, ClassNode> genericsPlaceholders) {
        Parameter[] newParameterTypes = new Parameter[parameterTypes.length]
        for (int i = 0; i < parameterTypes.length; i++) {
            Parameter parameterType = parameterTypes[i]
            Parameter newParameter = new Parameter(replaceGenericsPlaceholders(parameterType.getType(), genericsPlaceholders), parameterType.getName(), parameterType.getInitialExpression())
            copyAnnotations(parameterType, newParameter)
            newParameterTypes[i] = newParameter
        }
        return newParameterTypes
    }

    static Parameter[] copyParameters(Map<String, ClassNode> genericsSpec, Parameter[] parameterTypes, List<String> currentMethodGenPlaceholders) {
        Parameter[] newParameterTypes = new Parameter[parameterTypes.length]
        for (int i = 0; i < parameterTypes.length; i++) {
            Parameter parameterType = parameterTypes[i]
            ClassNode newParamType = correctToGenericsSpecRecurse(genericsSpec, parameterType.getType(), currentMethodGenPlaceholders)
            Parameter newParameter = new Parameter(newParamType, parameterType.getName(), parameterType.getInitialExpression())
            newParameter.addAnnotations(parameterType.getAnnotations())
            newParameterTypes[i] = newParameter
        }
        return newParameterTypes
    }

    static void copyAnnotations(final AnnotatedNode from, final AnnotatedNode to) {
        copyAnnotations(from, to, null, null)
    }

    static void copyAnnotations(final AnnotatedNode from, final AnnotatedNode to, final Set<String> included, final Set<String> excluded) {
        final List<AnnotationNode> annotationsToCopy = from.getAnnotations()
        for(final AnnotationNode node : annotationsToCopy) {
            String annotationClassName = node.getClassNode().getName()
            if((excluded==null || !excluded.contains(annotationClassName)) &&
                    (included==null || included.contains(annotationClassName))) {
                final AnnotationNode copyOfAnnotationNode = cloneAnnotation(node)
                to.addAnnotation(copyOfAnnotationNode)
            }
        }
    }

    static AnnotationNode cloneAnnotation(final AnnotationNode node) {
        final AnnotationNode copyOfAnnotationNode = new AnnotationNode(node.getClassNode())
        final Map<String, Expression> members = node.getMembers()
        for(final Map.Entry<String, Expression> entry : members.entrySet()) {
            copyOfAnnotationNode.addMember(entry.getKey(), entry.getValue())
        }
        return copyOfAnnotationNode
    }

    /**
     * Is the class an enum
     * @param classNode The class node
     * @return True if it is
     */
    static boolean isEnum(ClassNode classNode) {
        ClassNode parent = classNode.getSuperClass()
        while (parent != null) {
            if (parent.getName().equals("java.lang.Enum"))
                return true
            parent = parent.getSuperClass()
        }
        return false
    }

    /**
     * Is the class a number
     * @param classNode The class node
     * @return True if it is
     */
    static boolean isNumberType(ClassNode classNode) {
        if(classNode != null) {
            return ClassHelper.isNumberType(classNode) || isSubclassOfOrImplementsInterface(classNode, Number.name)
        }
        return false
    }

    /**
     * Obtains a property from the class hierarchy
     *
     * @param cn The class node
     * @param name The property name
     * @return The property node or null
     */
    static PropertyNode getPropertyFromHierarchy(ClassNode cn, String name) {
        PropertyNode pn = cn.getProperty(name)
        ClassNode superClass = cn.getSuperClass()
        while(pn == null && superClass != null) {
            pn = superClass.getProperty(name)
            if(pn != null) return pn
            superClass = superClass.getSuperClass()
        }
        return pn
    }
    @Memoized
    static boolean isDomainClass(ClassNode classNode) {
        if (classNode == null) return false
        if (classNode.isArray()) return false
        if(implementsInterface(classNode, "org.grails.datastore.gorm.GormEntity")) {
            return true
        }
        String filePath = classNode.getModule() != null ? classNode.getModule().getDescription() : null
        if (filePath != null) {
            try {
                if (isDomainClass(new File(filePath).toURI().toURL())) {
                    return true
                }
            } catch (MalformedURLException e) {
                // ignore
            }
        }
        List<AnnotationNode> annotations = classNode.getAnnotations()
        if (annotations != null && !annotations.isEmpty()) {
            for (AnnotationNode annotation : annotations) {
                String className = annotation.getClassNode().getName()
                if( ENTITY_ANNOTATIONS.any() { String ann -> ann.equals(className)} ) {
                    return true
                }
            }
        }
        return false
    }

    static ClassNode nonGeneric(ClassNode type) {
        return replaceGenericsPlaceholders(type, emptyGenericsPlaceHoldersMap)
    }

    @SuppressWarnings("unchecked")
    static ClassNode nonGeneric(ClassNode type, final ClassNode wildcardReplacement) {
        return replaceGenericsPlaceholders(type, emptyGenericsPlaceHoldersMap, wildcardReplacement)
    }

    static ClassNode replaceGenericsPlaceholders(ClassNode type, Map<String, ClassNode> genericsPlaceholders) {
        return replaceGenericsPlaceholders(type, genericsPlaceholders, null)
    }

    static ClassNode replaceGenericsPlaceholders(ClassNode type, Map<String, ClassNode> genericsPlaceholders, ClassNode defaultPlaceholder) {
        if (type.isArray()) {
            return replaceGenericsPlaceholders(type.getComponentType(), genericsPlaceholders).makeArray()
        }

        if (!type.isUsingGenerics() && !type.isRedirectNode()) {
            return type.getPlainNodeReference()
        }

        if(type.isGenericsPlaceHolder() && genericsPlaceholders != null) {
            final ClassNode placeHolderType
            if(genericsPlaceholders.containsKey(type.getUnresolvedName())) {
                placeHolderType = genericsPlaceholders.get(type.getUnresolvedName())
            } else {
                placeHolderType = defaultPlaceholder
            }
            if(placeHolderType != null) {
                return placeHolderType.getPlainNodeReference()
            } else {
                return ClassHelper.make(Object.class).getPlainNodeReference()
            }
        }

        final ClassNode nonGen = type.getPlainNodeReference()

        if("java.lang.Object".equals(type.getName())) {
            nonGen.setGenericsPlaceHolder(false)
            nonGen.setGenericsTypes(null)
            nonGen.setUsingGenerics(false)
        } else {
            if(type.isUsingGenerics()) {
                GenericsType[] parameterized = type.getGenericsTypes()
                if (parameterized != null && parameterized.length > 0) {
                    GenericsType[] copiedGenericsTypes = new GenericsType[parameterized.length]
                    for (int i = 0; i < parameterized.length; i++) {
                        GenericsType parameterizedType = parameterized[i]
                        GenericsType copiedGenericsType = null
                        if (parameterizedType.isPlaceholder() && genericsPlaceholders != null) {
                            ClassNode placeHolderType = genericsPlaceholders.get(parameterizedType.getName())
                            if(placeHolderType != null) {
                                copiedGenericsType = new GenericsType(placeHolderType.getPlainNodeReference())
                            } else {
                                copiedGenericsType = new GenericsType(ClassHelper.make(Object.class).getPlainNodeReference())
                            }
                        } else {
                            copiedGenericsType = new GenericsType(replaceGenericsPlaceholders(parameterizedType.getType(), genericsPlaceholders))
                        }
                        copiedGenericsTypes[i] = copiedGenericsType
                    }
                    nonGen.setGenericsTypes(copiedGenericsTypes)
                }
            }
        }

        return nonGen
    }

    static void injectTrait(ClassNode classNode, Class traitClass) {
        ClassNode traitClassNode = ClassHelper.make(traitClass)
        boolean implementsTrait = false
        boolean traitNotLoaded = false
        try {
            implementsTrait = classNode.declaresInterface(traitClassNode)
        } catch (Throwable e) {
            // if we reach this point, the trait injector could not be loaded due to missing dependencies (for example missing servlet-api). This is ok, as we want to be able to compile against non-servlet environments.
            traitNotLoaded = true
        }
        if (!implementsTrait && !traitNotLoaded) {
            final GenericsType[] genericsTypes = traitClassNode.getGenericsTypes()
            final Map<String, ClassNode> parameterNameToParameterValue = new LinkedHashMap<String, ClassNode>()
            if(genericsTypes != null) {
                for(GenericsType gt : genericsTypes) {
                    parameterNameToParameterValue.put(gt.getName(), classNode)
                }
            }
            classNode.addInterface(replaceGenericsPlaceholders(traitClassNode, parameterNameToParameterValue, classNode))
        }
    }

    static boolean hasOrInheritsProperty(ClassNode classNode, String propertyName) {
        if (hasProperty(classNode, propertyName)) {
            return true
        }

        ClassNode parent = classNode.getSuperClass()
        while (parent != null && !parent.name.equals("java.lang.Object")) {
            if (hasProperty(parent, propertyName)) {
                return true
            }
            parent = parent.getSuperClass()
        }

        return false
    }

    /**
     * Returns whether a classNode has the specified property or not
     *
     * @param classNode    The ClassNode
     * @param propertyName The name of the property
     * @return true if the property exists in the ClassNode
     */
    static boolean hasProperty(ClassNode classNode, String propertyName) {
        if (classNode == null || !StringUtils.hasText(propertyName)) {
            return false
        }

        final MethodNode method = classNode.getMethod(NameUtils.getGetterName(propertyName), Parameter.EMPTY_ARRAY)
        if (method != null) return true

        // check read-only field with setter
        if( classNode.getField(propertyName) != null && !classNode.getMethods(NameUtils.getSetterName(propertyName)).isEmpty()) {
            return true
        }

        for (PropertyNode pn in classNode.getProperties()) {
            if (pn.name.equals(propertyName) && !pn.isPrivate()) {
                return true
            }
        }

        return false
    }

    /**
     * Returns the property type if it exists
     *
     * @param classNode    The ClassNode
     * @param propertyName The name of the property
     * @return true if the property exists in the ClassNode
     */
    static ClassNode getPropertyType(ClassNode classNode, String propertyName) {
        if (classNode == null || !StringUtils.hasText(propertyName)) {
            return null
        }

        final MethodNode method = classNode.getMethod(NameUtils.getGetterName(propertyName), Parameter.EMPTY_ARRAY)
        if (method != null) return method.returnType

        for (PropertyNode pn in classNode.getProperties()) {
            if (pn.name.equals(propertyName) && !pn.isPrivate()) {
                return pn.type
            }
        }

        return null
    }

    static ClassNode getFurthestUnresolvedParent(ClassNode classNode) {
        ClassNode parent = classNode.getSuperClass()

        while (parent != null && !parent.name.equals("java.lang.Object") && !parent.isResolved()) {
            classNode = parent
            parent = parent.getSuperClass()
        }
        return classNode
    }

    /**
     * Adds an annotation to the give nclass node if it doesn't already exist
     *
     * @param classNode The class node
     * @param annotationClass The annotation class
     */
    static void addAnnotationIfNecessary(AnnotatedNode classNode, Class<? extends Annotation> annotationClass) {
        AstAnnotationUtils.addAnnotationIfNecessary(classNode, annotationClass)
    }

    /**
     * Adds an annotation to the given class node or returns the existing annotation
     *
     * @param classNode The class node
     * @param annotationClass The annotation class
     */
    static AnnotationNode addAnnotationOrGetExisting(AnnotatedNode classNode, Class<? extends Annotation> annotationClass) {
        AstAnnotationUtils.addAnnotationOrGetExisting(classNode, annotationClass)
    }

    /**
     * @return A new this variable
     */
    static VariableExpression varThis() {
        return new VariableExpression("this")
    }

    /**
     * Adds an annotation to the given class node or returns the existing annotation
     *
     * @param annotatedNode The class node
     * @param annotationClass The annotation class
     */
    static AnnotationNode addAnnotationOrGetExisting(AnnotatedNode annotatedNode, Class<? extends Annotation> annotationClass, Map<String, Object> members) {
        AstAnnotationUtils.addAnnotationOrGetExisting(annotatedNode, annotationClass, members)
    }

    static AnnotationNode addAnnotationOrGetExisting(AnnotatedNode annotatedNode, ClassNode annotationClassNode) {
        AstAnnotationUtils.addAnnotationOrGetExisting(annotatedNode, annotationClassNode)
    }

    static AnnotationNode addAnnotationOrGetExisting(AnnotatedNode annotatedNode, ClassNode annotationClassNode, Map<String, Object> members) {
        AstAnnotationUtils.addAnnotationOrGetExisting(annotatedNode, annotationClassNode, members)
    }

    static AnnotationNode findAnnotation(AnnotatedNode classNode, Class<?> type) {
        AstAnnotationUtils.findAnnotation(classNode, type)
    }

    static AnnotationNode findAnnotation(AnnotatedNode annotationClassNode, List<AnnotationNode> annotations) {
        AstAnnotationUtils.findAnnotation(annotationClassNode, annotations)
    }

    /**
     * Tests whether the ClasNode implements the specified method name.
     *
     * @param classNode  The ClassNode
     * @param methodName The method name
     * @return true if it does implement the method
     */
    static boolean implementsZeroArgMethod(ClassNode classNode, String methodName) {
        MethodNode method = classNode.getDeclaredMethod(methodName, Parameter.EMPTY_ARRAY)
        return method != null && (method.isPublic() || method.isProtected()) && !method.isAbstract()
    }

    @SuppressWarnings("rawtypes")
    static boolean implementsOrInheritsZeroArgMethod(ClassNode classNode, String methodName) {
        if (implementsZeroArgMethod(classNode, methodName)) {
            return true
        }

        ClassNode parent = classNode.getSuperClass()
        while (parent != null && !parent.name.equals("java.lang.Object")) {
            if (implementsZeroArgMethod(parent, methodName)) {
                return true
            }
            parent = parent.getSuperClass()
        }
        return false
    }


    static boolean isSubclassOfOrImplementsInterface(ClassNode childClass, ClassNode superClass) {
        String superClassName = superClass.getName()
        return isSubclassOfOrImplementsInterface(childClass, superClassName)
    }

    static boolean isSubclassOfOrImplementsInterface(ClassNode childClass, String superClassName) {
        return isSubclassOf(childClass, superClassName) || implementsInterface(childClass, superClassName)
    }

    /**
     * Returns true if the given class name is a parent class of the given class
     *
     * @param classNode The class node
     * @param parentClassName the parent class name
     * @return True if it is a subclass
     */
    static boolean isSubclassOf(ClassNode classNode, String parentClassName) {
        if(classNode.name == parentClassName) return true
        ClassNode currentSuper = classNode.getSuperClass()
        while (currentSuper != null ) {
            if (currentSuper.getName() == parentClassName) {
                return true
            }

            if(currentSuper.getName() == OBJECT_CLASS_NODE.getName()) {
                break
            }
            else {
                currentSuper = currentSuper.getSuperClass()
            }

        }
        return false
    }

    /**
     * Whether the given class node implements the given interface name
     *
     * @param classNode The class node
     * @param interfaceName The interface name
     * @return True if it does
     */
    static boolean implementsInterface(ClassNode classNode, String interfaceName) {
        ClassNode interfaceNode = make(interfaceName)
        return implementsInterface(classNode, interfaceNode)
    }
    /**
     * Whether the given class node implements the given interface node
     *
     * @param classNode The class node
     * @param itfc The interface
     * @return True if it does
     */
    static boolean implementsInterface(ClassNode classNode, Class itfc) {
        return classNode.getAllInterfaces().contains(make(itfc))
    }
    /**
     * Whether the given class node implements the given interface node
     *
     * @param classNode The class node
     * @param interfaceName The interface node
     * @return True if it does
     */
    static boolean implementsInterface(ClassNode classNode, ClassNode interfaceNode) {
        return classNode.getAllInterfaces().contains(interfaceNode)
    }

    /**
     * Whether the given type is a Groovy object
     * @param type The type
     * @return True if it is
     */
    static boolean isGroovyType(ClassNode type) {
        return type.isPrimaryClassNode() || implementsInterface(type, "groovy.lang.GroovyObject")
    }

    static ClassNode findInterface(ClassNode classNode, String interfaceName) {
        ClassNode currentClassNode = classNode
        if(currentClassNode.name == interfaceName) return classNode
        while (currentClassNode != null && !currentClassNode.getName().equals(OBJECT_CLASS_NODE.getName())) {
            ClassNode[] interfaces = currentClassNode.getInterfaces()

            def interfaceNode = implementsInterfaceInternal(interfaces, interfaceName)
            if (interfaceNode != null) {
                return interfaceNode
            }
            currentClassNode = currentClassNode.getSuperClass()
        }
        return null
    }

    private static ClassNode implementsInterfaceInternal(ClassNode[] interfaces, String interfaceName) {
        for (ClassNode anInterface : interfaces) {
            if(anInterface.getName().equals(interfaceName)) {
                return anInterface
            }
            ClassNode[] childInterfaces = anInterface.getInterfaces()
            if(childInterfaces != null && childInterfaces.length>0) {
                return implementsInterfaceInternal(childInterfaces,interfaceName )
            }

        }
        return null
    }

    static void warning(final SourceUnit sourceUnit, final ASTNode node, final String warningMessage) {
        final String sample = sourceUnit.getSample(node.getLineNumber(), node.getColumnNumber(), new Janitor())
        System.err.println("WARNING: " + warningMessage + "\n\n" + sample)
    }

    static void error(SourceUnit sourceUnit, ASTNode expr, String errorMessage) {
        sourceUnit.getErrorCollector().addErrorAndContinue(new SyntaxErrorMessage(
                new SyntaxException(errorMessage + '\n', expr.getLineNumber(), expr.getColumnNumber(),
                        expr.getLastLineNumber(), expr.getLastColumnNumber()),
                sourceUnit)
        )
    }


    static boolean isSetter(MethodNode declaredMethod) {
        return declaredMethod.getParameters().length == 1 && ReflectionUtils.isSetter(declaredMethod.getName(), OBJECT_CLASS_ARG)
    }

    static boolean isGetter(MethodNode declaredMethod) {
        return declaredMethod.getParameters().length == 0 && ReflectionUtils.isGetter(declaredMethod.getName(), EMPTY_JAVA_CLASS_ARRAY)
    }

    static boolean isIterableOrArrayOfDomainClasses(ClassNode type) {
        boolean isCompatibleReturnType = false
        if (type.name == Iterable.name || implementsInterface(type, Iterable.name)) {
            GenericsType[] genericsTypes = type.genericsTypes
            if (genericsTypes != null && genericsTypes.length > 0) {
                if (isDomainClass(genericsTypes[0].type)) {
                    isCompatibleReturnType = true
                }
            }
        } else if (type.isArray()) {

            ClassNode componentType = type.componentType
            if (componentType != null && isDomainClass(componentType)) {
                isCompatibleReturnType = true
            }
        }
        return isCompatibleReturnType
    }

    /**
     * Builds a map
     * @param map The map
     * @return The map expression
     */
    static MapExpression mapX(Map<String, ? extends Expression> map) {
        def me = new MapExpression()
        for(entry in map) {
            me.addMapEntryExpression(new MapEntryExpression(GeneralUtils.constX(entry.key), entry.value))
        }
        return me
    }

    /**
     * Makes a closure aware of the given methods arguments
     *
     * @param methodNode The method
     * @param closure The existing closure
     * @return A new closure aware of the method arguments
     */
    static ClosureExpression makeClosureAwareOfArguments(MethodNode methodNode, ClosureExpression closure) {
        // make a copy
        ClosureExpression closureExpression = new ClosureExpression(closure.parameters, closure.code)
        closure.setCode(new BlockStatement())
        VariableScope scope = methodNode.variableScope
        closureExpression.setVariableScope(scope)
        if (scope != null) {
            for (Parameter p in methodNode.parameters) {
                p.setClosureSharedVariable(true)
                scope.putReferencedLocalVariable(p)
                scope.putDeclaredVariable(p)
            }
        }

        CodeVisitorSupport variableTransformer = new ClassCodeExpressionTransformer() {
            @Override
            protected SourceUnit getSourceUnit() {
                methodNode.declaringClass.module.context
            }

            @Override
            Expression transform(Expression exp) {
                if (exp instanceof VariableExpression) {
                    VariableExpression var = (VariableExpression) exp
                    def local = scope.getReferencedLocalVariable(var.name)
                    if (local != null) {
                        def newExpr = new VariableExpression(local)
                        newExpr.setClosureSharedVariable(true)
                        newExpr.setAccessedVariable(local)
                        return newExpr
                    }
                }
                return super.transform(exp)
            }
        }
        variableTransformer.visitClosureExpression(closureExpression)
        return closureExpression
    }
}
