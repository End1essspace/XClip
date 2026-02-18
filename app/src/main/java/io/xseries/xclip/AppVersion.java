package io.xseries.xclip;

public final class AppVersion {

    public static final String VERSION;

    static {
        String v = AppVersion.class.getPackage().getImplementationVersion();
        VERSION = (v != null) ? v : "DEV";
    }

    private AppVersion() {}
}
