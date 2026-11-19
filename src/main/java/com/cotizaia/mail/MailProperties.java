package com.cotizaia.mail;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mail settings (spec 16). Everything is optional and every default keeps mail OFF: a deployment
 * that sets nothing gets {@link NoopEmailGateway} and unchanged behaviour. Credentials come only
 * from the environment ({@code MAIL_USERNAME}, {@code MAIL_PASSWORD}); nothing is hardcoded here.
 */
@ConfigurationProperties("mail")
public class MailProperties {

    /** Master switch: false means the no-op adapter is the bean and no SMTP connection is opened. */
    private boolean enabled = false;

    private String host = "localhost";

    private int port = 587;

    private String username = "";

    private String password = "";

    private String from = "no-reply@cotizaia.local";

    private boolean starttls = true;

    /** When true (and mail is enabled) an unverified account cannot log in. */
    private boolean verificationRequired = false;

    /** When true a sent document is also mailed to the client's address on file. */
    private boolean sendToClient = false;

    /** Base of the verification link, e.g. https://app.example.com (no trailing slash needed). */
    private String publicUrl = "http://localhost:5173";

    /** Socket timeouts, kept short so a dead SMTP server can never stall a request. */
    private Duration timeout = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public boolean isStarttls() {
        return starttls;
    }

    public void setStarttls(boolean starttls) {
        this.starttls = starttls;
    }

    public boolean isVerificationRequired() {
        return verificationRequired;
    }

    public void setVerificationRequired(boolean verificationRequired) {
        this.verificationRequired = verificationRequired;
    }

    public boolean isSendToClient() {
        return sendToClient;
    }

    public void setSendToClient(boolean sendToClient) {
        this.sendToClient = sendToClient;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public void setPublicUrl(String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    /** Verification is only enforced when a mail adapter exists to complete it. */
    public boolean isLoginVerificationRequired() {
        return enabled && verificationRequired;
    }
}
