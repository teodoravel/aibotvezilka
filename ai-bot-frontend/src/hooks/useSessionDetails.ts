import { useCallback, useEffect, useState } from 'react';
import errorMessage from '../api/errorMessage.ts';
import sessionApi from '../api/sessionApi.ts';
import type { BotActionLogResponse, SessionResponse } from '../api/types/session.ts';
import useSnackbar from './useSnackbar.ts';

const POLL_INTERVAL_MS = 3000;

/**
 * TODO(student): Load one session (sessionApi.findById) together with its
 * action logs (sessionApi.findLogs) for the SessionDetailsPage, including
 * loading/error state. Consider polling the logs while the session is RUNNING
 * so the trace updates live.
 */
const useSessionDetails = (id: string) => {
  const { showSnackbar } = useSnackbar();

  const [session, setSession] = useState<SessionResponse | null>(null);
  const [logs, setLogs] = useState<BotActionLogResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const fetch = useCallback(async () => {
    try {
      const [sessionResponse, logsResponse] = await Promise.all([
        sessionApi.findById(id),
        sessionApi.findLogs(id)
      ]);
      setSession(sessionResponse.data);
      setLogs(logsResponse.data);
    } catch (err) {
      showSnackbar(errorMessage(err, 'Failed to load session.'), 'error');
    } finally {
      setLoading(false);
    }
  }, [id, showSnackbar]);

  useEffect(() => {
    void fetch();
  }, [fetch]);

  const running = session?.status === 'RUNNING';

  useEffect(() => {
    if (!running) {
      return;
    }
    const interval = setInterval(() => void fetch(), POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [running, fetch]);

  return { session, logs, loading };
};

export default useSessionDetails;
