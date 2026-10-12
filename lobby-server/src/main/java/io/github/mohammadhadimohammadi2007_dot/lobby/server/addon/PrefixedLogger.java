package io.github.mohammadhadimohammadi2007_dot.lobby.server.addon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;
import org.slf4j.spi.LoggingEventBuilder;

/**
 * An addon's logger: every line starts with {@code [Addon name]}, because the console shows messages
 * without logger names. The log file also has the logger name ({@code addon.<id>}).
 */
final class PrefixedLogger extends AbstractLogger {

    private final transient Logger delegate;
    private final String prefix;

    PrefixedLogger(String id, String displayName) {
        this.delegate = LoggerFactory.getLogger("addon." + id);
        this.prefix = "[" + displayName + "] ";
        this.name = delegate.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return delegate.isTraceEnabled(marker);
    }

    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return delegate.isDebugEnabled(marker);
    }

    @Override
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return delegate.isInfoEnabled(marker);
    }

    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return delegate.isWarnEnabled(marker);
    }

    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return delegate.isErrorEnabled(marker);
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return PrefixedLogger.class.getName();
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments,
                                               Throwable throwable) {
        LoggingEventBuilder event = delegate.atLevel(level).setMessage(prefix + messagePattern);
        if (marker != null) {
            event = event.addMarker(marker);
        }
        if (arguments != null) {
            for (Object argument : arguments) {
                event = event.addArgument(argument);
            }
        }
        if (throwable != null) {
            event = event.setCause(throwable);
        }
        event.log();
    }
}
