package io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Not covered by the compatibility promise yet: it may change or go away in any minor version, while it is tried
 * out. Addons that use it may need an update after a lobby update. Once it is settled the annotation is removed
 * and the usual promise applies from then on.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Experimental {
}
