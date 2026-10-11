import {
  Box, Button, Checkbox, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, List, ListItem,
  ListItemButton, ListItemIcon, Typography
} from '@mui/material';
import { useEffect, useState } from 'react';
import errorMessage from '../../../../api/errorMessage.ts';
import postApi from '../../../../api/postApi.ts';
import type { CreateDonationBatchRequest } from '../../../../api/types/donation.ts';
import type { PostResponse } from '../../../../api/types/post.ts';
import useSnackbar from '../../../../hooks/useSnackbar.ts';

interface SubmitDonationDialogProps {
  open: boolean;
  onClose: () => void;
  onCreate: (data: CreateDonationBatchRequest) => Promise<boolean>;
}

// the same limits the backend checks before a post may be donated
const MIN_MACEDONIAN_CONFIDENCE = 0.5;
const MIN_TEXT_LENGTH = 20;
const MAX_POSTS = 100;

const SubmitDonationDialog = ({ open, onClose, onCreate }: SubmitDonationDialogProps) => {
  const { showSnackbar } = useSnackbar();

  const [posts, setPosts] = useState<PostResponse[]>([]);
  const [selected, setSelected] = useState<number[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [submitting, setSubmitting] = useState<boolean>(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    postApi
      .findAll({ donated: false, minMacedonianConfidence: MIN_MACEDONIAN_CONFIDENCE }, 0, MAX_POSTS)
      .then((response) => setPosts(response.data.content.filter((post) =>
        post.sourceUrl !== null && (post.content ?? '').trim().length >= MIN_TEXT_LENGTH
      )))
      .catch((err) => showSnackbar(errorMessage(err, 'Failed to load posts.'), 'error'))
      .finally(() => setLoading(false));
  }, [open, showSnackbar]);

  const allSelected = posts.length > 0 && selected.length === posts.length;

  const handleToggle = (id: number) => {
    setSelected((prev) => (prev.includes(id) ? prev.filter((postId) => postId !== id) : [...prev, id]));
  };

  const handleClose = () => {
    setPosts([]);
    setSelected([]);
    setLoading(true);
    onClose();
  };

  const handleCreate = async () => {
    setSubmitting(true);
    const created = await onCreate({ postIds: selected });
    setSubmitting(false);
    if (created) {
      handleClose();
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth='md'>
      <DialogTitle>New Donation Batch</DialogTitle>
      <DialogContent dividers>
        <Typography variant='body2' color='text.secondary' sx={{ mb: 1 }}>
          Read the posts and pick the ones to donate. Only posts that are not donated yet, look Macedonian
          (50% or more) and have at least 20 characters are listed.
        </Typography>
        {loading && (
          <Box sx={{ display: 'flex', justifyContent: 'center', my: 3 }}>
            <CircularProgress/>
          </Box>
        )}
        {!loading && posts.length === 0 && (
          <Typography>There are no posts that can be donated right now.</Typography>
        )}
        {!loading && posts.length > 0 && (
          <List dense disablePadding>
            <ListItem disablePadding divider>
              <ListItemButton onClick={() => setSelected(allSelected ? [] : posts.map((post) => post.id))}>
                <ListItemIcon sx={{ minWidth: 40 }}>
                  <Checkbox
                    edge='start'
                    disableRipple
                    tabIndex={-1}
                    checked={allSelected}
                    indeterminate={selected.length > 0 && !allSelected}
                  />
                </ListItemIcon>
                <Typography variant='subtitle2'>Select all ({posts.length})</Typography>
              </ListItemButton>
            </ListItem>
            {posts.map((post) => (
              <ListItem key={post.id} disablePadding divider>
                <ListItemButton sx={{ alignItems: 'flex-start' }} onClick={() => handleToggle(post.id)}>
                  <ListItemIcon sx={{ minWidth: 40 }}>
                    <Checkbox edge='start' disableRipple tabIndex={-1} checked={selected.includes(post.id)}/>
                  </ListItemIcon>
                  <Box sx={{ py: 0.5 }}>
                    <Typography variant='body2' sx={{ whiteSpace: 'pre-line', wordBreak: 'break-word' }}>
                      {post.content}
                    </Typography>
                    <Typography variant='caption' color='text.secondary'>
                      {post.authorHandle ?? 'unknown author'}, Macedonian {Math.round((post.macedonianConfidence ?? 0) * 100)}%
                    </Typography>
                  </Box>
                </ListItemButton>
              </ListItem>
            ))}
          </List>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Cancel</Button>
        <Button variant='contained' disabled={selected.length === 0 || submitting} onClick={handleCreate}>
          Create batch ({selected.length})
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default SubmitDonationDialog;
