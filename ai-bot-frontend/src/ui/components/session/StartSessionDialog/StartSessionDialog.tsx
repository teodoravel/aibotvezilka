import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, MenuItem, TextField, Typography
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import { useState } from 'react';
import type { CreateTargetRequest, SocialNetwork, TargetType } from '../../../../api/types/session.ts';
import useSessions from '../../../../hooks/useSessions.ts';

interface StartSessionDialogProps {
  open: boolean;
  onClose: () => void;
}

interface TargetTypeOption {
  type: TargetType;
  label: string;
  placeholder: string;
}

// the bot of this project works with YouTube
const socialNetworks: SocialNetwork[] = ['YOUTUBE'];

const targetTypes: TargetTypeOption[] = [
  { type: 'PROFILE', label: 'Channel', placeholder: '@channel' },
  { type: 'HASHTAG', label: 'Hashtag', placeholder: '#македонија' },
  { type: 'KEYWORD', label: 'Keyword', placeholder: 'Охрид' },
  { type: 'FEED_URL', label: 'Link', placeholder: 'https://www.youtube.com/watch?v=...' }
];

const emptyTarget: CreateTargetRequest = { type: 'PROFILE', value: '' };

/**
 * TODO(student): Implement the "new extraction session" form: a select for
 * the social network (your assigned one), a description field, and a dynamic
 * list of targets (type + value). Submit it via useSessions().onCreate and
 * close the dialog on success.
 */
const StartSessionDialog = ({ open, onClose }: StartSessionDialogProps) => {
  const { onCreate } = useSessions();

  const [socialNetwork, setSocialNetwork] = useState<SocialNetwork>('YOUTUBE');
  const [description, setDescription] = useState<string>('');
  const [targets, setTargets] = useState<CreateTargetRequest[]>([emptyTarget]);
  const [submitting, setSubmitting] = useState<boolean>(false);

  const valid = targets.every((target) => target.value.trim() !== '');

  const handleTargetChange = (index: number, changes: Partial<CreateTargetRequest>) => {
    setTargets((prev) => prev.map((target, i) => (i === index ? { ...target, ...changes } : target)));
  };

  const handleClose = () => {
    setDescription('');
    setTargets([emptyTarget]);
    onClose();
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    await onCreate({
      socialNetwork,
      description: description.trim(),
      targets: targets.map((target) => ({ type: target.type, value: target.value.trim() }))
    });
    setSubmitting(false);
    handleClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth='sm'>
      <DialogTitle>New Extraction Session</DialogTitle>
      <DialogContent>
        <TextField
          select
          fullWidth
          label='Social network'
          margin='normal'
          value={socialNetwork}
          onChange={(event) => setSocialNetwork(event.target.value as SocialNetwork)}
        >
          {socialNetworks.map((network) => (
            <MenuItem key={network} value={network}>{network}</MenuItem>
          ))}
        </TextField>
        <TextField
          fullWidth
          label='Description'
          margin='normal'
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />
        <Typography variant='subtitle1' sx={{ mt: 2 }}>What should the bot visit?</Typography>
        {targets.map((target, index) => (
          <Box key={index} sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
            <TextField
              select
              label='Type'
              sx={{ width: 160 }}
              value={target.type}
              onChange={(event) => handleTargetChange(index, { type: event.target.value as TargetType })}
            >
              {targetTypes.map((option) => (
                <MenuItem key={option.type} value={option.type}>{option.label}</MenuItem>
              ))}
            </TextField>
            <TextField
              fullWidth
              required
              label='Value'
              placeholder={targetTypes.find((option) => option.type === target.type)?.placeholder}
              value={target.value}
              onChange={(event) => handleTargetChange(index, { value: event.target.value })}
            />
            <IconButton
              aria-label='remove target'
              disabled={targets.length === 1}
              onClick={() => setTargets((prev) => prev.filter((_, i) => i !== index))}
            >
              <DeleteIcon/>
            </IconButton>
          </Box>
        ))}
        <Button startIcon={<AddIcon/>} sx={{ mt: 1 }} onClick={() => setTargets((prev) => [...prev, emptyTarget])}>
          Add target
        </Button>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Cancel</Button>
        <Button variant='contained' disabled={!valid || submitting} onClick={handleSubmit}>
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default StartSessionDialog;
