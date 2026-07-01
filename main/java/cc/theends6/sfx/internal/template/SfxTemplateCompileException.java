package cc.theends6.sfx.internal.template;

public final class SfxTemplateCompileException extends RuntimeException {
    SfxTemplateCompileException(String message) {
        super(message);
    }

    SfxTemplateCompileException(String message, Throwable cause) {
        super(message, cause);
    }
}
