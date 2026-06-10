import { describe, it, expect } from 'vitest';
import { viShared, interpolate, createT } from './vi';

describe('interpolate', () => {
  it('replaces single param', () => {
    expect(interpolate('Xin chào, {name}', { name: 'An' })).toBe('Xin chào, An');
  });

  it('replaces multiple and numeric params', () => {
    expect(interpolate('{n} căn hộ tại {block}', { n: 5, block: 'A' })).toBe('5 căn hộ tại A');
  });

  it('leaves unknown placeholders untouched', () => {
    expect(interpolate('Xin chào, {name}', {})).toBe('Xin chào, {name}');
  });

  it('returns template unchanged without params', () => {
    expect(interpolate('Đang tải...')).toBe('Đang tải...');
  });
});

describe('createT', () => {
  const appDict = {
    nav: { home: 'Trang chủ' },
    layout: { hello: 'Xin chào, {name}' },
    common: { cancel: 'APP-OVERRIDE' },
  };
  const t = createT(appDict, viShared);

  it('resolves dot-path keys', () => {
    expect(t('nav.home')).toBe('Trang chủ');
  });

  it('resolves shared keys via fallback dict', () => {
    expect(t('common.loading')).toBe('Đang tải...');
  });

  it('app dict shadows shared dict (search order)', () => {
    expect(t('common.cancel')).toBe('APP-OVERRIDE');
  });

  it('interpolates params', () => {
    expect(t('layout.hello', { name: 'Minh' })).toBe('Xin chào, Minh');
  });

  it('falls back to the key itself when missing', () => {
    expect(t('nav.missing')).toBe('nav.missing');
  });

  it('supports the nothing-exists-yet empty-state pattern', () => {
    expect(t('common.emptyYet', { item: 'phản ánh' })).toBe('Chưa có phản ánh nào');
  });

  it('supports the no-search-results empty-state pattern', () => {
    expect(t('common.emptyFound', { item: 'căn hộ' })).toBe('Không tìm thấy căn hộ');
  });
});
