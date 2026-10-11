import {
  Chip, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import type { BotActionLogResponse, BotActionType } from '../../../../api/types/session.ts';

interface SessionLogViewerProps {
  logs: BotActionLogResponse[];
}

const actionColors: Record<BotActionType, 'default' | 'primary' | 'secondary' | 'success' | 'info' | 'warning'> = {
  NAVIGATE: 'primary',
  CLICK: 'secondary',
  TYPE: 'secondary',
  SCROLL: 'default',
  WAIT: 'default',
  EXTRACT: 'success',
  LOGIN: 'warning',
  FINISH: 'info'
};

// the backend joins the target of an action and the reasoning with a long dash, or sends the reasoning only
const DETAILS_SEPARATOR = ' — ';

const splitDetails = (details: string | null) => {
  const text = details ?? '';
  const index = text.indexOf(DETAILS_SEPARATOR);

  return index < 0
    ? { target: null, reasoning: text }
    : { target: text.slice(0, index), reasoning: text.slice(index + DETAILS_SEPARATOR.length) };
};

/**
 * TODO(student): Render the agentic-loop trace: one row per action with its
 * type, details, success indicator and timestamp — the live view of what
 * your bot is doing during a session.
 */
const SessionLogViewer = ({ logs }: SessionLogViewerProps) => {
  if (logs.length === 0) {
    return (
      <Typography color='text.secondary'>
        The bot has not done anything in this session yet.
      </Typography>
    );
  }

  return (
    <TableContainer component={Paper}>
      <Table size='small'>
        <TableHead>
          <TableRow>
            <TableCell>#</TableCell>
            <TableCell>Time</TableCell>
            <TableCell>Action</TableCell>
            <TableCell>Details</TableCell>
            <TableCell align='center'>Done</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {logs.map((log, index) => {
            const { target, reasoning } = splitDetails(log.details);

            return (
              <TableRow key={log.id}>
                <TableCell>{index + 1}</TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(log.occurredAt).toLocaleTimeString()}</TableCell>
                <TableCell>
                  <Chip label={log.actionType} size='small' variant='outlined' color={actionColors[log.actionType]}/>
                </TableCell>
                <TableCell sx={{ wordBreak: 'break-word' }}>
                  {target && (
                    <Typography variant='body2' color='text.secondary'>{target}</Typography>
                  )}
                  <Typography variant='body2'>{reasoning}</Typography>
                </TableCell>
                <TableCell align='center'>
                  {log.successful
                    ? <CheckCircleIcon color='success' fontSize='small'/>
                    : <ErrorIcon color='error' fontSize='small'/>}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default SessionLogViewer;
