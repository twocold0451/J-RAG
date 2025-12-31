package com.twocold.jrag.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Observed {
    /**
     * Operation name. If empty, method name will be used.
     */
    String name() default "";

    /**
     * Whether to capture method arguments as input.
     */
    boolean captureInput() default true;

    /**
     * Fields to include in the input serialization.
     * Empty means all fields.
     */
    String[] includeInputFields() default {};

    /**
     * Whether to capture method return value as output.
     */
    boolean captureOutput() default true;

    /**
     * Fields to include in the output serialization (e.g. "id", "content").
     * Empty means all fields.
     */
    String[] includeOutFields() default {};

    /**
     * Maximum number of items to record if the output is a collection.
     * -1 means no limit.
     */
    int collectionLimit() default -1;

    /**
     * LangFuse observation type: "SPAN" or "GENERATION".
     */
    String type() default "SPAN";
}
