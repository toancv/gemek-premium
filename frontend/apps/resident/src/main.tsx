import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider, MutationCache } from '@tanstack/react-query';
import { mutationCacheHandlers } from '@gemek/ui';
import App from './App';
import './index.css';

// SECURITY-FIX: Removed window.__gemekAuthState and window.__gemekSetToken globals.
// axios client now imports useAuthStore directly — no window intermediary needed.

const queryClient = new QueryClient({
  mutationCache: new MutationCache(mutationCacheHandlers),
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>
);
