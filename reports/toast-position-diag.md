# Toast Position Diagnosis (2026-06-09)

## Root cause — CSS purge
resident tailwind.config.js content: ['./index.html', './src/**/*.{ts,tsx}']
admin tailwind.config.js content: [..., '../../packages/ui/src/**/*.{ts,tsx}']  ← correct

Toast.tsx lives in packages/ui/src. Its classes (fixed, top-4, right-4, z-[200],
bg-green-600, bg-red-600, etc.) are NOT scanned by the resident Tailwind build.
All positioning + color classes purged → toast renders as unstyled white block in
normal document flow (hence "white strip at left").

## Root cause — Mobile overflow
Even after CSS fix: fixed right-4 + width:100% + max-width:24rem
On 390px screen: left edge = 390 - 16 - 384 = -10px (off-screen left).
On 320px screen: left edge = 320 - 16 - 304 = 0px (just fits, but no padding).

## Toast singleton truth
ONE instance. listeners[] is module-level in Toast.tsx.
Vite resolves @gemek/ui → same absolute path regardless of import site
(App.tsx, ProfilePage.tsx, mutationToast.ts all resolve to
packages/ui/src/components/Toast.tsx via symlink).
Component-level toast.success() WORKS — was just CSS-invisible.
Previous diagnosis ("component path unreliable") was wrong.
meta.successMessage also works (same listeners[]).

## Canonical pattern (locked)
Both paths are equivalent. Use component-level toast.success() for conditional
messages (data-dependent). Use meta.successMessage for fixed messages where
the hook can define it statically. Either is fine for remaining clusters.
DO NOT route through meta.successMessage to "avoid singleton issues" — there
are no singleton issues.
