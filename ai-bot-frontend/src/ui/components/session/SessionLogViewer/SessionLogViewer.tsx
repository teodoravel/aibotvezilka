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
          {logs.map((log, index) => (
            <TableRow key={log.id}>
              <TableCell>{index + 1}</TableCell>
              <TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(log.occurredAt).toLocaleTimeString()}</TableCell>
              <TableCell>
                <Chip label={log.actionType} size='small' variant='outlined' color={actionColors[log.actionType]}/>
              </TableCell>
              <TableCell sx={{ wordBreak: 'break-word' }}>{log.details}</TableCell>
              <TableCell align='center'>
                {log.successful
                  ? <CheckCircleIcon color='success' fontSize='small'/>
                  : <ErrorIcon color='error' fontSize='small'/>}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
};

export default SessionLogViewer;
