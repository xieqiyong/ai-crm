package com.hz.crm.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {

    String module();

    String action();

    String description();

    String targetType() default "";

    int targetArgument() default 0;

    String targetIdField() default "id";

    boolean recordParameters() default true;
}
