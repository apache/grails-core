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
package grails.compiler.beans;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

/**
 * Marks a class whose {@code beans} closure property is a bean-definition DSL that should be
 * compiled into real {@code @Bean} factory methods, so the class can serve as a plain
 * {@code @AutoConfiguration} with no closure DSL surviving into the compiled bytecode.
 *
 * <h2>Declarations</h2>
 *
 * The annotated class declares a {@code beans} property initialised to a closure, whose every
 * top-level statement is one of:
 *
 * <dl>
 * <dt>{@code bean(["name", ] Type) { ... }}</dt>
 * <dd>A bean built by the closure body, which becomes the generated method's body verbatim.</dd>
 *
 * <dt>{@code bean(["name", ] Type)}</dt>
 * <dd>The same bean when the body would only have been {@code new Type()} - the most common shape.
 * A closure with parameters but an empty body means this for a bean with dependencies: the
 * parameters say what is injected and the constructor call is generated from them, in the order
 * written, leaving the compiler to select the constructor from their types exactly as it would for
 * a hand-written body. Both forms construct the declared type, so both require it to be
 * concrete.</dd>
 *
 * <dt>{@code bean(["name", ] Type, Implementation)}</dt>
 * <dd>A bean declared as the interface its consumers inject and constructed as something else:
 * a method returning {@code Type} whose body is {@code new Implementation()}, taking constructor
 * arguments from the closure parameters as the bodyless form does. Naming an implementation is
 * itself the construction, so this form takes no body; an interface with neither an implementation
 * nor a body is an error.</dd>
 *
 * <dt>{@code group("name").<conditions> { ... }}</dt>
 * <dd>A nested static {@code @Configuration(proxyBeanMethods = false)} class holding the
 * declarations in its body, with the chained conditions on the class rather than on each bean -
 * {@code group("imageServing").conditionalOnClass(name: "com.example.Optional") { ... }} generates
 * {@code Host$ImageServingConfiguration}. Spring finds it unaided, since
 * {@code ConfigurationClassParser} processes a configuration class's member classes.
 * <p>This is the shape real auto-configurations take - Spring Boot's own
 * {@code JacksonAutoConfiguration} carries four nested {@code @ConditionalOnClass} classes - and it
 * is the only shape that works for a bean whose <i>own signature</i> names a class that may be
 * absent. Spring reads a condition from the bytecode before loading anything, but a {@code @Bean}
 * method's parameter and return types are resolved when its configuration class is parsed, so
 * guarding such a bean on the method is not reliably safe; moving it into a group moves the guard
 * to a class that is never parsed when the condition fails.</p>
 * <p>Takes the condition qualifiers and {@code .annotate(...)} - the bean-shaped ones have nothing
 * to attach to. Groups do not nest, and a group's closure takes no parameters: it declares a class,
 * not a bean, so there is nothing to inject into.</p></dd>
 *
 * <dt>{@code field(["name", ] Type)}</dt>
 * <dd>A private field on the generated class, for state shared across bean methods. Chainable with
 * {@code .value(...)}, {@code .typeArguments(...)} and {@code .annotate(...)}. The usual case is
 * injected configuration: {@code field("encoding", String).value(Settings.GSP_VIEW_ENCODING,
 * "UTF-8")} compiles to {@code @Value("${grails.views.gsp.encoding:UTF-8}")}. The two-argument form
 * takes a config key (a literal or a bare constant reference) plus a default; the one-argument form
 * a bare key with no default; a string already carrying a {@code ${...}} placeholder or
 * {@code #{...}} SpEL expression passes through verbatim.</dd>
 *
 * <dt>{@code method(["name", ] Type) { ... }}</dt>
 * <dd>A private helper method, for logic shared across bean methods, lifted from the closure the
 * same way {@code bean(...)} is. Chainable with {@code .annotate(...)} and
 * {@code .typeArguments(...)} only - {@code .value(...)} is field-specific.</dd>
 * </dl>
 *
 * <p>When no name is given, one is derived from the type following the JavaBeans convention
 * ({@link java.beans.Introspector#decapitalize(String)}). A name that is given may be a String
 * literal or any compile-time String constant - a bare reference to one declared on this class, a
 * qualified {@code Other.CONSTANT}, or a concatenation of those - folded the same way
 * {@code .value(...)} folds a config key. Bean names are often already constants, because something
 * else looks the bean up by one; the DSL being the single place that could not say the constant's
 * name would mean writing the string twice and letting the two drift.</p>
 *
 * <h2>Construction settles type arguments</h2>
 *
 * Whatever a bean constructs - a named implementation, or a body that is just a {@code new ...}
 * expression - also settles the declared type's type arguments wherever it binds them concretely,
 * so {@code bean("auditorAware", AuditorAware, SpringSecurityAuditorAware)} declares an
 * {@code AuditorAware<Long>} without repeating {@code Long}. Where it does not - the construction
 * is raw, its binding is itself a type variable, or the body is anything but a bare construction -
 * the raw type stands and {@code .typeArguments(...)} says it explicitly.
 *
 * <p>An implementation configured by <i>properties</i> rather than constructor arguments still
 * needs a body, but only ever one expression: Groovy's map construction says in one line what
 * declare-set-return says in four, and being a construction it settles type arguments like any
 * other - {@code bean("panel", Panel) { PanelService service -> new PanelImpl(service: service) }}.</p>
 *
 * <h2>Qualifiers</h2>
 *
 * Every {@code bean(...)} form chains with any combination of:
 *
 * <dl>
 * <dt>{@code .conditionalOnMissingBean(...)}</dt>
 * <dd>Positional types, the annotation's own named attributes ({@code name:}, {@code search:}), or
 * nothing at all - the bare form lets Spring infer the back-off type from the return type.</dd>
 *
 * <dt>{@code .conditionalOnBean(...)}</dt>
 * <dd>The positive counterpart - "register this only when something else supplied that" - taking
 * the same positional types and named attributes. It has no zero-argument form: with nothing named
 * Spring deduces the type from this bean's own return type, conditioning a bean on a bean of its
 * own type already existing.</dd>
 *
 * <dt>{@code .conditionalOnMissingBeanName(...)}</dt>
 * <dd>Backs off by this bean's own name, set automatically so the two strings cannot diverge.
 * Accepts the annotation's other attributes but rejects {@code name:} and types.</dd>
 *
 * <dt>{@code .conditionalOnProperty("app.offline"[, ...], havingValue: "false", matchIfMissing: true)}</dt>
 * <dd>Property names positionally or as {@code name:}/{@code value:}, the annotation's other
 * attributes by name. All named properties must match, as the annotation defines.</dd>
 *
 * <dt>{@code .conditionalOnExpression("...")}</dt>
 * <dd>One SpEL string, for what the others cannot say - most often a disjunction, since
 * {@code .conditionalOnProperty} only ever conjoins. Write it single-quoted in Groovy: the
 * {@code ${...}} placeholders are Spring's, and a double-quoted string would interpolate them away
 * at compile time.</dd>
 *
 * <dt>{@code .conditionalOnClass(SomeType[, "com.example.Other", ...])}</dt>
 * <dd>Types and String class names positionally, the annotation's other attributes by name. The
 * two forms are not interchangeable and choosing between them is the point: a literal reads better
 * and is checked by the compiler, but can only be written for a class this module compiles
 * against, while a class that may be <i>absent</i> has to be named as a String or the reference is
 * itself the thing that fails. Spring's annotation carries both ({@code value} and {@code name})
 * for that reason.
 * <p>Mind where the condition goes. Spring reads it from the bytecode before loading anything, but
 * a {@code @Bean} method's parameter and return types are resolved when the configuration class is
 * parsed - so guarding a method whose own signature names the absent class is not reliably safe.
 * Gate at class level for that case: a separate {@code @GrailsBeans @AutoConfiguration} class
 * carrying {@code @ConditionalOnClass}, holding the beans that mention the optional type.</p></dd>
 *
 * <dt>{@code .conditionalOnGrailsEnv("development"[, ...])}</dt>
 * <dd>Registers the bean only in those Grails environments. See {@link ConditionalOnGrailsEnv} for
 * why this is not {@code @ConditionalOnProperty} on {@code grails.env}.</dd>
 *
 * <dt>{@code .aliases("legacyName"[, ...])}</dt>
 * <dd>Additional names for the bean, which Spring resolves to the same singleton. The canonical
 * name stays the one {@code bean(...)} states - it is what the generated method is named after,
 * what duplicate-name validation runs against, and what {@code .conditionalOnMissingBeanName()}
 * resolves to - so aliases are their own call rather than more arguments there. The case they
 * exist for is a migration: something reachable under an old name that must stay reachable while
 * its callers move.</dd>
 *
 * <dt>{@code .primary()}, {@code .lazy()}</dt>
 * <dd>{@code @Primary}, {@code @Lazy}.</dd>
 *
 * <dt>{@code .scope("session"[, proxyMode: ScopedProxyMode.TARGET_CLASS])}</dt>
 * <dd>The scope name positionally or as {@code value:}/{@code scopeName:}, the annotation's other
 * attributes by name. {@code proxyMode} is the one to know about: a session- or request-scoped bean
 * injected into a singleton needs {@code TARGET_CLASS}, or the singleton captures one scope
 * instance for the lifetime of the application and serves it to everyone - a wrong answer rather
 * than an error.</dd>
 *
 * <dt>{@code .staticMethod()}</dt>
 * <dd>A {@code static} factory method. Required for {@code BeanFactoryPostProcessor} and
 * {@code BeanPostProcessor} beans, which must be creatable without instantiating their declaring
 * class, and rejected at compile time when such a bean is declared without it.</dd>
 *
 * <dt>{@code .typeArguments(Type, ...)}</dt>
 * <dd>Type arguments for the declared type, where the construction does not already settle them.
 * This matters because Spring resolves an injection point by its full generic type, and the type in
 * {@code bean(...)} is a class literal, on which Groovy has no syntax for writing them.</dd>
 *
 * <dt>{@code .annotate(AnnotationType[, attr: value, ...])} (repeatable)</dt>
 * <dd>The escape hatch, attaching any other annotation with as many attributes as it declares; an
 * array-valued attribute takes either a list or a single value that widens into a one-element
 * array, exactly as it would written out ({@code .annotate(DependsOn, value: "other")}).
 * Naming an annotation a <i>qualifier</i> already attached is not a collision but a merge into it,
 * which is the only way to reach the attributes no qualifier sets:
 * {@code .annotate(Bean, destroyMethod: "")} is how a bean wrapping a client it does not own stops
 * Spring inferring and calling {@code close()} on it, and
 * and it is how any attribute a qualifier does not itself set is reached. What the
 * qualifier itself set is not re-settable - a bean's name comes from {@code bean("name", Type)} -
 * and naming the same annotation twice through {@code .annotate(...)} remains an error, since one
 * call already takes every attribute.</dd>
 * </dl>
 *
 * <h2>Closure parameters are the injection points</h2>
 *
 * Closure parameters become the generated method's parameters - annotations and all, so anything
 * Spring reads off an injection point can be written on the parameter that receives it:
 * {@code @Qualifier} to pick between candidates, {@code @Value} for a config property, and
 * {@code @Autowired(required = false)} for a dependency that may not be there, which is the only
 * way to say "inject this if some other module supplied it":
 * {@code bean("smsSender", SmsSender) { @Autowired(required = false) SmsTransport t -> ... }}.
 *
 * <p>A body may construct an anonymous inner class. Groovy fixes such a class's enclosing instance
 * from where it was written - a class inside a closure gets a {@code Closure} - so lifting the body
 * into a method corrects that; the alternative, coercing a closure to a single-abstract-method type
 * ({@code { ... } as Handler<Order>}, parameterized under {@code @CompileStatic}), needs no inner
 * class at all and delegates rather than subclasses. A {@code .staticMethod()} bean cannot carry
 * one, having no enclosing instance to give it.</p>
 *
 * <p>They are also the only correct way to reach a sibling bean. A host Spring does not proxy - an
 * {@code @AutoConfiguration}, a generated plugin sibling, a Grails {@code Application} class -
 * returns a second instance from a direct call rather than the registered singleton, so such calls
 * are rejected.</p>
 *
 * <h2>Bean names</h2>
 *
 * The generated method's name is an implementation detail: Spring resolves the bean by its
 * {@code @Bean("name")} value, so a name that is not a valid Java identifier (e.g.
 * {@code "my-service"}) simply gets a synthesized {@code <type>$N} method name behind the scenes.
 *
 * <p>One name may be declared by several {@code bean(...)} statements - the standard Spring Boot
 * pattern for mutually exclusive variants of one bean - provided every declaration with that name
 * carries its own discriminating condition, so at most one of them registers at runtime.</p>
 *
 * <h2>Seeing what a block compiled to</h2>
 *
 * Build with {@code -Dgrails.beans.dsl.dumpdir=<dir>}: each host class writes a
 * {@code <qualified name>.beans.txt} there listing the generated members - bean names, the
 * annotations the qualifiers became, modifiers, declared types with any type arguments they ended
 * up carrying, and parameter annotations. Bodies are omitted, being the author's own closure bodies
 * lifted verbatim. Nothing is written unless the property is set.
 *
 * <h2>Where the generated methods go</h2>
 *
 * They work on any class Spring processes as a configuration source: a registered
 * {@code @AutoConfiguration} or {@code @Configuration} class, or the Spring Boot application class
 * itself (e.g. a Grails {@code Application} class) - Spring Boot reads {@code @Bean} methods
 * directly off the class it is launched with, so no further registration is needed there.
 *
 * <p>Nothing requires an application's wiring to be one block. A {@code beans} block that has grown
 * past comfortable reading splits into several {@code @GrailsBeans @Configuration} classes grouped
 * by what they wire - security, persistence, the SPI bridges a set of plugins expect - pulled in
 * with {@code @Import} from the application class. The split is free: bean names, injection and
 * conditions all behave identically, since each class is an ordinary configuration source by the
 * time Spring sees it.</p>
 *
 * <h2>Plugin descriptors</h2>
 *
 * May also be applied to a {@code grails.plugins.Plugin} subclass, letting bean definitions live in
 * the familiar {@code *GrailsPlugin.groovy} file. The generated methods land on a new sibling class
 * instead, named by the plugin-descriptor convention - a {@code *GrailsPlugin} name swaps that
 * suffix for {@code AutoConfiguration} ({@code I18nGrailsPlugin} -> {@code I18nAutoConfiguration}),
 * any other name appends it, and the plugin's own package holds it - or {@link #autoConfigurationName}
 * if given, which may name a package too.
 *
 * <p>A {@code Plugin} subclass is never processed by Spring as a bean, so {@code @AutoConfiguration}
 * together with every annotation that gates or configures it (the {@code @Conditional*} family,
 * {@code @Import}/{@code @ImportAutoConfiguration}/{@code @ImportResource}, {@code @ComponentScan},
 * {@code @EnableConfigurationProperties}, {@code @PropertySource}/{@code @PropertySources},
 * {@code @AutoConfigureOrder}/{@code Before}/{@code After} - including any composed annotation
 * meta-annotated with one of these) found on the plugin class moves onto that sibling, since none of
 * them has any effect where the author wrote them. Annotations outside that set can be moved
 * explicitly via {@link #moveAnnotations}.</p>
 */
