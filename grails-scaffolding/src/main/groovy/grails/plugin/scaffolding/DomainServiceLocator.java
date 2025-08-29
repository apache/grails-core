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

package grails.plugin.scaffolding;

/**
 * Resolves the appropriate *Service bean for a given domain class by:
 *   1) Fast-path bean name guesses ("<simpleName>Service", "<packageLeaf><SimpleName>Service"),
 *      validated by resource.instanceOf(domainClass).
 *   2) Scanning beans that extend GormService<?> (directly or indirectly) and matching via resource.instanceOf(..).
 *   3) Tie-breakers: prefer guessed bean names, then same package-leaf as the domain.
 *
 * Keeps a single shared cache for the whole app.
 */
public final class DomainServiceLocator {

    private static final ConcurrentMap<Class<?>, Object> CACHE = new ConcurrentHashMap<>();

    private DomainServiceLocator() {}

    /** Resolve (and cache) a service bean for the given domain class. */
    public static Object resolve(Class<?> domainClass) {
        return CACHE.computeIfAbsent(domainClass, DomainServiceLocator::findService);
    }

    /** Clear cache (useful in tests/dev reloads). */
    public static void clear() { CACHE.clear(); }

    // ---------------------------------------------------------------------
    // Core resolution
    // ---------------------------------------------------------------------

    private static Object findService(Class<?> domainClass) {
        ApplicationContext ctx = Holders.getGrailsApplication().getMainContext();

        // Fast-path guesses: <simpleName>Service, <packageLeaf><SimpleName>Service
        String simpleNameBean = GrailsNameUtils.getPropertyName(domainClass.getSimpleName()) + "Service";
        String altName = null;
        Package pkg = domainClass.getPackage();
        if (pkg != null) {
            String[] parts = pkg.getName().split("\\.");
            String leaf = parts[parts.length - 1];
            altName = GrailsNameUtils.getPropertyName(leaf + domainClass.getSimpleName()) + "Service";
        }

        // 1) Try guessed bean names first (validate via resource.instanceOf(domainClass))
        if (ctx.containsBean(simpleNameBean)) {
            Object bean = ctx.getBean(simpleNameBean);
            if (serviceMatchesDomain(bean, domainClass)) return bean;
        }
        if (altName != null && ctx.containsBean(altName)) {
            Object bean = ctx.getBean(altName);
            if (serviceMatchesDomain(bean, domainClass)) return bean;
        }

        // 2) Collect all beans that ultimately extend GormService<?>
        List<Object> candidates = collectGormServiceCandidates(ctx);

        // Filter by match (resource.instanceOf(..))
        List<Object> matches = new ArrayList<>();
        for (Object cand : candidates) {
            if (serviceMatchesDomain(cand, domainClass)) matches.add(cand);
        }

        if (matches.size() == 1) return matches.get(0);
        if (matches.isEmpty()) {
            // Optional last-resort: scan any *Service bean and try matching
            List<Object> fallback = scanAllServiceBeansAndMatch(ctx, domainClass);
            if (fallback.size() == 1) return fallback.get(0);
            if (fallback.isEmpty()) {
                throw new IllegalStateException(
                        "No service found for domain " + domainClass.getName() +
                                " (checked bean name and resource.instanceOf(..) across GormService subclasses)."
                );
            }
            return pickFromMultiple(fallback, simpleNameBean, altName, domainClass);
        }

        // Multiple matches: tie-breakers
        return pickFromMultiple(matches, simpleNameBean, altName, domainClass);
    }

    // ---------------------------------------------------------------------
    // Candidate collection & matching
    // ---------------------------------------------------------------------

    /** Collect beans that extend GormService<?> (directly or via subclasses like GormMongoService<?>). */
    private static List<Object> collectGormServiceCandidates(ApplicationContext ctx) {
        List<Object> candidates = new ArrayList<>();

        // Fast path: any bean assignable to GormService
        String[] names = ctx.getBeanNamesForType(GormService.class);
        for (String n : names) candidates.add(ctx.getBean(n));
        if (!candidates.isEmpty()) return candidates;

        // Fallback: scan all *Service beans, keep those whose class ultimately extends GormService
        for (String name : ctx.getBeanDefinitionNames()) {
            if (!name.endsWith("Service")) continue;
            Object bean = ctx.getBean(name);
            if (extendsGormService(AopUtils.getTargetClass(bean))) candidates.add(bean);
        }
        return candidates;
    }

