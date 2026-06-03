package com.enterprise.auth.platform.modules.system.application;

public enum MailChannelPreset {
    QQ("smtp.qq.com", 587, "smtp", false, true),
    NETEASE("smtp.163.com", 465, "smtp", true, false),
    GMAIL("smtp.gmail.com", 587, "smtp", false, true),
    OUTLOOK("smtp-mail.outlook.com", 587, "smtp", false, true),
    CUSTOM("", 587, "smtp", false, true);

    private final String host;
    private final int port;
    private final String protocol;
    private final boolean useSsl;
    private final boolean useStartTls;

    MailChannelPreset(String host, int port, String protocol, boolean useSsl, boolean useStartTls) {
        this.host = host;
        this.port = port;
        this.protocol = protocol;
        this.useSsl = useSsl;
        this.useStartTls = useStartTls;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String protocol() {
        return protocol;
    }

    public boolean useSsl() {
        return useSsl;
    }

    public boolean useStartTls() {
        return useStartTls;
    }
}