package dev.vibe.account;

import java.io.IOException;

/** A definitive cookie/export rejection, distinct from transport or service failures. */
final class InvalidCookiesException extends IOException {
    InvalidCookiesException(String message) { super(message); }
}
