package io.github.mohammadhadimohammadi2007_dot.lobby.server.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * {@code %plainmsg} in logback.xml: the message without ANSI color codes. Minestom writes command replies
 * to the console with colors; log files must stay readable text.
 */
public final class PlainMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return ConsoleAppender.stripAnsi(super.convert(event));
    }
}
