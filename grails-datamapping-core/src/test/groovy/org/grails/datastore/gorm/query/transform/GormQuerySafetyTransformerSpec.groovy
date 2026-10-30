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
package org.grails.datastore.gorm.query.transform

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.messages.WarningMessage

import spock.lang.Specification
import spock.lang.Unroll

class GormQuerySafetyTransformerSpec extends Specification {

    private List<WarningMessage> compileAndCollectWarnings(String source) {
        CompilationUnit unit = new CompilationUnit(new GroovyClassLoader())
        def sourceUnit = unit.addSource("Book.groovy", source)
        unit.compile(Phases.CANONICALIZATION)
        sourceUnit.getErrorCollector().getWarnings() ?: []
    }

    void "test String query flattened from an interpolated GString before executeQuery fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        String q = "from Book where title = ${title}"
        executeQuery(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")
    }

    @Unroll
    void "test flattening via #description before find fails to compile"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static Book byTitle(String title) {
        $declaration
        find(q)
    }
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')

        where:
        description        | declaration
        '.toString()'      | 'def q = "from Book where title = ${title}".toString()'
        'as String'        | 'String q = ("from Book where title = ${title}" as String)'
        '(String) cast'    | 'String q = (String) "from Book where title = ${title}"'
        'reassignment'     | 'String q; q = "from Book where title = ${title}"'
    }

    @Unroll
    void "test inline #description at the call site fails to compile"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        executeQuery($argument)
    }
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')

        where:
        description     | argument
        '.toString()'   | '"from Book where title = ${title}".toString()'
        '(String) cast' | '(String) "from Book where title = ${title}"'
        'as String'     | '("from Book where title = ${title}" as String)'
    }

    @Unroll
    void "test aliasing via #description before find fails to compile"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static Book byTitle(String title) {
        $declaration
        find(q)
    }
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')

        where:
        description                      | declaration
        'a variable holding a GString'   | 'def g = "from Book where title = ${title}"\nString q = g'
        '.toString() on an alias'        | 'def g = "from Book where title = ${title}"\nString q = g.toString()'
        'a two-hop alias chain'          | 'def g = "from Book where title = ${title}"\ndef h = g\nString q = h'
        'assigning an already-flattened alias' | 'String p = "from Book where title = ${title}"\nString q = p'
    }

    void "test a variable flattened in only one branch of an if/else still fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static Book byTitle(String title, boolean condition) {
        String q
        if (condition) {
            q = "from Book where title = ${title}"
        } else {
            q = "from Book"
        }
        find(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    void "test a variable flattened only in the else branch still fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static Book byTitle(String title, boolean condition) {
        String q
        if (condition) {
            q = "from Book"
        } else {
            q = "from Book where title = ${title}"
        }
        find(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    void "test if/else where both branches build a safe query compiles cleanly"() {
        when:
        Class<?> bookClass = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static Book byTitle(String title, boolean condition) {
        String q
        if (condition) {
            q = "from Book where title = :title"
        } else {
            q = "from Book"
        }
        find(q, [title: title])
    }
}
''')

        then:
        bookClass != null
    }

    @Unroll
    void "test a variable assigned data on one path of #construct is not treated as constant text"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List sorted(String sort, List<String> sorts) {
        String frag = ""
        $body
        String q = "from Book order by \${frag}"
        executeQuery(q)
    }
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")

        where:
        construct                                          | body
        'an if/else'                                       | 'if (sort) { frag = sort } else { frag = " title" }'
        'a try/catch'                                      | 'try { frag = sort } catch (Exception ex) { frag = "" }'
        'a try block that overwrites it before the catch'  | 'try { frag = sort; frag.length(); frag = " title" } catch (Exception ex) { }'
        'a switch'                                         | 'switch (sort) { case "a": frag = sort; break; default: frag = " title" }'
        'a switch with no default'                         | 'switch (sort) { case "a": frag = sort; break }'
        'a for loop'                                       | 'for (String s in sorts) { frag = s }'
        'a while loop'                                     | 'while (sorts) { frag = sorts.remove(0) }'
        'a do/while loop'                                  | 'do { frag = sort } while (false)'
        'a loop that breaks after the assignment'          | 'for (String s in sorts) { if (s) { frag = s; break } }'
        'a loop that continues after the assignment'       | 'for (String s in sorts) { if (s) { frag = s; continue } }'
        'a closure'                                        | 'sorts.each { frag = it }'
        'a closure that returns after the assignment'      | 'sorts.each { frag = it; return }'
        'a multiple assignment'                            | 'def other\n        (frag, other) = [sort, 1]'
        'a ternary that assigns'                           | 'sort ? (frag = sort) : (frag = " title")'
        'a short-circuit operand that assigns'             | 'frag = sort\n        sort.isEmpty() || (frag = " title")'
        'a safe-navigation call argument that assigns'     | 'frag = sort\n        sorts?.add(frag = " title")'
        'a closure called after a constant reassignment'   | 'Closure reset = { frag = sort }\n        frag = " title"\n        reset()'
        'an anonymous inner class'                         | 'Runnable r = new Runnable() {\n            void run() {\n                frag = sort\n            }\n        }\n        r.run()'
        'an anonymous inner class initializer'             | 'Object o = new Object() {\n            {\n                frag = sort\n            }\n        }'
        'a finally block a break out of a loop runs'       | 'for (String s in sorts) {\n            try {\n                if (s == "stop") break\n            } finally {\n                frag = sort\n            }\n            frag = " id"\n        }'
        'a finally block a break out of a switch runs'     | 'switch (sort) {\n            case "a":\n                try {\n                    if (sorts) break\n                } finally {\n                    frag = sort\n                }\n                frag = " id"\n                break\n        }'
        'a finally block a continue runs'                  | 'for (String s in sorts) {\n            try {\n                if (s == "skip") continue\n            } finally {\n                frag = sort\n            }\n            frag = " id"\n        }'
        'an outer finally block a continue runs'           | 'for (String s in sorts) {\n            try {\n                try {\n                    if (s == "skip") continue\n                } finally {\n                    println(s)\n                }\n            } finally {\n                frag = sort\n            }\n            frag = " id"\n        }'
        'a finally block an exception runs before a catch' | 'String held = " title"\n        try {\n            try {\n                frag = sort\n                println(frag)\n                frag = " id"\n            } finally {\n                held = frag\n            }\n        } catch (Exception ex) {\n            frag = held\n        }'
        'the same finally block and catch in a loop'       | 'for (String s in sorts) {\n            String held = " title"\n            try {\n                try {\n                    frag = sort\n                    println(frag)\n                    frag = " id"\n                } finally {\n                    held = frag\n                }\n            } catch (Exception ex) {\n                frag = held\n            }\n        }'
    }

    void "test a variable assigned data later in a loop body is unsafe at a use earlier in it"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static void sorted(List<String> sorts) {
        String frag = " title"
        for (String sort in sorts) {
            String q = "from Book order by ${frag}"
            executeQuery(q)
            frag = sort
        }
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.errorCollector.errorCount == 1
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")
    }

    void "test a continue carries its state to the next iteration"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static void byTitle(List<String> titles) {
        String q = "from Book"
        for (String title in titles) {
            executeQuery(q)
            if (title) {
                q = "from Book where title = ${title}"
                continue
            }
            q = "from Book"
        }
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.errorCollector.errorCount == 1
        e.message.contains('GormUnsafeQueryString')
    }

    void "test a labelled break carries its state to the loop it leaves"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(List<String> titles) {
        String q = "from Book"
        outer:
        for (String title in titles) {
            for (String other in titles) {
                if (other == title) {
                    q = "from Book where title = ${title}"
                    break outer
                }
            }
            q = "from Book"
        }
        executeQuery(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    void "test a case entered by falling through from a case that assigned data sees that data"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List sorted(String mode, String sort) {
        String frag = " title"
        switch (mode) {
            case "custom":
                frag = sort
            case "custom-or-title":
                String q = "from Book order by ${frag}"
                return executeQuery(q)
            default:
                return []
        }
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    void "test a case that breaks does not fall through into the next case"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List sorted(String mode, String sort) {
        String frag = " title"
        String query = "from Book"
        switch (mode) {
            case "custom":
                frag = sort
                break
            case "title":
                query = "from Book order by ${frag}"
                break
        }
        executeQuery(query)
    }
}
''')

        then:
        warnings.empty
    }

    void "test a query in a finally block is checked against every path into it"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static void byTitle(String title) {
        String q = "from Book"
        try {
            q = "from Book where title = ${title}"
            q = "from Book"
        } finally {
            executeQuery(q)
        }
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.errorCollector.errorCount == 1
        e.message.contains('GormUnsafeQueryString')
    }

    void "test a query in a finally block sees the state a return leaves with"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        String q = "from Book"
        try {
            if (title) {
                return [q = "from Book where title = ${title}"]
            }
            q = "from Book"
        } finally {
            executeQuery(q)
        }
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.errorCollector.errorCount == 1
        e.message.contains('GormUnsafeQueryString')
    }

    @Unroll
    void "test #description compiles cleanly with no warnings"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List search(List<String> titles, String title, boolean condition) {
        Map queryParams = [title: title]
        $body
        executeQuery(q, queryParams)
    }
}
""")

        then:
        warnings.empty

        where:
        description                                                   | body
        'constant text appended in a loop'                            | 'String q = "from Book b where 1 = 1"\n        for (String t in titles) {\n            q += " and b.title = :title"\n        }'
        'constant text chosen in every case of a switch'              | 'String q\n        switch (title) {\n            case "a": q = "from Book b where b.title = :title"; break\n            default: q = "from Book b"\n        }'
        'constant text assigned in both a try and its catch block'    | 'String q\n        try {\n            q = "from Book b where b.title = :title"\n        } catch (Exception ex) {\n            q = "from Book b"\n        }'
        'a query reassigned safely in a try block with a finally'     | 'String q = "from Book b where b.title = ${title}"\n        try {\n            q = "from Book b where b.title = :title"\n        } finally {\n            queryParams.title = title\n        }'
        'a branch that returns before reaching the query'             | 'String q = "from Book b"\n        if (condition) {\n            q = "from Book b where b.title = ${title}"\n            return [q]\n        }'
        'a loop iteration that returns before reaching the query'     | 'String q = "from Book b"\n        for (String t in titles) {\n            if (t == title) {\n                q = "from Book b where b.title = ${t}"\n                return [q]\n            }\n        }'
        'a finally block that resets the query before a break lands'  | 'String q = "from Book b"\n        for (String t in titles) {\n            try {\n                q = "from Book b where b.title = ${t}"\n                if (t == title) break\n            } finally {\n                q = "from Book b where b.title = :title"\n            }\n        }'
        'constant text appended inside an anonymous inner class'      | 'String q = "from Book b where 1 = 1"\n        Runnable r = new Runnable() {\n            void run() {\n                q += " and b.title = :title"\n            }\n        }\n        r.run()'
        'constant text read inside an anonymous inner class'          | 'String restriction = condition ? " and b.title = :title" : ""\n        Runnable r = new Runnable() {\n            void run() {\n                String inner = "from Book b where 1 = 1 ${restriction}"\n                Book.executeQuery(inner, queryParams)\n            }\n        }\n        r.run()\n        String q = "from Book b"'
        'constant text chosen by a switch expression'                 | 'String restriction = switch (title) {\n            case "" -> ""\n            default -> " and b.title = :title"\n        }\n        String q = "from Book b where 1 = 1 ${restriction}"'
        'constant text yielded by a switch expression block'          | 'String restriction = switch (title) {\n            case "" -> { yield "" }\n            default -> { yield " and b.title = :title" }\n        }\n        String q = "from Book b where 1 = 1 ${restriction}"'
        'constant text in a local a switch expression block yields'   | 'String restriction = switch (title) {\n            case "" -> ""\n            default -> {\n                String fragment = " and b.title = :title"\n                yield fragment\n            }\n        }\n        String q = "from Book b where 1 = 1 ${restriction}"'
        'constant text yielded by a colon-form switch expression'     | 'String restriction = switch (title) {\n            case "": yield ""\n            default: yield " and b.title = :title"\n        }\n        String q = "from Book b where 1 = 1 ${restriction}"'
        'constant text yielded by a nested switch expression'         | 'String restriction = switch (title) {\n            case "" -> ""\n            default -> switch (condition) {\n                case true -> " and b.title = :title"\n                default -> ""\n            }\n        }\n        String q = "from Book b where 1 = 1 ${restriction}"'
        'a local reassigned safely inside a switch expression arm'    | 'String chosen = switch (title) {\n            case "a" -> {\n                String inner = "from Book b where b.title = ${title}"\n                inner = "from Book b where b.title = :title"\n                Book.executeQuery(inner, queryParams)\n                yield inner\n            }\n            default -> "from Book b"\n        }\n        String q = "from Book b"'
        'constant text returned by a closure called on the spot'      | 'String restriction = {\n            if (condition) {\n                " and b.title = :title"\n            } else {\n                ""\n            }\n        }()\n        String q = "from Book b where 1 = 1 ${restriction}"'
        'constant text assigned by a multiple-assignment declaration' | 'def (String restriction, String order) = [" and b.title = :title", " order by b.title"]\n        String q = "from Book b where 1 = 1 ${restriction}${order}"'
        'a GString passed directly from inside a closure'             | 'def g = "from Book b where b.title = ${title}"\n        Book.withTransaction {\n            executeQuery(g)\n        }\n        String q = "from Book b"'
        'a ternary choosing between GStrings passed directly'         | 'executeQuery(condition ? "from Book b where b.title = ${title}" : "from Book b")\n        String q = "from Book b"'
    }

    @Unroll
    void "test a use inside #construct sees data assigned where the walk cannot place it"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static void sorted(String sort, List<String> sorts) {
        String frag = " title"
        $body
    }
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")

        where:
        construct                                   | body
        'a do/while body assigned in its condition' | 'do {\n            String q = "from Book order by ${frag}"\n            executeQuery(q)\n        } while ((frag = sorts.remove(0)) != null)'
        'a closure called after a reassignment'     | 'Closure run = {\n            String q = "from Book order by ${frag}"\n            executeQuery(q)\n        }\n        frag = sort\n        run()'
        'a closure called after a flattening'       | 'String q = "from Book"\n        Closure run = {\n            executeQuery(q)\n        }\n        q = "from Book order by ${sort}"\n        run()'
        'an anonymous inner class'                  | 'String q = "from Book order by ${sort}"\n        Runnable run = new Runnable() {\n            void run() {\n                Book.executeQuery(q)\n            }\n        }\n        run.run()'
        'an anonymous inner class run later'        | 'Runnable run = new Runnable() {\n            void run() {\n                String q = "from Book order by ${frag}"\n                Book.executeQuery(q)\n            }\n        }\n        frag = sort\n        run.run()'
    }

    @Unroll
    void "test #construct reusing the name of an earlier constant local is not treated as constant text"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static void sorted(String sort, List<String> sorts) {
        $body
    }
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")

        where:
        construct                                | body
        'a loop variable after a try block'      | 'try {\n            String part = " title"\n        } finally {\n        }\n        for (String part in sorts) {\n            String q = "from Book order by ${part}"\n            executeQuery(q)\n        }'
        'a loop variable after an if/else'       | 'if (sort) {\n            String part = " id"\n        } else {\n            String part = " title"\n        }\n        for (String part in sorts) {\n            String q = "from Book order by ${part}"\n            executeQuery(q)\n        }'
        'a closure parameter after an if/else'   | 'if (sort) {\n            String part = " id"\n        } else {\n            String part = " title"\n        }\n        sorts.each { String part ->\n            String q = "from Book order by ${part}"\n            executeQuery(q)\n        }'
    }

    @Unroll
    void "test a query flattened inside a closure is still flagged when #construct"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static void byTitle(List<String> titles, String title) {
        String q = "from Book"
        $body
    }
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")

        where:
        construct                                        | body
        'it is used after the closure is called'         | 'Closure flatten = { q = "from Book where title = ${title}" }\n        flatten()\n        executeQuery(q)'
        'a later run of the closure reaches the use'     | 'titles.each {\n            executeQuery(q)\n            q = "from Book where title = ${it}"\n        }'
        'the closure returns after flattening it'        | 'Closure flatten = {\n            if (title) {\n                q = "from Book where title = ${title}"\n                return\n            }\n            q = "from Book"\n        }\n        flatten()\n        executeQuery(q)'
    }

    @Unroll
    void "test a query flattened #construct fails to compile"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static void byTitle(List<String> titles, String title, boolean condition) {
        String q = "from Book"
        $body
    }
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")

        where:
        construct                                     | body
        'in one branch of a ternary initializer'      | 'String chosen = condition ? "from Book where title = ${title}" : "from Book"\n        executeQuery(chosen)'
        'in an Elvis initializer'                     | 'String chosen = "from Book where title = ${title}" ?: "from Book"\n        executeQuery(chosen)'
        'in one branch of a ternary passed directly'  | 'String flattened = "from Book where title = ${title}"\n        executeQuery(condition ? flattened : q)'
        'in a ternary concatenated onto the query'    | 'String chosen = "from Book" + (condition ? " where title = ${title}" : "")\n        executeQuery(chosen)'
        'in an arm of a switch expression'            | 'String chosen = switch (title) {\n            case "a" -> "from Book where title = ${title}"\n            default -> "from Book"\n        }\n        executeQuery(chosen)'
        'in a local a switch expression block yields' | 'String chosen = switch (title) {\n            case "a" -> {\n                String flattened = "from Book where title = ${title}"\n                yield flattened\n            }\n            default -> "from Book"\n        }\n        executeQuery(chosen)'
        'in a colon-form switch expression'           | 'String chosen = switch (title) {\n            case "a": yield "from Book where title = ${title}"\n            default: yield "from Book"\n        }\n        executeQuery(chosen)'
        'in a nested switch expression'               | 'String chosen = switch (title) {\n            case "a" -> switch (condition) {\n                case true -> "from Book where title = ${title}"\n                default -> "from Book"\n            }\n            default -> "from Book"\n        }\n        executeQuery(chosen)'
        'in a switch expression arm that assigns it'  | 'String chosen = switch (title) {\n            case "a" -> {\n                q = "from Book where title = ${title}"\n                yield "a"\n            }\n            default -> "b"\n        }\n        executeQuery(q)'
        'in a switch expression arm that executes it' | 'String chosen = switch (title) {\n            case "a" -> {\n                String inner = "from Book where title = ${title}"\n                executeQuery(inner)\n                yield "a"\n            }\n            default -> "b"\n        }'
        'in a result of a closure called on the spot' | 'String chosen = {\n            if (condition) {\n                "from Book where title = ${title}"\n            } else {\n                "from Book"\n            }\n        }()\n        executeQuery(chosen)'
        'in a ternary branch that assigns it'         | 'condition ? (q = "from Book where title = ${title}") : (q = "from Book")\n        executeQuery(q)'
        'by a multiple assignment'                    | 'def other\n        (q, other) = ["from Book where title = ${title}", 1]\n        executeQuery(q)'
        'by a multiple-assignment declaration'        | 'def (String chosen, other) = ["from Book where title = ${title}", 1]\n        executeQuery(chosen)'
        'in a do/while condition'                     | 'do {\n            executeQuery(q)\n        } while ((q = "from Book where title = ${titles.remove(0)}") != null)'
        'in a closure called after a reset'           | 'Closure flatten = { q = "from Book where title = ${title}" }\n        q = "from Book"\n        flatten()\n        executeQuery(q)'
        'in an anonymous inner class'                 | 'Runnable flatten = new Runnable() {\n            void run() {\n                q = "from Book where title = ${title}"\n            }\n        }\n        flatten.run()\n        executeQuery(q)'
    }

    @Unroll
    void "test a query called on this in #construct inside a domain class fails to compile"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    $member
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")

        where:
        construct                                   | member
        'an anonymous inner class, this implied'    | 'static void byTitle(String title) {\n        Runnable r = new Runnable() {\n            void run() {\n                String q = "from Book where title = ${title}"\n                executeQuery(q)\n            }\n        }\n        r.run()\n    }'
        'an anonymous inner class, this written'    | 'static void byTitle(String title) {\n        Runnable r = new Runnable() {\n            void run() {\n                String q = "from Book where title = ${title}"\n                this.executeQuery(q)\n            }\n        }\n        r.run()\n    }'
        'a static nested class'                     | 'static class Finder {\n        List byTitle(String title) {\n            String q = "from Book where title = ${title}"\n            executeQuery(q)\n        }\n    }'
        'an inner class'                            | 'class Finder {\n        List byTitle(String title) {\n            String q = "from Book where title = ${title}"\n            executeQuery(q)\n        }\n    }'
    }

    @Unroll
    void "test #construct inside a domain class compiles cleanly with no warnings"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings("""
