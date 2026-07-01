package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 文档提取工具 — 从 PDF、DOCX、PPTX、XLSX 等文档中提取文本内容。
 * 对应 OpenClaw 的 document-extract extension。
 */
public class DocumentExtractTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(DocumentExtractTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_TEXT_LENGTH = 100000;

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "filePath", Map.of("type", "string", "description", "Path to the document file"),
            "format", Map.of("type", "string", "enum", "text,markdown,structured",
                "description", "Output format"),
            "maxPages", Map.of("type", "number", "description", "Max pages to extract (PDF)")
        ));
        return new ToolDescriptor(
            "document_extract",
            "Document Extract",
            "Extract text content from PDF, DOCX, PPTX, XLSX, TXT, CSV, HTML documents.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var filePath = args.path("filePath").asText("");

            if (filePath.isEmpty()) {
                return ToolResult.failure(input.toolCallId(), "filePath is required");
            }

            var path = Paths.get(filePath);
            if (!Files.exists(path)) {
                return ToolResult.failure(input.toolCallId(), "File not found: " + filePath);
            }

            var fileName = path.getFileName().toString();
            var ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

            return switch (ext) {
                case "txt", "md", "csv", "tsv", "log", "json", "xml", "yaml", "yml", "html", "htm" ->
                    extractText(path, input);
                case "pdf" -> extractPdf(path, args, input);
                case "docx" -> extractDocx(path, input);
                case "xlsx" -> extractXlsx(path, input);
                case "pptx" -> extractPptx(path, input);
                default -> extractText(path, input);
            };
        });
    }

    private ToolResult extractText(Path path, ToolInput input) throws Exception {
        var content = Files.readString(path);
        if (content.length() > MAX_TEXT_LENGTH) {
            content = content.substring(0, MAX_TEXT_LENGTH) + "\n\n... [truncated at " + MAX_TEXT_LENGTH + " chars]";
        }
        return ToolResult.success(input.toolCallId(), content);
    }

    private ToolResult extractPdf(Path path, com.fasterxml.jackson.databind.JsonNode args, ToolInput input) {
        try {
            // 使用基本二进制流检查 — 完整 PDF 解析需要 Apache PDFBox 依赖
            var bytes = Files.readAllBytes(path);
            var header = new String(bytes, 0, Math.min(5, bytes.length));

            if (!header.startsWith("%PDF")) {
                return ToolResult.failure(input.toolCallId(), "Not a valid PDF file");
            }

            // 尝试提取文本流（简化版 — 仅提取可读文本片段）
            var text = extractPdfTextSimple(bytes);

            return ToolResult.success(input.toolCallId(),
                "📄 PDF Document: " + path.getFileName() + "\n" +
                "Size: " + bytes.length / 1024 + "KB\n\n" +
                (text.isEmpty()
                    ? "⚠️ PDF text extraction requires Apache PDFBox dependency. Add it to pom.xml for full extraction.\n" +
                      "Basic info: " + bytes.length + " bytes, appears to be a valid PDF."
                    : text));
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "PDF extraction failed: " + e.getMessage());
        }
    }

    private String extractPdfTextSimple(byte[] bytes) {
        // 简化版：提取 BT...ET 文本块中的括号内文字
        var sb = new StringBuilder();
        var content = new String(bytes);
        var inText = false;
        int i = 0;
        while (i < content.length() && sb.length() < MAX_TEXT_LENGTH) {
            // 查找 (text) 模式
            if (content.charAt(i) == '(') {
                int end = content.indexOf(')', i);
                if (end > i && end - i < 200) {
                    var candidate = content.substring(i + 1, end);
                    // 过滤：只保留包含可读字符的文本
                    if (candidate.matches(".*[a-zA-Z\\u4e00-\\u9fa5].*") && !candidate.contains("\\")) {
                        sb.append(candidate).append(" ");
                    }
                }
            }
            i++;
        }
        var result = sb.toString().trim();
        return result.length() > 100 ? result : ""; // 至少100字符才算有意义
    }

    private ToolResult extractDocx(Path path, ToolInput input) {
        try {
            // DOCX 是 ZIP 包，尝试从 word/document.xml 提取文本
            var bytes = Files.readAllBytes(path);
            var zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));
            java.util.zip.ZipEntry entry;
            var sb = new StringBuilder();

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("word/document.xml")) {
                    var xml = new String(zis.readAllBytes());
                    // 提取 <w:t> 标签中的文本
                    var pattern = java.util.regex.Pattern.compile("<w:t[^>]*>([^<]+)</w:t>");
                    var matcher = pattern.matcher(xml);
                    while (matcher.find()) {
                        sb.append(matcher.group(1));
                    }
                    // 段落分隔
                    var paraPattern = java.util.regex.Pattern.compile("</w:p>");
                    var content = paraPattern.matcher(sb).replaceAll("\n");
                    sb = new StringBuilder(content);
                }
                zis.closeEntry();
            }
            zis.close();

            var text = sb.toString().trim();
            if (text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH) + "\n\n... [truncated]";
            }
            return ToolResult.success(input.toolCallId(),
                "📝 DOCX: " + path.getFileName() + "\n\n" + text);
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "DOCX extraction failed: " + e.getMessage());
        }
    }

    private ToolResult extractXlsx(Path path, ToolInput input) {
        try {
            var bytes = Files.readAllBytes(path);
            var zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));
            java.util.zip.ZipEntry entry;
            var sb = new StringBuilder();

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("xl/worksheets/sheet") && entry.getName().endsWith(".xml")) {
                    var xml = new String(zis.readAllBytes());
                    var pattern = java.util.regex.Pattern.compile("<t[^>]*>([^<]+)</t>");
                    var matcher = pattern.matcher(xml);
                    while (matcher.find()) {
                        sb.append(matcher.group(1)).append("\t");
                    }
                    sb.append("\n");
                }
                zis.closeEntry();
            }
            zis.close();

            return ToolResult.success(input.toolCallId(),
                "📊 XLSX: " + path.getFileName() + "\n\n" + sb.toString().trim());
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "XLSX extraction failed: " + e.getMessage());
        }
    }

    private ToolResult extractPptx(Path path, ToolInput input) {
        try {
            var bytes = Files.readAllBytes(path);
            var zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes));
            java.util.zip.ZipEntry entry;
            var sb = new StringBuilder();

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("ppt/slides/slide") && entry.getName().endsWith(".xml")) {
                    var slideName = entry.getName().replaceAll("[^0-9]", "");
                    sb.append("--- Slide ").append(slideName).append(" ---\n");
                    var xml = new String(zis.readAllBytes());
                    var pattern = java.util.regex.Pattern.compile("<a:t>([^<]+)</a:t>");
                    var matcher = pattern.matcher(xml);
                    while (matcher.find()) {
                        sb.append(matcher.group(1)).append(" ");
                    }
                    sb.append("\n\n");
                }
                zis.closeEntry();
            }
            zis.close();

            return ToolResult.success(input.toolCallId(),
                "📽 PPTX: " + path.getFileName() + "\n\n" + sb.toString().trim());
        } catch (Exception e) {
            return ToolResult.failure(input.toolCallId(), "PPTX extraction failed: " + e.getMessage());
        }
    }
}
