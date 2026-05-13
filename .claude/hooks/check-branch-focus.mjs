#!/usr/bin/env node
import { execSync } from 'node:child_process';

try {
    const branch = execSync('git rev-parse --abbrev-ref HEAD', {
        encoding: 'utf8',
        stdio: ['pipe', 'pipe', 'pipe'],
    }).trim();

    const match = branch.match(/^(feat|fix|refactor|bugfix)\/(.+)$/);
    if (!match) process.exit(0);

    const prefix = match[1];
    const featureName = match[2];

    process.stdout.write(
        `[BRANCH-FOCUS] 현재 브랜치: ${branch} | 허용 범위: ${featureName}\n` +
        `이 브랜치(${prefix}/${featureName})에서는 '${featureName}' 와 관련된 작업만 허용됩니다. ` +
        `사용자가 다른 기능 개발을 요청하면 반드시 경고하고, 작업을 진행하지 말고, ` +
        `새 브랜치(예: git checkout -b feat/<새-기능-이름>) 생성을 안내하세요.\n`
    );
} catch {
    process.exit(0);
}