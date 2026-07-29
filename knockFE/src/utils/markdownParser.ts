/**
 * 간단한 마크다운 파싱 함수
 * 마크다운 텍스트를 HTML로 변환
 */
export const parseMarkdown = (text: string): string => {
  if (!text) return '';

  // 줄 단위로 분리
  const lines = text.split('\n');
  const processedLines: string[] = [];
  let inList = false;
  let listItems: string[] = [];

  const flushList = () => {
    if (listItems.length > 0) {
      processedLines.push(`<ul>${listItems.join('')}</ul>`);
      listItems = [];
      inList = false;
    }
  };

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();
    
    // 빈 줄 처리
    if (!trimmed) {
      flushList();
      if (processedLines.length > 0 && !processedLines[processedLines.length - 1].endsWith('<br>')) {
        processedLines.push('<br>');
      }
      continue;
    }

    // 헤더 처리 (### ## #)
    if (trimmed.startsWith('### ')) {
      flushList();
      processedLines.push(`<h3>${processInlineMarkdown(trimmed.substring(4))}</h3>`);
      continue;
    }
    if (trimmed.startsWith('## ')) {
      flushList();
      processedLines.push(`<h2>${processInlineMarkdown(trimmed.substring(3))}</h2>`);
      continue;
    }
    if (trimmed.startsWith('# ')) {
      flushList();
      processedLines.push(`<h1>${processInlineMarkdown(trimmed.substring(2))}</h1>`);
      continue;
    }

    // 리스트 항목 처리 (- 또는 *)
    if (trimmed.match(/^[\-\*] /)) {
      if (!inList) {
        inList = true;
      }
      const content = trimmed.substring(2);
      listItems.push(`<li>${processInlineMarkdown(content)}</li>`);
      continue;
    }

    // 숫자 리스트 처리 (1. 2. 등)
    if (trimmed.match(/^\d+\. /)) {
      flushList();
      const content = trimmed.replace(/^\d+\. /, '');
      processedLines.push(`<p>${processInlineMarkdown(content)}</p>`);
      continue;
    }

    // 일반 텍스트
    flushList();
    const processed = processInlineMarkdown(trimmed);
    processedLines.push(`<p>${processed}</p>`);
  }

  flushList();

  // 결과 합치기
  let html = processedLines.join('');

  // 연속된 <br> 정리
  html = html.replace(/(<br>\s*){3,}/g, '<br><br>');
  
  // 빈 <p> 태그 제거
  html = html.replace(/<p>\s*<\/p>/g, '');

  return html;
};

/**
 * 인라인 마크다운 처리 (볼드, 이탤릭, 코드 등)
 */
const processInlineMarkdown = (text: string): string => {
  let html = text;

  // 코드 처리 먼저 (코드 안의 마크다운은 처리하지 않음)
  const codeBlocks: string[] = [];
  html = html.replace(/`([^`]+?)`/g, (_match, content) => {
    const placeholder = `__CODE_BLOCK_${codeBlocks.length}__`;
    codeBlocks.push(`<code>${content}</code>`);
    return placeholder;
  });

  // 볼드 처리 (**텍스트**) - 여러 번 반복하여 모든 볼드 처리
  // 최대 10번 반복 (무한 루프 방지)
  for (let i = 0; i < 10; i++) {
    const newHtml = html.replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>');
    if (newHtml === html) break; // 더 이상 변경사항이 없으면 종료
    html = newHtml;
  }
  
  // 볼드 처리 (__텍스트__) - 단일 언더스코어와 구분
  for (let i = 0; i < 10; i++) {
    const newHtml = html.replace(/__([^_]+?)__/g, '<strong>$1</strong>');
    if (newHtml === html) break;
    html = newHtml;
  }

  // 이탤릭 처리 (*텍스트*) - 볼드가 아닌 단일 별표만
  // 이미 <strong> 태그로 변환된 부분은 건너뛰기
  html = html.replace(/\*([^*\n]+?)\*/g, (match, content) => {
    // <strong> 태그 내부가 아니고, **로 시작하지 않는 경우만
    if (!match.includes('**') && !content.includes('<strong>')) {
      return `<em>${content}</em>`;
    }
    return match;
  });
  
  // 이탤릭 처리 (_텍스트_) - 볼드가 아닌 단일 언더스코어만
  html = html.replace(/_([^_\n]+?)_/g, (match, content) => {
    // <strong> 태그 내부가 아니고, __로 시작하지 않는 경우만
    if (!match.includes('__') && !content.includes('<strong>')) {
      return `<em>${content}</em>`;
    }
    return match;
  });

  // 코드 블록 복원
  codeBlocks.forEach((code, index) => {
    html = html.replace(`__CODE_BLOCK_${index}__`, code);
  });

  return html;
};

