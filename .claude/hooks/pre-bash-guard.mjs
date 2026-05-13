#!/usr/bin/env node
import { readFileSync } from 'node:fs';

const input = JSON.parse(readFileSync(0, 'utf8'));
const command = input?.tool_input?.command ?? '';

const dangerPatterns = [
  { pattern: /git push/, label: 'git push — 명시적 지시 없이 push 금지 (AI Constraints)' },
  { pattern: /git push.*--force/, label: 'git push --force — 원격 히스토리 파괴 위험' },
  { pattern: /DROP\s+TABLE/i, label: 'DROP TABLE — DB 데이터 영구 삭제 위험' },
  { pattern: /rm\s+-rf/, label: 'rm -rf — 파일 영구 삭제 위험' },
  { pattern: /git\s+reset\s+--hard/, label: 'git reset --hard — 작업 내역 영구 삭제 위험' },
  { pattern: /git\s+checkout\s+main/, label: 'main 브랜치 직접 전환 — branch-focus 규칙 확인 필요' },
];

const matched = dangerPatterns.filter(({ pattern }) => pattern.test(command));

if (matched.length > 0) {
  process.stdout.write(
    '[PRE-BASH-GUARD] 위험 명령어 감지:\n' +
    matched.map(({ label }) => `  - ${label}`).join('\n') + '\n' +
    '사용자에게 확인을 받은 후 진행하세요.\n'
  );
}
