import { Chip } from '@mui/material';
import type { SessionStatus } from '../../../../api/types/session.ts';

interface SessionStatusChipProps {
  status: SessionStatus;
}

const statusColors: Record<SessionStatus, 'default' | 'info' | 'warning' | 'success' | 'error'> = {
  CREATED: 'default',
  RUNNING: 'info',
  PAUSED: 'warning',
  COMPLETED: 'success',
  FAILED: 'error'
};

const SessionStatusChip = ({ status }: SessionStatusChipProps) => {
  return <Chip label={status} size='small' color={statusColors[status]} sx={{ alignSelf: 'flex-start' }}/>;
};

export default SessionStatusChip;
