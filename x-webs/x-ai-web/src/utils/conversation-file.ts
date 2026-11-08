import type { ConversationFile } from '@/types/chat';

export function isImageFile(file: ConversationFile): boolean {
  return /^image\//i.test(file.contentType)
    || /\.(png|jpe?g|gif|webp|svg|avif|bmp|ico)$/i.test(file.name);
}

export function isHtmlFile(file: ConversationFile): boolean {
  return /\.html?$/i.test(file.name);
}
