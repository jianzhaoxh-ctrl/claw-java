package com.openclaw.desktop.channel.mail;

import com.openclaw.desktop.channel.*;
import com.openclaw.desktop.session.SessionKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.*;
import jakarta.mail.search.SearchTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 邮件通道 — 通过 IMAP 轮询接收邮件，SMTP 发送回复。
 * 对应 OpenClaw 的 email 通道功能。
 *
 * 与 EmailTool 的区别：这是通道级别的持续监听，而非一次性工具调用。
 */
public class MailChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(MailChannel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelId id;
    private final MailConfig mailConfig;
    private final ChannelConfig config;
    private final Sinks.Many<InboundMessage> messageSink;
    private final ScheduledExecutorService poller;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile int lastSeenCount;

    public MailChannel(MailConfig mailConfig) {
        this.id = new ChannelId("mail");
        this.mailConfig = mailConfig;
        this.config = new ChannelConfig(id, mailConfig.enabled(), Map.of());
        this.messageSink = Sinks.many().multicast().onBackpressureBuffer();
        this.poller = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "mail-poller");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public ChannelId id() { return id; }

    @Override
    public ChannelConfig config() { return config; }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("Starting Mail channel: {}", mailConfig.imapHost());
                lastSeenCount = getInboxCount();
                poller.scheduleWithFixedDelay(this::pollInbox, 0, mailConfig.pollIntervalSec(), TimeUnit.SECONDS);
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            running.set(false);
            poller.shutdown();
            log.info("Mail channel stopped");
        });
    }

    @Override
    public Flux<InboundMessage> inbound() {
        return messageSink.asFlux();
    }

    @Override
    public Mono<Void> send(OutboundMessage message) {
        return Mono.fromCallable(() -> {
            var text = switch (message.content()) {
                case OutboundMessage.OutboundContent.Text(var t) -> t;
                default -> message.content().toString();
            };
            var to = extractAddress(message);

            var props = new Properties();
            props.put("mail.smtp.host", mailConfig.smtpHost());
            props.put("mail.smtp.port", String.valueOf(mailConfig.smtpPort()));
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            var session = Session.getInstance(props, new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(mailConfig.username(), mailConfig.password());
                }
            });

            var msg = new jakarta.mail.internet.MimeMessage(session);
            msg.setFrom(new jakarta.mail.internet.InternetAddress(mailConfig.username()));
            msg.setRecipients(Message.RecipientType.TO, jakarta.mail.internet.InternetAddress.parse(to));
            msg.setSubject("Re: ClawDesktop");
            msg.setText(text);
            msg.setSentDate(new java.util.Date());
            Transport.send(msg);

            return null;
        });
    }

    @Override
    public com.openclaw.desktop.infra.health.HealthCheckResult healthCheck() {
        try {
            var count = getInboxCount();
            return com.openclaw.desktop.infra.health.HealthCheckResult.healthy();
        } catch (Exception e) {
            return com.openclaw.desktop.infra.health.HealthCheckResult.unhealthy(e.getMessage());
        }
    }

    @Override
    public boolean isRunning() { return running.get(); }

    // ---- internal ----

    private String extractAddress(OutboundMessage message) {
        var sk = message.sessionKey();
        return sk != null ? sk.toString().replace("main:mail-", "") : "";
    }

    private int getInboxCount() {
        try {
            var session = Session.getInstance(buildImapProps());
            var store = session.getStore("imap");
            store.connect(mailConfig.imapHost(), mailConfig.username(), mailConfig.password());
            var folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            var count = folder.getMessageCount();
            folder.close(false);
            store.close();
            return count;
        } catch (Exception e) {
            log.warn("Failed to get inbox count: {}", e.getMessage());
            return -1;
        }
    }

    private void pollInbox() {
        try {
            var currentCount = getInboxCount();
            if (currentCount <= lastSeenCount) return;

            var session = Session.getInstance(buildImapProps());
            var store = session.getStore("imap");
            store.connect(mailConfig.imapHost(), mailConfig.username(), mailConfig.password());
            var folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);

            var messages = folder.getMessages();
            var newCount = currentCount - lastSeenCount;
            log.debug("Mail poll: {} new messages", newCount);

            for (int i = messages.length - newCount; i < messages.length; i++) {
                if (i < 0) continue;
                var msg = messages[i];
                var from = msg.getFrom() != null && msg.getFrom().length > 0
                    ? msg.getFrom()[0].toString() : "unknown";
                var subject = msg.getSubject() != null ? msg.getSubject() : "(No Subject)";

                // 提取纯文本正文
                var content = getMessageContent(msg);
                if (content.length() > 1000) content = content.substring(0, 1000) + "...";

                var text = "📧 From: " + from + "\nSubject: " + subject + "\n\n" + content;
                var inbound = InboundMessage.text(
                    id,
                    SessionKey.main("mail-" + from),
                    text
                );
                messageSink.tryEmitNext(inbound);
                log.info("New mail from {}: {}", from, subject);
            }

            lastSeenCount = currentCount;
            folder.close(false);
            store.close();
        } catch (Exception e) {
            log.warn("Mail poll failed: {}", e.getMessage());
        }
    }

    private Properties buildImapProps() {
        var props = new Properties();
        props.put("mail.imap.host", mailConfig.imapHost());
        props.put("mail.imap.port", String.valueOf(mailConfig.imapPort()));
        props.put("mail.imap.ssl.enable", "true");
        return props;
    }

    private String getMessageContent(jakarta.mail.Message msg) throws Exception {
        var content = msg.getContent();
        if (content instanceof String s) return s;
        if (content instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                var part = mp.getBodyPart(i);
                if (part.getContentType().startsWith("text/plain")) {
                    return part.getContent().toString();
                }
            }
        }
        return "";
    }
}
