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
 * <p>The annotated class must declare a {@code beans} property initialised to a closure whose
 * statements are one of:
 * <ul>
 * <li>{@code bean(["name", ] Type) { ... }} - or {@code bean(["name", ] Type)} with no body at all,
 * which declares a bean that is simply {@code new Type()}, the most common shape. A closure with
 * parameters but an empty body means the same for a bean with dependencies: the parameters say what
 * is injected and the constructor call is generated from them, in the order written, leaving the
 * compiler to select the constructor from their types just as it would for a hand-written body. Both
 * forms require a concrete type; an interface or abstract type needs a body naming the
 * implementation. It chains with the qualifiers below exactly as the
 * closure form does ({@code bean(Foo).lazy().conditionalOnMissingBean()}). The closure form may be
 * chained with any combination of
 * {@code .conditionalOnMissingBean(...)} (positional types, the annotation's own named
 * attributes such as {@code name:}/{@code search:}, or no arguments at all to let Spring infer
 * the back-off type from the return type), {@code .conditionalOnMissingBeanName(...)} (backs off
 * by this bean's own name - set automatically, stated once - accepting the annotation's other
 * attributes but rejecting {@code name:} and types), {@code .primary()}, {@code .lazy()},
 * {@code .scope("name")}, {@code .staticMethod()} (a {@code static} factory method - Spring's
 * recommended shape for {@code BeanFactoryPostProcessor}/{@code BeanPostProcessor} beans, which
 * must be creatable without instantiating their declaring configuration class), and (repeatably)
 * {@code .annotate(AnnotationType[, attr: value, ...])} - the last a generic escape hatch
 * attaching any other single-valued annotation. The closure body becomes the generated method's
 * body verbatim, and closure parameters become the generated method's parameters (for
 * constructor-style bean injection). The generated method's name is an implementation detail:
 * Spring resolves the bean by its {@code @Bean("name")} value, so a bean name that isn't a valid
 * Java identifier (e.g. {@code "my-service"}) simply gets a synthesized {@code <type>$N} method
 * name behind the scenes. The same bean name may even be declared by more than one
 * {@code bean(...)} statement - the standard Spring Boot pattern for mutually exclusive variants
 * of one bean - provided every declaration with the name carries its own discriminating
 * condition (e.g. {@code .annotate(ConditionalOnProperty, ...)}) so that at most one of them
 * registers at runtime.</li>
 * <li>{@code field(["name", ] Type)}, optionally chained with {@code .value(...)} and/or
 * (repeatably) {@code .annotate(AnnotationType[, attr: value, ...])}. Declares a private field on
 * the generated class, for state shared across bean methods. The usual case is injected
 * configuration: {@code field("encoding", String).value(Settings.GSP_VIEW_ENCODING, "UTF-8")}
 * compiles to {@code @Value("${grails.views.gsp.encoding:UTF-8}")} - the two-argument form takes
 * a config key (a literal or a bare constant reference) plus default. The one-argument form
 * takes a bare config key with no default ({@code .value("app.encoding")} compiles to
 * {@code @Value("${app.encoding}")}), while a string already containing a {@code ${...}}
 * placeholder or {@code #{...}} SpEL expression passes through verbatim.</li>
 * <li>{@code method(["name", ] Type) { ... }}, chainable with {@code .annotate(...)} only
 * ({@code .value(...)} is field-specific).
 * Declares a private helper method on the generated class, for logic shared across bean methods,
 * lifted from the closure the same way {@code bean(...)} is.</li>
 * </ul>
 * When no name is given, one is derived from the type name following the JavaBeans convention
 * ({@link java.beans.Introspector#decapitalize(String)}).
 *
 * <p>The generated methods work on any class Spring processes as a configuration source: a
 * registered {@code @AutoConfiguration} or {@code @Configuration} class, or the Spring Boot
 * application class itself (e.g. a Grails {@code Application} class) - Spring Boot reads
 * {@code @Bean} methods directly off the class it is launched with, so no further registration
 * is needed there.
 *
 * <p>May also be applied to a {@code grails.plugins.Plugin} subclass, letting bean definitions
 * live in the familiar {@code *GrailsPlugin.groovy} file. In that case the generated methods land
 * on a new sibling class instead, named by the plugin-descriptor convention - a {@code *GrailsPlugin}
 * name swaps that suffix for {@code AutoConfiguration} ({@code I18nGrailsPlugin} ->
 * {@code I18nAutoConfiguration}), any other name appends it, and the plugin's own package holds it
 * - or {@link #autoConfigurationName} if given, which may name a package too. A {@code Plugin} subclass is never processed by
 * Spring as a bean, so {@code @AutoConfiguration} together with every annotation that gates or
 * configures it (the {@code @Conditional*} family, {@code @Import}/{@code @ImportAutoConfiguration}/
 * {@code @ImportResource}, {@code @ComponentScan}, {@code @EnableConfigurationProperties},
 * {@code @PropertySource}/{@code @PropertySources},
 * {@code @AutoConfigureOrder}/{@code Before}/{@code After} - including any composed annotation
 * meta-annotated with one of these) found on the plugin class moves onto that sibling, since none
 * of them has any effect where the author wrote them. Annotations outside that set can be moved
 * explicitly via {@link #moveAnnotations}.
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
