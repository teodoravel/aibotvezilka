import { isAxiosError } from 'axios';

/**
 * The backend answers a failed request with { status, message, timestamp }.
 * That message says what went wrong, unlike the generic "Request failed with status code 409".
 */
const errorMessage = (err: unknown, fallback: string): string => {
  if (isAxiosError(err) && typeof err.response?.data?.message === 'string') {
    return err.response.data.message;
  }
  return err instanceof Error ? err.message : fallback;
};

export default errorMessage;
