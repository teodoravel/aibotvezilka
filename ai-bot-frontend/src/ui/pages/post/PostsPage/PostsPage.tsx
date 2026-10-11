import { Box, CircularProgress, Pagination, Typography } from '@mui/material';
import { useCallback, useState } from 'react';
import type { PostFilter } from '../../../../api/types/post.ts';
import usePosts from '../../../../hooks/usePosts.ts';
import PostFilters from '../../../components/post/PostFilters/PostFilters.tsx';
import PostGrid from '../../../components/post/PostGrid/PostGrid.tsx';

const PAGE_SIZE = 12;

/**
 * The extracted-content browser.
 */
const PostsPage = () => {
  const [filter, setFilter] = useState<PostFilter>({});
  const [page, setPage] = useState<number>(0);

  const { posts, loading, onDelete } = usePosts(filter, page, PAGE_SIZE);

  // a new filter gives a new list, so it is shown from its first page
  const handleFilterChange = useCallback((newFilter: PostFilter) => {
    setFilter(newFilter);
    setPage(0);
  }, []);

  const filtered = Object.values(filter).some((value) => value !== undefined);

  return (
    <Box>
      <Typography variant='h5' sx={{ mb: 2 }}>Extracted Posts</Typography>
      <PostFilters filter={filter} onChange={handleFilterChange}/>
      {loading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 4 }}>
          <CircularProgress/>
        </Box>
      )}
      {!loading && (!posts || posts.content.length === 0) && (
        <Typography color='text.secondary'>
          {filtered
            ? 'No posts match these filters.'
            : 'No extracted posts yet. Run an extraction session first.'}
        </Typography>
      )}
      {!loading && posts && posts.content.length > 0 &&
       <>
         <Typography variant='body2' color='text.secondary' sx={{ mb: 1 }}>
           {posts.totalElements} posts
         </Typography>
         <PostGrid posts={posts.content} onDelete={onDelete}/>
         {posts.totalPages > 1 && (
           <Box sx={{ display: 'flex', justifyContent: 'center', mt: 3 }}>
             <Pagination
               color='primary'
               count={posts.totalPages}
               page={page + 1}
               onChange={(_, value) => setPage(value - 1)}
             />
           </Box>
         )}
       </>}
    </Box>
  );
};

export default PostsPage;
