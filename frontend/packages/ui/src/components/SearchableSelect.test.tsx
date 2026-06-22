import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchableSelect } from './SearchableSelect';
import type { SearchableOption } from './SearchableSelect';

const staticOptions: SearchableOption[] = [
  { value: '1', label: 'Alice Nguyen' },
  { value: '2', label: 'Bob Tran' },
  { value: '3', label: 'Charlie Le' },
];

describe('SearchableSelect — static mode (regression)', () => {
  it('filters options client-side on input', async () => {
    render(
      <SearchableSelect
        options={staticOptions}
        value=""
        onChange={() => {}}
      />
    );
    fireEvent.click(screen.getByRole('button'));
    const input = screen.getByPlaceholderText('Tìm kiếm...');
    await act(async () => { fireEvent.change(input, { target: { value: 'ali' } }); });
    expect(screen.getByText('Alice Nguyen')).toBeDefined();
    expect(screen.queryByText('Bob Tran')).toBeNull();
    expect(screen.queryByText('Charlie Le')).toBeNull();
  });

  it('shows all options when query is empty', () => {
    render(
      <SearchableSelect
        options={staticOptions}
        value=""
        onChange={() => {}}
      />
    );
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Alice Nguyen')).toBeDefined();
    expect(screen.getByText('Bob Tran')).toBeDefined();
    expect(screen.getByText('Charlie Le')).toBeDefined();
  });

  it('shows no-results message when nothing matches', async () => {
    render(
      <SearchableSelect
        options={staticOptions}
        value=""
        onChange={() => {}}
      />
    );
    fireEvent.click(screen.getByRole('button'));
    const input = screen.getByPlaceholderText('Tìm kiếm...');
    await act(async () => { fireEvent.change(input, { target: { value: 'zzz' } }); });
    expect(screen.getByText('Không tìm thấy kết quả')).toBeDefined();
  });

  it('calls onChange and closes on selection', () => {
    const onChange = vi.fn();
    render(
      <SearchableSelect
        options={staticOptions}
        value=""
        onChange={onChange}
      />
    );
    fireEvent.click(screen.getByRole('button'));
    fireEvent.mouseDown(screen.getByText('Bob Tran'));
    expect(onChange).toHaveBeenCalledWith('2');
    expect(screen.queryByPlaceholderText('Tìm kiếm...')).toBeNull();
  });
});

describe('SearchableSelect — async mode', () => {
  const asyncResults: SearchableOption[] = [
    { value: 'a1', label: 'Duc Nguyen — duc@example.com' },
    { value: 'a2', label: 'Em Pham — em@example.com' },
  ];

  let loadOptions: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    loadOptions = vi.fn().mockResolvedValue(asyncResults);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('calls loadOptions with empty query on open (no debounce)', async () => {
    render(<SearchableSelect value="" onChange={() => {}} loadOptions={loadOptions} />);
    fireEvent.click(screen.getByRole('button'));
    // delay=0 for empty query → fires immediately via setTimeout(0)
    await act(async () => { vi.runAllTimers(); });
    await act(async () => {}); // flush promise
    expect(loadOptions).toHaveBeenCalledWith('');
  });

  it('debounces loadOptions call when typing (300ms)', async () => {
    render(<SearchableSelect value="" onChange={() => {}} loadOptions={loadOptions} />);
    fireEvent.click(screen.getByRole('button'));
    await act(async () => { vi.runAllTimers(); });
    await act(async () => {});
    loadOptions.mockClear();

    const input = screen.getByPlaceholderText('Tìm kiếm...');
    fireEvent.change(input, { target: { value: 'd' } });
    // not yet called
    expect(loadOptions).not.toHaveBeenCalled();
    // advance past debounce
    await act(async () => { vi.advanceTimersByTime(300); });
    await act(async () => {});
    expect(loadOptions).toHaveBeenCalledWith('d');
  });

  it('renders results returned by loadOptions', async () => {
    render(<SearchableSelect value="" onChange={() => {}} loadOptions={loadOptions} />);
    fireEvent.click(screen.getByRole('button'));
    await act(async () => { vi.runAllTimers(); });
    await act(async () => {});
    expect(screen.getByText('Duc Nguyen — duc@example.com')).toBeDefined();
    expect(screen.getByText('Em Pham — em@example.com')).toBeDefined();
  });

  it('persists selected label when not in current search results', async () => {
    const onChange = vi.fn();
    const { rerender } = render(
      <SearchableSelect value="" onChange={onChange} loadOptions={loadOptions} />
    );

    // Open, load initial results, select first option
    fireEvent.click(screen.getByRole('button'));
    await act(async () => { vi.runAllTimers(); });
    await act(async () => {});
    fireEvent.mouseDown(screen.getByText('Duc Nguyen — duc@example.com'));
    expect(onChange).toHaveBeenCalledWith('a1');

    // Simulate parent updating value prop; loadOptions now returns different results
    loadOptions.mockResolvedValue([{ value: 'b1', label: 'Completely Different' }]);
    rerender(
      <SearchableSelect value="a1" onChange={onChange} loadOptions={loadOptions} />
    );

    // Trigger button must still show the previously selected label
    const button = screen.getByRole('button');
    expect(button.textContent).toContain('Duc Nguyen — duc@example.com');
  });
});
