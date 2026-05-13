#!/usr/bin/env node
import { readFileSync } from 'node:fs';

const input = JSON.parse(readFileSync(0, 'utf8'));
const filePath = input?.tool_input?.file_path ?? '';

if (!filePath.endsWith('.java')) process.exit(0);

let content = '';
try {
  content = readFileSync(filePath, 'utf8');
} catch {
  process.exit(0);
}

const violations = [];

if (/@Setter/.test(content) && /@Entity/.test(content)) {
  violations.push('Entity에 @Setter 사용 금지 (code-convention 위반)');
}
if (/@Data/.test(content) && /@Entity/.test(content)) {
  violations.push('Entity에 @Data 사용 금지 (순환 참조 위험)');
}
if (/javax\./.test(content)) {
  violations.push('javax.* 사용 감지 — jakarta.*로 변경 필요 (Spring Boot 4.x)');
}
if (/@Transactional/.test(content) && /Controller/.test(filePath)) {
  violations.push('Controller에 @Transactional 사용 금지');
}

if (violations.length > 0) {
  process.stdout.write(
    '[POST-WRITE-LINT] 컨벤션 위반 감지:\n' +
    violations.map(v => `  - ${v}`).join('\n') + '\n' +
    '수정 후 다시 저장하세요.\n'
  );
}
