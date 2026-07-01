package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.mail.search.SearchTerm;
import java.util.*;

/**
 * 邮件工具 — 发送和读取邮件（SMTP + IMAP）。
 * 对应 OpenClaw 的 email plugin。
 * 支持：发送邮件（SMTP）、读取收件箱（IMAP）、搜索邮件。
 */
public class EmailTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(EmailTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // SMTP 配置
    private final String smtpHost;
    private final int smtpPort;
    private final String emailUser;
    private final String emailPass;

    // IMAP 配置
    private final String imapHost;
    private final int imapPort;

    public EmailTool() {
        this(null, 0, null, null, null, 0);
    }

    public EmailTool(String smtpHost, int smtpPort, String user, String pass, String imapHost, int imapPort) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.emailUser = user;
        this.emailPass = pass;
        this.imapHost = imapHost;
        this.imapPort = imapPort;
    }

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "action", Map.of("type", "string", "enum", "send,read,search,count",
                "description", "Email action"),
            "to", Map.of("type", "string", "description", "Recipient address (for send)"),
            "subject", Map.of("type", "string", "description", "Email subject (for send)"),
            "body", Map.of("type", "string", "description", "Email body (for send)"),
            "cc", Map.of("type", "string", "description", "CC recipients (comma-separated)"),
            "folder", Map.of("type", "string", "description", "Mail folder (default INBOX)"),
            "limit", Map.of("type", "number", "description", "Max emails to read (default 10)"),
            "query", Map.of("type", "string", "description", "Search query (for search)")
        ));
        return new ToolDescriptor(
            "email",
            "Email",
            "Send and read emails via SMTP/IMAP. Actions: send, read, search, count.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var action = args.path("action").asText("read");

            if (smtpHost == null && imapHost == null) {
                return ToolResult.failure(input.toolCallId(),
                    "Email not configured. Set SMTP/IMAP host, port, and credentials in settings.");
            }

            return switch (action) {
                case "send" -> sendEmail(args, input);
                case "read" -> readEmail(args, input);
                case "search" -> searchEmail(args, input);
                case "count" -> countEmail(args, input);
                default -> ToolResult.failure(input.toolCallId(), "Unknown action: " + action);
            };
        });
    }

    private Properties buildSmtpProps() {
        var props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        return props;
    }

    private Properties buildImapProps() {
        var props = new Properties();
        props.put("mail.imap.host", imapHost);
        props.put("mail.imap.port", String.valueOf(imapPort));
        props.put("mail.imap.ssl.enable", "true");
        return props;
    }

    private ToolResult sendEmail(com.fasterxml.jackson.databind.JsonNode args, ToolInput input) {
        try {
            var to = args.path("to").asText("");
            var subject = args.path("subject").asText("(No Subject)");
            var body = args.path("body").asText("");
            var cc = args.path("cc").asText("");

            if (to.isEmpty()) {
                return ToolResult.failure(input.toolCallId(), "Recipient (to) is required");
            }

            var session = Session.getInstance(buildSmtpProps(), new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(emailUser, emailPass);
                }
            });

            var msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(emailUser));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            if (!cc.isEmpty()) {
                msg.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
            }
            msg.setSubject(subject);
            msg.setText(body);
            msg.setSentDate(new Date());

            Transport.send(msg);

            return ToolResult.success(input.toolCallId(),
                "✅ 邮件已发送\nTo: " + to + "\nSubject: " + subject);
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "Send failed: " + e.getMessage());
        }
    }

    private ToolResult readEmail(com.fasterxml.jackson.databind.JsonNode args, ToolInput input) {
        try {
            var folderName = args.path("folder").asText("INBOX");
            var limit = args.path("limit").asInt(10);

            var session = Session.getInstance(buildImapProps());
            var store = session.getStore("imap");
            store.connect(imapHost, emailUser, emailPass);

            var folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);

            var messages = folder.getMessages();
            var count = Math.min(limit, messages.length);
            var sb = new StringBuilder();
            sb.append("📬 ").append(folderName).append(" (").append(messages.length).append(" total, showing ").append(count).append(")\n\n");

            for (int i = messages.length - 1; i >= Math.max(0, messages.length - count); i--) {
                var msg = messages[i];
                sb.append("--- Email ").append(messages.length - i).append(" ---\n");
                sb.append("From: ").append(msg.getFrom() != null && msg.getFrom().length > 0 ? msg.getFrom()[0] : "unknown").append("\n");
                sb.append("Subject: ").append(msg.getSubject()).append("\n");
                sb.append("Date: ").append(msg.getSentDate()).append("\n");
                // 提取正文前 200 字符
                var content = getMessageContent(msg);
                if (content.length() > 200) content = content.substring(0, 200) + "...";
                sb.append("Preview: ").append(content).append("\n\n");
            }

            folder.close(false);
            store.close();

            return ToolResult.success(input.toolCallId(), sb.toString().trim());
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "Read failed: " + e.getMessage());
        }
    }

    private ToolResult searchEmail(com.fasterxml.jackson.databind.JsonNode args, ToolInput input) {
        try {
            var query = args.path("query").asText("");
            var folderName = args.path("folder").asText("INBOX");
            var limit = args.path("limit").asInt(10);

            if (query.isEmpty()) {
                return ToolResult.failure(input.toolCallId(), "Search query is required");
            }

            var session = Session.getInstance(buildImapProps());
            var store = session.getStore("imap");
            store.connect(imapHost, emailUser, emailPass);

            var folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);

            var searchTerm = new SearchTerm() {
                @Override public boolean match(Message msg) {
                    try {
                        return (msg.getSubject() != null && msg.getSubject().toLowerCase().contains(query.toLowerCase()))
                            || (msg.getContent() != null && msg.getContent().toString().toLowerCase().contains(query.toLowerCase()));
                    } catch (Exception e) { return false; }
                }
            };

            var messages = folder.search(searchTerm);
            var count = Math.min(limit, messages.length);
            var sb = new StringBuilder();
            sb.append("🔍 Found ").append(messages.length).append(" email(s) matching '").append(query).append("'\n\n");

            for (int i = messages.length - 1; i >= Math.max(0, messages.length - count); i--) {
                var msg = messages[i];
                sb.append("From: ").append(msg.getFrom()[0]).append("\n");
                sb.append("Subject: ").append(msg.getSubject()).append("\n");
                sb.append("Date: ").append(msg.getSentDate()).append("\n\n");
            }

            folder.close(false);
            store.close();

            return ToolResult.success(input.toolCallId(), sb.toString().trim());
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "Search failed: " + e.getMessage());
        }
    }

    private ToolResult countEmail(com.fasterxml.jackson.databind.JsonNode args, ToolInput input) {
        try {
            var folderName = args.path("folder").asText("INBOX");
            var session = Session.getInstance(buildImapProps());
            var store = session.getStore("imap");
            store.connect(imapHost, emailUser, emailPass);

            var folder = store.getFolder(folderName);
            folder.open(Folder.READ_ONLY);
            var count = folder.getMessageCount();
            var unread = folder.getUnreadMessageCount();
            folder.close(false);
            store.close();

            return ToolResult.success(input.toolCallId(),
                "📬 " + folderName + ": " + count + " total (" + unread + " unread)");
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "Count failed: " + e.getMessage());
        }
    }

    private String getMessageContent(Message msg) throws Exception {
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
