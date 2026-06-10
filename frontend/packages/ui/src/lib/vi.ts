/**
 * Shared Vietnamese dictionary + t() helper for all Gemek apps.
 *
 * Architecture (DECISIONS.md 2026-06-10 i18n entry):
 * - Plain TS objects, no react-i18next.
 * - This file holds ONLY genuinely cross-app strings; app-specific strings
 *   live in each app's `src/i18n/vi.ts`.
 * - Enum display-maps are a separate concern (`src/i18n/enums.ts` per app);
 *   raw enum keys (value= attrs, API payloads) are never translated.
 */

/** Nested string dictionary; keys addressed by dot-path, e.g. `common.cancel`. */
export type TranslationDict = { [key: string]: string | TranslationDict };

/** Interpolation params: `{name}` placeholders replaced by params.name. */
export type TranslateParams = Record<string, string | number>;

/**
 * Cross-app shared strings. Keep this list strict — a string belongs here
 * only when BOTH apps render it (buttons, pagination, generic states).
 */
export const viShared: TranslationDict = {
  common: {
    cancel: 'Hủy',
    save: 'Lưu',
    edit: 'Sửa',
    loading: 'Đang tải...',
    prev: 'Trước',
    next: 'Sau',
    actions: 'Thao tác',
    status: 'Trạng thái',
    // Generic empty-state pattern: t('common.empty', { item: 'thông báo' })
    empty: 'Không có {item}',
  },
};

/** Replaces `{param}` placeholders; unknown placeholders are left as-is. */
export function interpolate(template: string, params?: TranslateParams): string {
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (match, key: string) =>
    params[key] !== undefined ? String(params[key]) : match,
  );
}

/** Walks a dot-path through a nested dict; returns undefined when absent. */
function lookup(dict: TranslationDict, key: string): string | undefined {
  let node: TranslationDict | string | undefined = dict;
  for (const part of key.split('.')) {
    if (typeof node !== 'object' || node === undefined) return undefined;
    node = node[part];
  }
  return typeof node === 'string' ? node : undefined;
}

/**
 * Builds a t() bound to one or more dictionaries.
 * Dictionaries are searched in order — pass the app dict first so it can
 * shadow shared keys. Missing keys fall back to the key itself (visible in
 * UI, greppable, never throws).
 */
export function createT(...dicts: TranslationDict[]) {
  return function t(key: string, params?: TranslateParams): string {
    for (const dict of dicts) {
      const value = lookup(dict, key);
      if (value !== undefined) return interpolate(value, params);
    }
    return key;
  };
}
