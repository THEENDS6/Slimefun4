package cc.theends6.sfx.api.display;


public record SfxDisplayTransform(float translationX, float translationY, float translationZ,
                                  float scaleX, float scaleY, float scaleZ,
                                  float leftX, float leftY, float leftZ, float leftW,
                                  float rightX, float rightY, float rightZ, float rightW) {
    public static final SfxDisplayTransform IDENTITY = new SfxDisplayTransform(
            0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1);

    public SfxDisplayTransform {
        float[] values = {translationX, translationY, translationZ, scaleX, scaleY, scaleZ,
                leftX, leftY, leftZ, leftW, rightX, rightY, rightZ, rightW};
        for (float value : values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Display transform must be finite");
        if (scaleX == 0 || scaleY == 0 || scaleZ == 0) throw new IllegalArgumentException("Display scale must not be zero");
    }

    public static SfxDisplayTransform scale(float scale) {
        return new SfxDisplayTransform(0, 0, 0, scale, scale, scale,
                0, 0, 0, 1, 0, 0, 0, 1);
    }

    public SfxDisplayTransform withLeftYRotation(double radians) {
        float halfSin = (float) Math.sin(radians / 2.0D);
        float halfCos = (float) Math.cos(radians / 2.0D);
        return new SfxDisplayTransform(translationX, translationY, translationZ, scaleX, scaleY, scaleZ,
                0, halfSin, 0, halfCos, rightX, rightY, rightZ, rightW);
    }
}