import grails.gorm.annotation.Entity

@Entity
$annotation
class Book {
    String title

    $member
}
""")

        then:
        warnings.empty

        where:
        construct                                                              | annotation                                   | member
        'a nested class calling its own find method'                           | ''                                           | 'static class Finder {\n        List find(String query) { [] }\n        List byTitle(String title) {\n            String q = "from Book where title = ${title}"\n            find(q)\n        }\n    }'
        'a flattened query in an anonymous inner class of a suppressed method' | ''                                           | '@SuppressWarnings("GormUnsafeQueryString")\n    static void byTitle(String title) {\n        Runnable r = new Runnable() {\n            void run() {\n                String q = "from Book where title = ${title}"\n                executeQuery(q)\n            }\n        }\n        r.run()\n    }'
        'a flattened query in a nested class of a suppressed class'            | '@SuppressWarnings("GormUnsafeQueryString")' | 'static class Finder {\n        List byTitle(String title) {\n            String q = "from Book where title = ${title}"\n            executeQuery(q)\n        }\n    }'
        'a suppressed local read in an anonymous inner class'                  | ''                                           | 'static void byTitle(String title) {\n        @SuppressWarnings("GormUnsafeQueryString")\n        String q = "from Book where title = ${title}"\n        Runnable r = new Runnable() {\n            void run() {\n                executeQuery(q)\n            }\n        }\n        r.run()\n    }'
    }

    void "test a constant local read inside a closure compiles cleanly with no warnings"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static void search(List<String> titles, Map queryParams) {
        String restriction = ""
        if (queryParams.title) {
            restriction = " and b.title = :title"
        }
        titles.each { String t ->
            String q = "from Book b where b.title <> :t ${restriction}"
            executeQuery(q, queryParams + [t: t])
        }
    }
}
''')

        then:
        warnings.empty
    }

    @Unroll
    void "test #construct nested #depth deep compiles without the check slowing it down exponentially"() {
        given:
        String open = (1..depth).collect { opening.replace('#', it.toString()) }.join('\n')
        String close = (1..depth).collect { '}' }.join('\n')
        String source = """
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List search(List<String> titles) {
        String q = "from Book b where 1 = 1"
        $open
        $innermost
        $close
        executeQuery(q)
    }
}
"""

        when:
        long started = System.nanoTime()
        List<WarningMessage> warnings = compileAndCollectWarnings(source)
        long elapsedMillis = (System.nanoTime() - started).intdiv(1_000_000)

        then: 'every level walked twice would take minutes at this depth'
        warnings.empty
        elapsedMillis < 10_000

        where:
        construct                         | depth | opening                        | innermost
        'closures'                        | 24    | 'titles.each { String t# ->'   | 'println(t1)'
        'for loops'                       | 24    | 'for (String t# in titles) {'  | 'println(t1)'
        'for loops appending to a query'  | 24    | 'for (String t# in titles) {'  | 'q += " and b.title is not null"'
        'closures appending to a query'   | 24    | 'titles.each { String t# ->'   | 'q += " and b.title is not null"'
        'finally blocks'                  | 24    | 'try { println(#) } finally {' | 'println(0)'
    }

    void "test aliasing a plain non-interpolated variable compiles cleanly"() {
        when:
        Class<?> bookClass = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List all() {
        def g = "from Book"
        String q = g
        executeQuery(q)
    }
}
''')

        then:
        bookClass != null
    }

    void "test a GString literal passed directly compiles cleanly"() {
        when:
        Class<?> bookClass = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        executeQuery("from Book where title = ${title}")
    }
}
''')

        then:
        bookClass != null
    }

    void "test a plain non-interpolated String compiles cleanly"() {
        when:
        Class<?> bookClass = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List all() {
        String q = "from Book"
        executeQuery(q)
    }
}
''')

        then:
        bookClass != null
    }

    void "test a flattened query suppressed with @SuppressWarnings compiles cleanly"() {
        when:
        Class<?> bookClass = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    @SuppressWarnings("GormUnsafeQueryString")
    static List byTitle(String title) {
        String q = "from Book where title = ${title}"
        executeQuery(q)
    }
}
''')

        then:
        bookClass != null
    }

    void "test a flattened query in a constructor fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title
}

class BookReport {

    List books

    BookReport(String title) {
        String q = "from Book where title = ${title}"
        books = Book.executeQuery(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")
    }

    void "test a flattened query suppressed on its constructor compiles cleanly"() {
        when:
        Class<?> bookClass = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title
}

class BookReport {

    List books

    @SuppressWarnings("GormUnsafeQueryString")
    BookReport(String title) {
        String q = "from Book where title = ${title}"
        books = Book.executeQuery(q)
    }
}
''')

        then:
        bookClass != null
    }

    void "test a non-domain class with its own find/findAll(String) methods does not false-positive"() {
        when:
        Class<?> planClass = new GroovyClassLoader().parseClass('''
class Plan {
    String find(String query) {
        return query
    }

    List findAll(String query) {
        return [query]
    }

    static String byName(String name) {
        String q = "plan ${name}"
        new Plan().find(q)
    }
}
''')

        then:
        planClass != null
    }

    void "test Collection.find/.findAll with a closure argument does not false-positive"() {
        when:
        Class<?> bookClass = new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static Object pickOne(List titles, String target) {
        String q = "picking ${target}"
        titles.find { it == q }
        titles.findAll { it == q }
    }
}
''')

        then:
        bookClass != null
    }

    void "test findPathTo detects a flattened query at argument index 1, not 0"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static Object toOther(Class other, String title) {
        String q = "MATCH (b:Book)-[*]->(o) WHERE b.title = ${title} RETURN o"
        findPathTo(other, q, [:])
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'findPathTo'")
    }

    void "test a flattened variable referenced inside a nested closure still fails to compile"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static void byTitles(List titles) {
        titles.each { String title ->
            String q = "from Book where title = ${title}"
            executeQuery(q)
        }
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    void "test a field flattened via its own GString initializer warns but still compiles"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title
    String cachedQuery = "from Book where title = ${title}"

    List loadCached() {
        executeQuery(this.cachedQuery)
    }
}
''')

        then:
        warnings.size() == 1
        warnings[0].message.contains('GormUnsafeQueryString')
        warnings[0].message.contains("passed to 'executeQuery'")
    }

    void "test a field flattened via this.field assignment warns but still compiles"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title
    String cachedQuery

    void prepare(String title) {
        this.cachedQuery = "from Book where title = ${title}"
    }

    List loadCached() {
        executeQuery(this.cachedQuery)
    }
}
''')

        then:
        warnings.size() == 1
        warnings[0].message.contains('GormUnsafeQueryString')
    }

    void "test a field safely reassigned does not warn"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title
    String cachedQuery = "from Book"

    List loadCached() {
        this.cachedQuery = "from Book where title = :title"
        executeQuery(this.cachedQuery, [title: title])
    }
}
''')

        then:
        warnings.empty
    }

    void "test string concatenation with a non-constant value warns but still compiles"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        String q = "from Book where title = " + title
        executeQuery(q)
    }
}
''')

        then:
        warnings.size() == 1
        warnings[0].message.contains('GormUnsafeQueryString')
        warnings[0].message.contains("passed to 'executeQuery'")
    }

    void "test string concatenation passed directly as the query argument warns but still compiles"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        executeQuery("from Book where title = " + title)
    }
}
''')

        then:
        warnings.size() == 1
        warnings[0].message.contains('GormUnsafeQueryString')
    }

    void "test concatenating two constant strings does not warn"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List all() {
        String q = "from " + "Book"
        executeQuery(q)
    }
}
''')

        then:
        warnings.empty
    }

    void "test concatenating a GString with more text fails to compile as flattening, not as a lesser concatenation warning"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        String q = "from Book where title = ${title}" + " order by title"
        executeQuery(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")
    }

    @Unroll
    void "test a GString whose interpolations are all constant text (#description) compiles cleanly with no warnings"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    static final String ORDER_BY = ' order by b.title'
    static final String DEFAULT_ORDER = ORDER_BY + ', b.id'
    String title

    static List search(Map params, boolean condition) {
        Map queryParams = [:]
        $declaration
        String q = "from Book b where 1 = 1 \${restriction}"
        executeQuery(q, queryParams)
    }
}
""")

        then:
        warnings.empty

        where:
        description                                  | declaration
        'a string literal'                           | 'String restriction = " and b.title = :title"'
        'a local reassigned in one branch'           | 'String restriction = ""\n        if (params.title) {\n            restriction = " and b.title = :title"\n            queryParams.title = params.title\n        }'
        'a local assigned in both branches'          | 'String restriction\n        if (params.title) {\n            restriction = " and b.title = :title"\n        } else {\n            restriction = ""\n        }'
        'a ternary over literals'                    | 'String restriction = params.title ? " and b.title = :title" : ""'
        'an Elvis over constant text'                | 'String base = condition ? null : " and b.title = :title"\n        String restriction = base ?: ""'
        'a static final field'                       | 'String restriction = ORDER_BY'
        'a qualified static final field'             | 'String restriction = Book.ORDER_BY'
        'a static final field built from another'    | 'String restriction = DEFAULT_ORDER'
        'a GString of constant text'                 | 'String restriction = " and b.title = ${":title"}"'
        'a concatenation of constant text'           | 'String restriction = " and " + "b.title = :title"'
        'an appended constant fragment'              | 'String restriction = ""\n        if (params.title) {\n            restriction += " and b.title = :title"\n        }'
        'a cast of constant text'                    | 'String restriction = (String) " and b.title = :title"'
        'toString() of constant text'                | 'String restriction = " and b.title = :title".toString()'
        'a two-hop alias of constant text'           | 'def first = " and b.title = :title"\n        def second = first\n        String restriction = second'
        'a ternary that assigns constant text'       | 'String restriction\n        condition ? (restriction = " and b.title = :title") : (restriction = "")'
        'constant text appended inside a closure'    | 'String restriction = ""\n        params.each { restriction += " and b.title = :title" }'
    }

    @Unroll
    void "test a GString interpolating #description is still flattened and fails to compile"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    static String mutableFragment = ' order by b.title'
    static final String PREFIX = 'from Book b where '
    String title

    static List search(Map params, String title, boolean condition) {
        $declaration
        String q = "from Book b where 1 = 1 \${restriction}"
        executeQuery(q)
    }
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')

        where:
        description                                    | declaration
        'a parameter'                                  | 'String restriction = title'
        'a local made non-constant in one branch'      | 'String restriction = ""\n        if (condition) {\n            restriction = title\n        }'
        'a local constant in only one branch'          | 'String restriction = title\n        if (condition) {\n            restriction = " and b.title = :title"\n        }'
        'a constant local appended with data'          | 'String restriction = " and b.title = "\n        restriction += title'
        'a ternary with a non-constant branch'         | 'String restriction = condition ? " and b.title = :title" : title'
        'a non-final static field'                     | 'String restriction = mutableFragment'
        'a local shadowing a static final constant'    | 'String PREFIX = title\n        String restriction = PREFIX'
        'a method call result'                         | 'String restriction = title.trim()'
        'a map value'                                  | 'String restriction = params.restriction'
        'an instance property'                         | 'String restriction = new Book().title'
    }

    void "test appending constant text with += and + to a constant query compiles cleanly with no warnings"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List search(Map params) {
        Map queryParams = [:]
        String order = " order by b.title"
        String q = "from Book b where 1 = 1"
        if (params.title) {
            q += " and b.title = :title"
            queryParams.title = params.title
        }
        q = q + order
        executeQuery(q, queryParams)
    }
}
''')

        then:
        warnings.empty
    }

    @Unroll
    void "test #description with += fails to compile as flattening"() {
        when:
        new GroovyClassLoader().parseClass("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        $declaration
        executeQuery(q)
    }
}
""")

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
        e.message.contains("passed to 'executeQuery'")

        where:
        description                               | declaration
        'appending text to a flattened query'     | 'String q = "from Book where title = ${title}"\n        q += " order by title"'
        'appending text to a live GString'        | 'def q = "from Book where title = ${title}"\n        q += " order by title"'
        'appending a live GString to constant text' | 'String q = "from Book where "\n        q += "title = ${title}"'
    }

    void "test concatenating more text onto an already-flattened query fails to compile rather than warning"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        String q = "from Book where title = ${title}"
        q = q + " order by title"
        executeQuery(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    void "test a non-plus compound assignment stops a local from counting as constant text"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        def restriction = " and b.title = "
        restriction <<= title
        String q = "from Book b where 1 = 1 ${restriction}"
        executeQuery(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    @Unroll
    void "test a flattened query suppressed on its declaration via #description compiles cleanly"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings("""
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        $annotation
        String q = "from Book where title = \${title}"
        executeQuery(q)
    }
}
""")

        then:
        warnings.empty

        where:
        description        | annotation
        'a single value'   | '@SuppressWarnings("GormUnsafeQueryString")'
        'a list of values' | '@SuppressWarnings(["unchecked", "GormUnsafeQueryString"])'
    }

    void "test a declaration-level suppression covers later reassignments of that variable"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List search(Map params) {
        @SuppressWarnings("GormUnsafeQueryString")
        String q = "from Book b where 1 = 1"
        if (params.title) {
            q = "${q} and b.title = ${params.title}"
        }
        q += params.order
        executeQuery(q)
    }
}
''')

        then:
        warnings.empty
    }

    void "test a declaration-level suppression on the fragment makes its interpolation constant text"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static String restrictionFor(Map params) {
        params.title ? " and b.title = :title" : ""
    }

    static List search(Map params) {
        @SuppressWarnings("GormUnsafeQueryString")
        String restriction = restrictionFor(params)
        String q = "from Book b where 1 = 1 ${restriction}"
        executeQuery(q, [title: params.title])
    }
}
''')

        then:
        warnings.empty
    }

    void "test a declaration-level suppression leaves every other variable checked"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        @SuppressWarnings("GormUnsafeQueryString")
        String reviewed = "from Book where title = ${title}"
        String q = "from Book where title = ${title}"
        executeQuery(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    void "test redeclaring a suppressed name without the annotation re-arms the check"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title, boolean condition) {
        if (condition) {
            @SuppressWarnings("GormUnsafeQueryString")
            String q = "from Book where title = ${title}"
            executeQuery(q)
        }
        String q = "from Book where title = ${title}"
        executeQuery(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('GormUnsafeQueryString')
    }

    void "test a declaration-level suppression also silences the concatenation warning for that variable"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        @SuppressWarnings("GormUnsafeQueryString")
        String q = "from Book where title = " + title
        executeQuery(q)
    }
}
''')

        then:
        warnings.empty
    }

    void "test a field suppressed on its declaration does not warn"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title
    @SuppressWarnings("GormUnsafeQueryString")
    String cachedQuery = "from Book where title = ${title}"

    List loadCached() {
        this.cachedQuery = "from Book where title = ${title}"
        executeQuery(this.cachedQuery)
    }
}
''')

        then:
        warnings.empty
    }

    void "test the error message explains the query text case and the declaration-level suppression"() {
        when:
        new GroovyClassLoader().parseClass('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        String q = "from Book where title = ${title}"
        executeQuery(q)
    }
}
''')

        then:
        def e = thrown(MultipleCompilationErrorsException)
        e.message.contains('HQL fragments')
        e.message.contains('GORM would bind the fragment as a parameter value')
        e.message.contains('to the declaration of the variable that holds the query text, or to the enclosing method')
    }

    void "test the concatenation warning points at the declaration-level suppression"() {
        when:
        List<WarningMessage> warnings = compileAndCollectWarnings('''
import grails.gorm.annotation.Entity

@Entity
class Book {
    String title

    static List byTitle(String title) {
        String q = "from Book where title = " + title
        executeQuery(q)
    }
}
''')

        then:
        warnings.size() == 1
        warnings[0].message.contains('to the declaration of the variable that holds the query text, or to the enclosing method')
    }
}
