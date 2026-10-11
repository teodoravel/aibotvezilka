import { Box, Button, Chip, Container, Paper, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import postApi from '../../../../api/postApi.ts';
import sessionApi from '../../../../api/sessionApi.ts';
import type { SessionResponse } from '../../../../api/types/session.ts';
import useAuth from '../../../../hooks/useAuth.ts';

// from this score on a post counts as Macedonian (the same limit is used for donating)
const MACEDONIAN_THRESHOLD = 0.5;

interface Dashboard {
  totalPosts: number;
  macedonianPosts: number;
  donatedPosts: number;
  latestSession: SessionResponse | null;
}

const HomePage = () => {
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();

  const [dashboard, setDashboard] = useState<Dashboard | null>(null);

  useEffect(() => {
    // the numbers come from the protected API, a visitor who is not logged in sees only the introduction
    if (!isLoggedIn) {
      return;
    }
    Promise
      .all([
        postApi.findAll({}, 0, 1),
        postApi.findAll({ minMacedonianConfidence: MACEDONIAN_THRESHOLD }, 0, 1),
        postApi.findAll({ donated: true }, 0, 1),
        sessionApi.findAll()
      ])
      .then(([allPosts, macedonianPosts, donatedPosts, sessions]) => setDashboard({
        totalPosts: allPosts.data.totalElements,
        macedonianPosts: macedonianPosts.data.totalElements,
        donatedPosts: donatedPosts.data.totalElements,
        // the backend returns the newest session first
        latestSession: sessions.data[0] ?? null
      }))
      .catch(() => setDashboard(null));
  }, [isLoggedIn]);

  const statistics = dashboard === null ? [] : [
    { label: 'Extracted posts', value: dashboard.totalPosts },
    { label: 'Macedonian posts', value: dashboard.macedonianPosts },
    { label: 'Donated posts', value: dashboard.donatedPosts }
  ];

  return (
    <Box sx={{ m: 0, p: 0 }}>
      <Container maxWidth='xl' sx={{ mt: 3, py: 3 }}>
        <Typography variant='h4' gutterBottom>
          AI Bot for doniraj.vezilka.ai
        </Typography>
        <Typography variant='body1' sx={{ mb: 4 }}>
          This bot navigates a social network, extracts Macedonian content and
          donates it to the Vezilka language-preservation platform. Use the
          Sessions page to run the bot, the Posts page to browse what it
          collected, and the Donations page to review and submit batches.
        </Typography>
        {isLoggedIn && dashboard &&
         <>
           <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
             {statistics.map((statistic) => (
               <Paper key={statistic.label} sx={{ px: 3, py: 2, minWidth: 180 }}>
                 <Typography variant='h4'>{statistic.value}</Typography>
                 <Typography color='text.secondary'>{statistic.label}</Typography>
               </Paper>
             ))}
           </Box>
           <Paper sx={{ px: 3, py: 2, maxWidth: 600 }}>
             <Typography variant='h6' gutterBottom>Latest extraction session</Typography>
             {!dashboard.latestSession && (
               <Typography color='text.secondary'>No sessions yet.</Typography>
             )}
             {dashboard.latestSession &&
              <>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1 }}>
                  <Typography>
                    #{dashboard.latestSession.id} {dashboard.latestSession.description}
                  </Typography>
                  <Chip size='small' label={dashboard.latestSession.status}/>
                </Box>
                <Button variant='outlined' onClick={() => navigate(`/sessions/${dashboard.latestSession!.id}`)}>
                  Open the session
                </Button>
              </>}
           </Paper>
         </>}
      </Container>
    </Box>
  );
};

export default HomePage;
