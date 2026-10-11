package io.github.mohammadhadimohammadi2007_dot.lobby.server.logging;

import ch.qos.logback.core.rolling.RollingPolicyBase;
import ch.qos.logback.core.rolling.RolloverFailure;
import ch.qos.logback.core.rolling.TriggeringPolicy;
import ch.qos.logback.core.rolling.helper.CompressionMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Logback rolling for {@code logs/latest.log} (see logback.xml): the previous run's log is archived when
 * the server starts, and the current one when the day changes, both by {@link LogFiles}.
 *
 * @param <E> the log event type
 */
public final class LatestLogPolicy<E> extends RollingPolicyBase implements TriggeringPolicy<E> {

    private volatile LocalDate day = LocalDate.now();

    @Override
    public void start() {
        Path log = Path.of(getActiveFileName());
        try {
            if (log.toFile().isFile()) {
                LogFiles.archive(log, LogFiles.lastWritten(log));
            }
        } catch (IOException e) {
            addWarn("Could not archive the previous " + log + ": " + e.getMessage());
        }
        LogFiles.deleteOld(log.toAbsolutePath().getParent(), LocalDate.now(), LogFiles.keepDays());
        day = LocalDate.now();
        super.start();
    }

    @Override
    public boolean isTriggeringEvent(File activeFile, E event) {
        return !LocalDate.now().equals(day);
    }

    @Override
    public void rollover() throws RolloverFailure {
        Path log = Path.of(getActiveFileName());
        LocalDate finished = day;
        day = LocalDate.now();
        try {
            LogFiles.archive(log, finished);
        } catch (IOException e) {
            throw new RolloverFailure("Could not archive " + log + ": " + e.getMessage());
        }
        LogFiles.deleteOld(log.toAbsolutePath().getParent(), day, LogFiles.keepDays());
    }

    @Override
    public String getActiveFileName() {
        return getParentsRawFileProperty();
    }

    @Override
    public CompressionMode getCompressionMode() {
        // Compressed by LogFiles itself.
        return CompressionMode.NONE;
    }
}
