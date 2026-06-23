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
package org.grails.gsp.observation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Records hit/miss for a GSP cache as the {@code gsp.cache} counter, tagged {@code cache}
 * (which cache: {@code template} or {@code view}) and {@code result} ({@code hit} / {@code miss}).
 *
 * <p>This deliberately instruments the caches that are actually consulted on the request path of a
 * <em>deployed</em> (non-development) application — the {@code <g:render>} template cache
 * ({@code GroovyPagesTemplateRenderer}) and the view-resolver cache ({@code GroovyPageViewResolver})
 * — rather than the {@code GroovyPagesTemplateEngine}'s runtime <em>compile</em> cache. A
 * production deployment serves GSPs precompiled, which bypasses the engine's compile cache entirely
 * (and the layers above intercept any second lookup), so the engine cache can never register a hit
 * in production and is therefore not a useful operational signal there.</p>
 *
 * <p>Note these caches do not expire by default ({@code cacheTimeout == -1}), so a steady-state
 * <em>ratio</em> sits at ~100%; the actionable signal is the <strong>miss rate</strong>, which
 * spikes on cold start / deploy and on any unexpected cache flush.</p>
 *
 * @since 8.0
 */
public final class GroovyPageCacheMetrics {

    /** A no-op instance used when no {@link MeterRegistry} is available. */
    public static final GroovyPageCacheMetrics NOOP = new GroovyPageCacheMetrics(null, null);

    private static final String METRIC_NAME = "gsp.cache";

    private final Counter hits;
    private final Counter misses;

    private GroovyPageCacheMetrics(Counter hits, Counter misses) {
        this.hits = hits;
        this.misses = misses;
    }

    /**
     * Builds the hit/miss counters for the named cache, or returns {@link #NOOP} when metrics are
     * not configured.
     *
     * @param meterRegistry the registry to register the counters with, or {@code null} to disable
     * @param cache the value for the {@code cache} tag, e.g. {@code "template"} or {@code "view"}
     * @return a recorder bound to {@code meterRegistry}, or {@link #NOOP}
     */
    public static GroovyPageCacheMetrics forCache(MeterRegistry meterRegistry, String cache) {
        if (meterRegistry == null) {
            return NOOP;
        }
        Counter hits = Counter.builder(METRIC_NAME).tag("cache", cache).tag("result", "hit")
                .description("GSP " + cache + " cache lookups served from cache").register(meterRegistry);
        Counter misses = Counter.builder(METRIC_NAME).tag("cache", cache).tag("result", "miss")
                .description("GSP " + cache + " cache lookups that had to build the entry").register(meterRegistry);
        return new GroovyPageCacheMetrics(hits, misses);
    }

    /**
     * Records a single cache access.
     *
     * @param hit {@code true} if the value was served from cache, {@code false} if it had to be built
     */
    public void record(boolean hit) {
        Counter counter = hit ? this.hits : this.misses;
        if (counter != null) {
            counter.increment();
        }
    }
}
