package dev.vibe.launcher.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.channels.ClosedByInterruptException;

/** A failure that already knows its {@link ErrorCode}; the message is shown to the user as is. */
public class LauncherException extends IOException {
    private static final long serialVersionUID = 1L;
    public final ErrorCode code;

    public LauncherException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public LauncherException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * Gives an error without a recognisable cause the code of the step that failed,
     * while specific causes (no internet, disk full, ...) keep their own code.
     */
    public static IOException wrap(IOException error, ErrorCode fallback, String message) {
        if (error instanceof LauncherException || cancelled(error) || !ErrorCode.unknown(error)) return error;
        return new LauncherException(fallback, message + " (" + Text.describe(error) + ")", error);
    }

    /**
     * Whether an error means the user cancelled. Read timeouts are also
     * {@link InterruptedIOException}s, but they are failures, not cancellations.
     */
    public static boolean cancelled(Throwable error) {
        if (error instanceof InterruptedException || error instanceof ClosedByInterruptException) return true;
        return error instanceof InterruptedIOException && !(error instanceof SocketTimeoutException);
    }
}
