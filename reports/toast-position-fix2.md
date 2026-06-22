# Toast Position Fix 2 — Root Cause & Fix
**Date:** 2026-06-09

## Confirmed Root Cause

Resident `Layout.tsx:31`: `<div className="flex flex-col min-h-screen bg-gray-50 max-w-md mx-auto">`
App column is `max-w-md` (448px) centered via `mx-auto` on a wide viewport.

`Toaster` is mounted in `App.tsx:61` OUTSIDE the BrowserRouter/Layout, with `position: fixed`.  
Prior Toast container: `fixed top-4 left-4 right-4 md:left-auto md:right-4 md:max-w-sm`  
On md+ (≥ 768px): `right: 1rem; left: auto; max-width: 24rem` — anchored to VIEWPORT right edge.  
On a 1440px desktop: toast renders at `1440 - 16 = 1424px` from left. App column right edge: ~944px. Toast is ~480px outside the app frame.

Styling was correct (Tailwind purge was fixed in c518623). The issue was ONLY the anchor point.

## Fix Applied

`packages/ui/src/components/Toast.tsx` container:

```tsx
// Before (viewport right-edge anchor on md+):
<div className="fixed top-4 left-4 right-4 md:left-auto md:right-4 md:max-w-sm z-[200] flex flex-col gap-2 pointer-events-none">

// After (viewport-centered, always over app column):
<div className="fixed top-4 left-1/2 -translate-x-1/2 w-[calc(100%-2rem)] max-w-sm z-[200] flex flex-col gap-2 pointer-events-none">
```

- `left-1/2 -translate-x-1/2`: centers horizontally in viewport at all widths
- `w-[calc(100%-2rem)]`: 16px margin from each viewport edge on narrow screens
- `max-w-sm`: capped at 384px on wide screens

**Resident (448px column, desktop):** viewport-centered toast = centered over the app column ✓  
**Resident (390px mobile):** 390-32=358px wide toast, 16px margins, fully in-frame ✓  
**Admin (full-width desktop):** toast centers in viewport = center of admin — acceptable ✓

## Build Evidence
- admin: 18.32kB CSS — exit 0
- resident: 17.43kB CSS — exit 0

## Remaining Action
Rebuild nginx to deploy.
