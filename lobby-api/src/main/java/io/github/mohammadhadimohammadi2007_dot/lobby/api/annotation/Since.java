package io.github.mohammadhadimohammadi2007_dot.lobby.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The API version that added it, e.g. {@code "1.2"}. Everything without it exists since 1.0. An addon that uses
 * it needs that version: set {@code api-version} in addon.json to it, or check first with
 * {@code ApiVersion.supports("1.2")} to work on older lobbies too.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Since {

    /** The version, {@code major.minor}. */
    String value();
}
