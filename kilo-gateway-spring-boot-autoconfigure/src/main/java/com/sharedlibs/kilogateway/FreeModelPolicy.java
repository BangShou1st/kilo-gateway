package com.sharedlibs.kilogateway;

/** Restricts anonymous/free-only configurations to known Kilo free-model ID shapes. */
public final class FreeModelPolicy {
    private FreeModelPolicy() { }

    public static boolean isFreeModelId(String model) {
        return model != null && !model.isBlank()
                && (model.endsWith(":free") || model.equals("kilo-auto/free"));
    }
}