/*
 * CLASS retention: the transform consumes this at canonicalization and it is not among the
 * annotations moved to the generated sibling, so it stays on the source class with nothing left to
 * read it. Keeping it out of the runtime image matters a little more than usual here, since
 * grails-core declares grails-beans-dsl api and so puts this module on every application's runtime
 * classpath.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@GroovyASTTransformationClass("org.grails.compiler.beans.GrailsBeansASTTransformation")
public @interface GrailsBeans {

    /**
     * The name of the generated sibling class, for a {@code grails.plugins.Plugin} subclass. The
     * default derives from the plugin's own name - a {@code *GrailsPlugin} class swaps that suffix
     * for {@code AutoConfiguration}, any other name appends it - in the plugin's own package. Set
     * this when converting an existing public {@code @AutoConfiguration} class whose name doesn't
     * follow that convention and whose class identity must be preserved (e.g. for
     * {@code exclude = } references, {@code before=}/{@code after=} ordering from other modules, or
     * tests that import it by name).
     *
     * <p>A bare identifier renames the class within the plugin's package. Class identity is the
     * qualified name, though, and a plugin descriptor sits in the package its implementation
     * classes sit beneath rather than alongside them, so the class being replaced is often in
     * another package: give the qualified name and the sibling is generated there instead, e.g.
     * {@code @GrailsBeans(autoConfigurationName = "com.example.web.ExampleAutoConfiguration")} on
     * {@code com.example.ExampleGrailsPlugin}. A name containing a dot is taken as written - it is
     * never resolved relative to the plugin's package. Generating into a package the plugin does
     * not otherwise own splits that package across two jars, which a modular or native-image
     * consumer pays for, so name one of the plugin's own.
     */
    String autoConfigurationName() default "";

    /**
     * Additional annotation types to move from a {@code grails.plugins.Plugin} subclass onto its
     * generated sibling, beyond the ones recognised automatically. The automatic set covers the
     * common Spring configuration annotations (and anything meta-annotated with them), but it is
     * a closed list - an annotation outside it that Spring reads off the configuration class
     * (rather than one this DSL happens to know about) would otherwise silently stay on the
     * plugin class, where Spring never sees it. E.g.
     * {@code @GrailsBeans(moveAnnotations = [SomeVendorAnnotation])}.
     */
    Class<?>[] moveAnnotations() default {};

}
