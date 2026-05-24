package cc.theends6.sfx.internal.core;

import java.util.Objects;
import java.util.Optional;

public final class SfxResult<T> {
    private final T value;
    private final SfxErrorCode code;
    private final String message;
    private final Throwable cause;

    private SfxResult(T value, SfxErrorCode code, String message, Throwable cause) {
        this.value = value;
        this.code = Objects.requireNonNull(code, "code");
        this.message = message == null ? "" : message;
        this.cause = cause;
    }

    public static <T> SfxResult<T> ok(T value) {
        return new SfxResult<>(value, SfxErrorCode.OK, "", null);
    }

    public static SfxResult<Void> ok() {
        return new SfxResult<>(null, SfxErrorCode.OK, "", null);
    }

    public static <T> SfxResult<T> fail(SfxErrorCode code, String message) {
        return new SfxResult<>(null, code == SfxErrorCode.OK ? SfxErrorCode.INTERNAL_ERROR : code, message, null);
    }

    public static <T> SfxResult<T> fail(SfxErrorCode code, String message, Throwable cause) {
        return new SfxResult<>(null, code == SfxErrorCode.OK ? SfxErrorCode.INTERNAL_ERROR : code, message, cause);
    }

    public boolean success() {
        return code == SfxErrorCode.OK;
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public T valueOrNull() {
        return value;
    }

    public SfxErrorCode code() {
        return code;
    }

    public String message() {
        return message;
    }

    public Optional<Throwable> cause() {
        return Optional.ofNullable(cause);
    }
}
