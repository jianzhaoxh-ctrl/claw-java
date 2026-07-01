---
name: file-operations
description: 文件操作技能，提供读写、列出、删除文件的能力
triggers:
  - "读取文件"
  - "写入文件"
  - "列出文件"
  - "删除文件"
  - "read file"
  - "write file"
  - "list files"
  - "delete file"
allowed-tools:
  - read_file
  - write_file
  - list_files
  - delete_file
enabled: true
---

# 文件操作技能

当用户请求文件操作时，按以下流程执行：

## 操作流程

1. **读取文件**：使用 `read_file` 工具读取指定路径的文件内容，支持 offset/limit 分页
2. **写入文件**：使用 `write_file` 工具将内容写入指定路径（覆盖写）
3. **列出目录**：使用 `list_files` 工具列出指定目录下的文件和子目录
4. **删除文件**：使用 `delete_file` 工具删除文件（执行前需向用户确认）

## 注意事项

- 操作前先用 `list_files` 确认路径是否存在
- 读取大文件时使用 limit 参数避免一次性加载过多内容
- 写入前检查父目录是否存在，必要时先创建
- 删除操作不可逆，务必向用户二次确认
- 操作完成后，简要报告结果（文件大小、行数等）
