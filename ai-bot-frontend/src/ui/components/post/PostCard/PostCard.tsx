import { Box, Button, Card, CardActions, CardContent, CardMedia, Chip, Typography } from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import InfoIcon from '@mui/icons-material/Info';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import VideocamIcon from '@mui/icons-material/Videocam';
import { useNavigate } from 'react-router';
import type { PostResponse } from '../../../../api/types/post.ts';

interface PostCardProps {
  post: PostResponse;
  onDelete: (id: number) => Promise<void>;
}

// from this score on a post looks Macedonian enough to be donated (the backend uses the same limit)
const DONATION_THRESHOLD = 0.5;

/**
 * TODO(student): Show the extracted post: author, content preview, source
 * link, the Macedonian-language confidence, media thumbnails, and whether it
 * is already part of a donation batch. Add navigation to /posts/{id} and a
 * delete action (usePosts().onDelete).
 */
const PostCard = ({ post, onDelete }: PostCardProps) => {
  const navigate = useNavigate();

  const thumbnail = post.mediaItems.find((item) => item.type === 'IMAGE');
  const hasVideo = post.mediaItems.some((item) => item.type === 'VIDEO');
  const donated = post.donationBatchId !== null;
  const confidence = post.macedonianConfidence ?? 0;

  const handleDelete = async () => {
    if (window.confirm('Delete this post?')) {
      await onDelete(post.id);
    }
  };

  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {thumbnail && (
        <CardMedia component='img' height='140' image={thumbnail.sourceUrl} alt='Post media'/>
      )}
      <CardContent sx={{ flexGrow: 1 }}>
        <Typography variant='subtitle2'>{post.authorHandle ?? 'unknown author'}</Typography>
        <Typography
          variant='body2'
          sx={{
            my: 1,
            whiteSpace: 'pre-line',
            overflow: 'hidden',
            display: '-webkit-box',
            WebkitBoxOrient: 'vertical',
            WebkitLineClamp: 5
          }}
        >
          {post.content}
        </Typography>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
          <Chip
            size='small'
            color={confidence >= DONATION_THRESHOLD ? 'success' : 'default'}
            label={`Macedonian ${Math.round(confidence * 100)}%`}
          />
          {hasVideo && (
            <Chip size='small' variant='outlined' icon={<VideocamIcon/>} label='video'/>
          )}
          <Chip
            size='small'
            variant='outlined'
            color={donated ? 'success' : 'default'}
            label={donated ? `In donation batch #${post.donationBatchId}` : 'Not donated'}
          />
        </Box>
      </CardContent>
      <CardActions sx={{ justifyContent: 'space-between' }}>
        <Button startIcon={<InfoIcon/>} onClick={() => navigate(`/posts/${post.id}`)}>
          Details
        </Button>
        {post.sourceUrl && (
          <Button
            component='a'
            href={post.sourceUrl}
            target='_blank'
            rel='noopener noreferrer'
            startIcon={<OpenInNewIcon/>}
          >
            Source
          </Button>
        )}
        <Button
          color='error'
          startIcon={<DeleteIcon/>}
          disabled={donated}
          onClick={handleDelete}
        >
          Delete
        </Button>
      </CardActions>
    </Card>
  );
};

export default PostCard;