    /** True if the given class or any superclass equals GormService. */
    private static boolean extendsGormService(Class<?> type) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            if (GormService.class.equals(c)) return true;
            c = c.getSuperclass();
        }
        return false;
    }

    /** Robust domain match: use service.resource.instanceOf(domainClass). */
    private static boolean serviceMatchesDomain(Object bean, Class<?> domainClass) {
        Object resource = extractResource(bean);
        if (resource instanceof GormEntity) {
            @SuppressWarnings("unchecked")
            GormEntity<?> ge = (GormEntity<?>) resource;
            return ge.instanceOf(domainClass);
        }
        return false;
    }

    /** Try to read service.resource (Groovy property → getResource()) or direct field access on target class. */
    private static Object extractResource(Object bean) {
        try {
            Method m = bean.getClass().getMethod("getResource");
            return m.invoke(bean);
        } catch (NoSuchMethodException e) {
            Class<?> target = AopUtils.getTargetClass(bean);
            Field f = findField(target, "resource");
            if (f != null) {
                try {
                    f.setAccessible(true);
                    return f.get(bean);
                } catch (Throwable ignore) { /* fall through */ }
            }
        } catch (Throwable ignore) {
            // ignore and treat as not found
        }
        return null;
    }

    /** Find a declared field in the class hierarchy. */
    private static Field findField(Class<?> type, String name) {
        Class<?> c = type;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    /** Last-resort scan across all *Service beans using the same resource-based match. */
    private static List<Object> scanAllServiceBeansAndMatch(ApplicationContext ctx, Class<?> domainClass) {
        List<Object> matches = new ArrayList<>();
        for (String name : ctx.getBeanDefinitionNames()) {
            if (!name.endsWith("Service")) continue;
            Object bean = ctx.getBean(name);
            if (serviceMatchesDomain(bean, domainClass)) matches.add(bean);
        }
        return matches;
    }

    // ---------------------------------------------------------------------
    // Tie-breakers
    // ---------------------------------------------------------------------

    private static Object pickFromMultiple(List<Object> matches, String simpleNameBean, String altName, Class<?> domainClass) {
        ApplicationContext ctx = Holders.getGrailsApplication().getMainContext();

        // Prefer bean whose name equals a fast-path guess (compare target classes)
        for (String guess : new String[]{simpleNameBean, altName}) {
            if (guess == null) continue;
            if (ctx.containsBean(guess)) {
                Object bean = ctx.getBean(guess);
                Class<?> guessTarget = AopUtils.getTargetClass(bean);
                for (Object m : matches) {
                    if (AopUtils.getTargetClass(m).equals(guessTarget)) return m;
                }
            }
        }

        // Prefer same package *leaf* as the domain (e.g., "admin" for org.website.admin.User)
        String domainLeaf = packageLeaf(domainClass);
        if (domainLeaf != null) {
            List<Object> sameLeaf = new ArrayList<>();
            for (Object m : matches) {
                String leaf = packageLeaf(AopUtils.getTargetClass(m));
                if (domainLeaf.equals(leaf)) sameLeaf.add(m);
            }
            if (sameLeaf.size() == 1) return sameLeaf.get(0);
        }

        throw new IllegalStateException("Multiple services match domain " + domainClass.getName()
                + ": " + classNames(matches));
    }

    private static String packageLeaf(Class<?> clazz) {
        Package p = clazz.getPackage();
        if (p == null) return null;
        String name = p.getName();
        int idx = name.lastIndexOf('.');
        return (idx >= 0) ? name.substring(idx + 1) : name;
    }

    private static String classNames(List<Object> beans) {
        List<String> names = new ArrayList<>(beans.size());
        for (Object b : beans) names.add(AopUtils.getTargetClass(b).getName());
        return names.toString();
    }
}
