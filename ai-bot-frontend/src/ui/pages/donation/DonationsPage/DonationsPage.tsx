import { Box, Button, CircularProgress, Grid, Paper, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useState } from 'react';
import type { DonationStatus } from '../../../../api/types/donation.ts';
import useDonations from '../../../../hooks/useDonations.ts';
import DonationBatchCard from '../../../components/donation/DonationBatchCard/DonationBatchCard.tsx';
import SubmitDonationDialog from '../../../components/donation/SubmitDonationDialog/SubmitDonationDialog.tsx';

/**
 * The donation dashboard towards doniraj.vezilka.ai.
 * TODO(student): Implement useDonations, DonationBatchCard and
 * SubmitDonationDialog, plus overall donation statistics.
 */
const DonationsPage = () => {
  const { donations, loading, onCreate, onApprove, onSubmit } = useDonations();

  const [newBatchDialogOpen, setNewBatchDialogOpen] = useState<boolean>(false);

  const countByStatus = (...statuses: DonationStatus[]) =>
    donations.filter((batch) => statuses.includes(batch.status)).length;

  const statistics = [
    { label: 'Batches', value: donations.length },
    { label: 'Posts in batches', value: donations.reduce((sum, batch) => sum + batch.postIds.length, 0) },
    { label: 'Waiting to be sent', value: countByStatus('DRAFT', 'APPROVED') },
    { label: 'Accepted by Vezilka', value: countByStatus('ACCEPTED') },
    { label: 'Rejected or failed', value: countByStatus('REJECTED', 'FAILED') }
  ];

  return (
    <Box>
      {loading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <CircularProgress/>
        </Box>
      )}
      {!loading &&
       <>
         <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
           <Typography variant='h5'>Donations</Typography>
           <Button variant='contained' startIcon={<AddIcon/>} onClick={() => setNewBatchDialogOpen(true)}>
             New Batch
           </Button>
         </Box>
         <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, mb: 2 }}>
           {statistics.map((statistic) => (
             <Paper key={statistic.label} sx={{ px: 2, py: 1, minWidth: 150 }}>
               <Typography variant='h6'>{statistic.value}</Typography>
               <Typography variant='body2' color='text.secondary'>{statistic.label}</Typography>
             </Paper>
           ))}
         </Box>
         {donations.length === 0 && (
           <Typography color='text.secondary'>
             No donation batches yet. Group extracted posts into a batch and
             donate them to doniraj.vezilka.ai.
           </Typography>
         )}
         <Grid container spacing={2}>
           {donations.map((batch) => (
             <Grid key={batch.id} size={{ xs: 12, sm: 6, md: 4 }}>
               <DonationBatchCard batch={batch} onApprove={onApprove} onSubmit={onSubmit}/>
             </Grid>
           ))}
         </Grid>
         <SubmitDonationDialog
           open={newBatchDialogOpen}
           onClose={() => setNewBatchDialogOpen(false)}
           onCreate={onCreate}
         />
       </>}
    </Box>
  );
};

export default DonationsPage;
