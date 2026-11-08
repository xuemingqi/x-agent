import MarkdownIt from 'markdown-it';

const markdown = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: true,
});

export function renderMarkdown(content: string): string {
  return markdown.render(content);
}
