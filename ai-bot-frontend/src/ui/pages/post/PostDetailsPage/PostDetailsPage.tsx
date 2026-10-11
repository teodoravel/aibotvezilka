import { Box, Button, Chip, CircularProgress, Link, Paper, Typography } from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import PlayCircleIcon from '@mui/icons-material/PlayCircle';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import errorMessage from '../../../../api/errorMessage.ts';
import postApi from '../../../../api/postApi.ts';
import type { PostResponse } from '../../../../api/types/post.ts';
import useSnackbar from '../../../../hooks/useSnackbar.ts';

// from this score on a post looks Macedonian enough to be donated (the backend uses the same limit)
const DONATION_THRESHOLD = 0.5;

/**
 * TODO(student): Show one extracted post in full: the complete content, its
 * media items (images/videos), the source link, the language confidence and
 * its donation status (postApi.findById).
 */
const PostDetailsPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { showSnackbar } = useSnackbar();

  const [post, setPost] = useState<PostResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    postApi
      .findById(id!)
      .then((response) => setPost(response.data))
      .catch((err) => showSnackbar(errorMessage(err, 'Failed to load post.'), 'error'))
      .finally(() => setLoading(false));
  }, [id, showSnackbar]);

  if (loading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
        <CircularProgress/>
      </Box>
    );
  }

  const confidence = post?.macedonianConfidence ?? 0;

  return (
    <Box>
      <Button startIcon={<ArrowBackIcon/>} sx={{ mb: 1 }} onClick={() => navigate('/posts')}>
        All posts
      </Button>
      {!post && (
        <Typography color='text.secondary'>Post #{id} could not be loaded.</Typography>
      )}
      {post &&
       <>
         <Typography variant='h5' gutterBottom>
           Post #{id} - {post.authorHandle ?? 'unknown author'}
         </Typography>
         <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
           <Chip
             size='small'
             color={confidence >= DONATION_THRESHOLD ? 'success' : 'default'}
             label={`Macedonian ${Math.round(confidence * 100)}%`}
           />
           <Chip size='small' variant='outlined' label={post.socialNetwork}/>
           <Chip
             size='small'
             variant='outlined'
             label={`Session #${post.sessionId}`}
             onClick={() => navigate(`/sessions/${post.sessionId}`)}
           />
           <Chip
             size='small'
             variant='outlined'
             color={post.donationBatchId !== null ? 'success' : 'default'}
             label={post.donationBatchId !== null ? `In donation batch #${post.donationBatchId}` : 'Not donated'}
           />
         </Box>
         <Paper sx={{ p: 2, my: 2, whiteSpace: 'pre-line' }}>{post.content}</Paper>
         {post.sourceUrl && (
           <Typography variant='body2' sx={{ wordBreak: 'break-all' }}>
             Source: <Link href={post.sourceUrl} target='_blank' rel='noopener noreferrer'>{post.sourceUrl}</Link>
           </Typography>
         )}
         {post.postedAt && (
           <Typography variant='body2' color='text.secondary'>
             Posted: {new Date(post.postedAt).toLocaleString()}
           </Typography>
         )}
         {post.mediaItems.length > 0 &&
          <>
            <Typography variant='subtitle1' sx={{ mt: 2 }} gutterBottom>Media</Typography>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-start', gap: 2 }}>
              {post.mediaItems.map((item) => (
                item.type === 'IMAGE'
                  ? (
                    <Box
                      key={item.id}
                      component='img'
                      src={item.sourceUrl}
                      alt='Post image'
                      sx={{ maxWidth: 320, borderRadius: 1 }}
                    />
                  )
                  : (
                    <Button
                      key={item.id}
                      component='a'
                      href={item.sourceUrl}
                      target='_blank'
                      rel='noopener noreferrer'
                      variant='outlined'
                      startIcon={<PlayCircleIcon/>}
                    >
                      Open the video
                    </Button>
                  )
              ))}
            </Box>
          </>}
       </>}
    </Box>
  );
};

export default PostDetailsPage;
