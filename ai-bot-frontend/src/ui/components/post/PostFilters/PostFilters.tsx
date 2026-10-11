import { Box, MenuItem, Slider, TextField, Typography } from '@mui/material';
import { useEffect, useState } from 'react';
import sessionApi from '../../../../api/sessionApi.ts';
import type { PostFilter } from '../../../../api/types/post.ts';
import type { SessionResponse } from '../../../../api/types/session.ts';

interface PostFiltersProps {
  filter: PostFilter;
  onChange: (filter: PostFilter) => void;
}

const SEARCH_DELAY_MS = 400;

/**
 * TODO(student): Implement the filter bar for the content browser: session,
 * minimum Macedonian confidence (slider), donated yes/no, and a free-text
 * search over the content. Call onChange with the updated filter.
 */
const PostFilters = ({ filter, onChange }: PostFiltersProps) => {
  const [sessions, setSessions] = useState<SessionResponse[]>([]);
  const [search, setSearch] = useState<string>(filter.search ?? '');
  const [minConfidence, setMinConfidence] = useState<number>(filter.minMacedonianConfidence ?? 0);

  useEffect(() => {
    sessionApi
      .findAll()
      .then((response) => setSessions(response.data))
      .catch(() => setSessions([]));
  }, []);

  // the search is applied a moment after the typing stops, not on every key
  useEffect(() => {
    const timeout = setTimeout(() => {
      const text = search.trim();
      if (text !== (filter.search ?? '')) {
        onChange({ ...filter, search: text === '' ? undefined : text });
      }
    }, SEARCH_DELAY_MS);
    return () => clearTimeout(timeout);
  }, [search, filter, onChange]);

  const donated = filter.donated === undefined ? 'all' : filter.donated ? 'yes' : 'no';

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 2, mb: 2 }}>
      <TextField
        size='small'
        label='Search in the text'
        sx={{ width: 240 }}
        value={search}
        onChange={(event) => setSearch(event.target.value)}
      />
      <TextField
        select
        size='small'
        label='Session'
        sx={{ width: 240 }}
        value={filter.sessionId ?? ''}
        onChange={(event) => onChange({
          ...filter,
          sessionId: event.target.value === '' ? undefined : Number(event.target.value)
        })}
      >
        <MenuItem value=''>All sessions</MenuItem>
        {sessions.map((session) => (
          <MenuItem key={session.id} value={session.id}>
            #{session.id} {session.description}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        size='small'
        label='Donated'
        sx={{ width: 180 }}
        value={donated}
        onChange={(event) => onChange({
          ...filter,
          donated: event.target.value === 'all' ? undefined : event.target.value === 'yes'
        })}
      >
        <MenuItem value='all'>All posts</MenuItem>
        <MenuItem value='no'>Not donated yet</MenuItem>
        <MenuItem value='yes'>Already donated</MenuItem>
      </TextField>
      <Box sx={{ width: 240 }}>
        <Typography variant='caption' color='text.secondary'>
          Macedonian at least {Math.round(minConfidence * 100)}%
        </Typography>
        <Slider
          size='small'
          min={0}
          max={1}
          step={0.05}
          value={minConfidence}
          onChange={(_, value) => setMinConfidence(value as number)}
          onChangeCommitted={(_, value) => onChange({
            ...filter,
            minMacedonianConfidence: value === 0 ? undefined : value as number
          })}
        />
      </Box>
    </Box>
  );
};

export default PostFilters;
