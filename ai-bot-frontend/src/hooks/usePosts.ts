import { useCallback, useEffect, useState } from 'react';
import errorMessage from '../api/errorMessage.ts';
import postApi from '../api/postApi.ts';
import type { PageResponse, PostFilter, PostResponse } from '../api/types/post.ts';
import useSnackbar from './useSnackbar.ts';

/**
 * TODO(student): Load a page of extracted posts (postApi.findAll) for the
 * PostsPage, re-fetching whenever the filter or page changes, with
 * loading/error state and a delete action (postApi.delete).
 */
const usePosts = (filter: PostFilter, page: number, size: number) => {
  const { showSnackbar } = useSnackbar();

  const [posts, setPosts] = useState<PageResponse<PostResponse> | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  const fetch = useCallback(async () => {
    setLoading(true);

    try {
      const response = await postApi.findAll(filter, page, size);
      setPosts(response.data);
    } catch (err) {
      showSnackbar(errorMessage(err, 'Failed to load posts.'), 'error');
    } finally {
      setLoading(false);
    }
  }, [filter, page, size, showSnackbar]);

  const onDelete = useCallback(async (id: number) => {
    try {
      await postApi.delete(id.toString());
      showSnackbar('The post was deleted.');
      await fetch();
    } catch (err) {
      showSnackbar(errorMessage(err, 'Failed to delete post.'), 'error');
    }
  }, [fetch, showSnackbar]);

  useEffect(() => {
    void fetch();
  }, [fetch]);

  return { posts, loading, onDelete };
};

export default usePosts;
