import { Box, Button, Chip, CircularProgress, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useNavigate, useParams } from 'react-router';
import useSessionDetails from '../../../../hooks/useSessionDetails.ts';
import SessionLogViewer from '../../../components/session/SessionLogViewer/SessionLogViewer.tsx';
import SessionStatusChip from '../../../components/session/SessionStatusChip/SessionStatusChip.tsx';

const SessionDetailsPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { session, logs, loading } = useSessionDetails(id!);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
        <CircularProgress/>
      </Box>
    );
  }

  return (
    <Box>
      <Button startIcon={<ArrowBackIcon/>} sx={{ mb: 1 }} onClick={() => navigate('/sessions')}>
        All sessions
      </Button>
      {!session && (
        <Typography color='text.secondary'>Session #{id} could not be loaded.</Typography>
      )}
      {session &&
       <>
         <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 1 }}>
           <Typography variant='h5'>Session #{id} - {session.socialNetwork}</Typography>
           <SessionStatusChip status={session.status}/>
         </Box>
         {session.description && (
           <Typography sx={{ mb: 1 }}>{session.description}</Typography>
         )}
         <Typography variant='body2' color='text.secondary'>
           Started: {session.startedAt ? new Date(session.startedAt).toLocaleString() : 'not yet'}
         </Typography>
         <Typography variant='body2' color='text.secondary'>
           Finished: {session.finishedAt ? new Date(session.finishedAt).toLocaleString() : 'not yet'}
         </Typography>
         <Typography variant='subtitle1' sx={{ mt: 2 }}>Targets</Typography>
         <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
           {session.targets.map((target) => (
             <Chip key={target.id} variant='outlined' label={`${target.type}: ${target.value}`}/>
           ))}
         </Box>
         <Typography variant='subtitle1' gutterBottom>What the bot did ({logs.length} actions)</Typography>
         <SessionLogViewer logs={logs}/>
       </>}
    </Box>
  );
};

export default SessionDetailsPage;
