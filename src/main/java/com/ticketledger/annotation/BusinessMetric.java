package com.ticketledger.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking business operations to be automatically recorded as
 * metrics.
 * <p>
 * This annotation can be applied to any method representing a key business
 * operation (e.g., booking creation).
 * When applied, it will automatically record success/failure counts and
 * optionally latency for the annotated method.
 */

@Target(ElementType.METHOD) // STRICT: Method-level only
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessMetric {
    /**
     * The metric name (e.g., "business.booking.attempt").
     */
    String name();

    /**
     * If true, records a timer for latency.
     */
    boolean recordLatency() default true;
}