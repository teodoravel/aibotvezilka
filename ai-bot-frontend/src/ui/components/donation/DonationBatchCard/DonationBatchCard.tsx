import { Button, Card, CardActions, CardContent, Chip, Typography } from '@mui/material';
import CheckIcon from '@mui/icons-material/Check';
import SendIcon from '@mui/icons-material/Send';
import { useState } from 'react';
import type { DonationBatchResponse, DonationStatus } from '../../../../api/types/donation.ts';

interface DonationBatchCardProps {
  batch: DonationBatchResponse;
  onApprove: (id: number) => Promise<void>;
  onSubmit: (id: number) => Promise<void>;
}

const statusColors: Record<DonationStatus, 'default' | 'info' | 'warning' | 'success' | 'error'> = {
  DRAFT: 'default',
  APPROVED: 'info',
  SUBMITTED: 'warning',
  ACCEPTED: 'success',
  REJECTED: 'error',
  FAILED: 'error'
};

// the backend stores what Vezilka decided for a submitted batch as "batch:accepted=8,duplicate=1,rejected=2"
const BATCH_REFERENCE = /^batch:accepted=(\d+),duplicate=(\d+),rejected=(\d+)$/;

const describeReference = (reference: string) => {
  const counts = reference.match(BATCH_REFERENCE);

  return counts
    ? `${counts[1]} accepted, ${counts[2]} already in the corpus, ${counts[3]} rejected`
    : reference;
};

const DonationBatchCard = ({ batch, onApprove, onSubmit }: DonationBatchCardProps) => {
  const [busy, setBusy] = useState<boolean>(false);

  // DRAFT -> APPROVED -> SUBMITTED -> ACCEPTED | REJECTED | FAILED, a FAILED batch can be sent again
  const canApprove = batch.status === 'DRAFT';
  const canSubmit = batch.status === 'APPROVED' || batch.status === 'FAILED';

  const handleApprove = async () => {
    setBusy(true);
    await onApprove(batch.id);
    setBusy(false);
  };

  const handleSubmit = async () => {
    if (!window.confirm(`Send the ${batch.postIds.length} posts of batch #${batch.id} to doniraj.vezilka.ai?`)) {
      return;
    }
    setBusy(true);
    await onSubmit(batch.id);
    setBusy(false);
  };

  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <CardContent sx={{ flexGrow: 1 }}>
        <Typography variant='h6'>Batch #{batch.id}</Typography>
        <Chip label={batch.status} size='small' color={statusColors[batch.status]}/>
        <Typography variant='body2' sx={{ mt: 1 }}>{batch.postIds.length} posts</Typography>
        <Typography variant='body2' color='text.secondary'>
          Created: {new Date(batch.createdAt).toLocaleString()}
        </Typography>
        {batch.submittedAt && (
          <Typography variant='body2' color='text.secondary'>
            Sent: {new Date(batch.submittedAt).toLocaleString()}
          </Typography>
        )}
        {batch.vezilkaReference && (
          <Typography variant='body2' sx={{ mt: 1, wordBreak: 'break-word' }}>
            Vezilka: {describeReference(batch.vezilkaReference)}
          </Typography>
        )}
      </CardContent>
      <CardActions sx={{ justifyContent: 'space-between' }}>
        <Button startIcon={<CheckIcon/>} disabled={!canApprove || busy} onClick={handleApprove}>
          Approve
        </Button>
        <Button startIcon={<SendIcon/>} color='success' disabled={!canSubmit || busy} onClick={handleSubmit}>
          {batch.status === 'FAILED' ? 'Send again' : 'Send to Vezilka'}
        </Button>
      </CardActions>
    </Card>
  );
};

export default DonationBatchCard;
