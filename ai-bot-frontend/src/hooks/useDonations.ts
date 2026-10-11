import { useCallback, useEffect, useState } from 'react';
import donationApi from '../api/donationApi.ts';
import errorMessage from '../api/errorMessage.ts';
import type { CreateDonationBatchRequest, DonationBatchResponse } from '../api/types/donation.ts';
import useSnackbar from './useSnackbar.ts';

// Vezilka decides right away, the backend stores its verdict a moment after the batch is submitted
const VERDICT_DELAY_MS = 2000;

/**
 * Drives the donation workflow of the DonationsPage: the list of batches
 * and the create, approve and submit actions. The list is loaded again after every action.
 */
const useDonations = () => {
  const { showSnackbar } = useSnackbar();

  const [donations, setDonations] = useState<DonationBatchResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const fetch = useCallback(async () => {
    try {
      const response = await donationApi.findAll();
      setDonations(response.data);
    } catch (err) {
      showSnackbar(errorMessage(err, 'Failed to load donation batches.'), 'error');
    } finally {
      setLoading(false);
    }
  }, [showSnackbar]);

  // tells the caller whether the batch was created, so the dialog stays open when it was not
  const onCreate = useCallback(async (data: CreateDonationBatchRequest) => {
    try {
      await donationApi.add(data);
      showSnackbar('The donation batch was created.');
      await fetch();
      return true;
    } catch (err) {
      showSnackbar(errorMessage(err, 'Failed to create donation batch.'), 'error');
      return false;
    }
  }, [fetch, showSnackbar]);

  const onApprove = useCallback(async (id: number) => {
    try {
      await donationApi.approve(id.toString());
      await fetch();
    } catch (err) {
      showSnackbar(errorMessage(err, 'Failed to approve donation batch.'), 'error');
    }
  }, [fetch, showSnackbar]);

  const onSubmit = useCallback(async (id: number) => {
    try {
      await donationApi.submit(id.toString());
      showSnackbar('The batch was sent to doniraj.vezilka.ai.');
      await fetch();
      setTimeout(() => void fetch(), VERDICT_DELAY_MS);
    } catch (err) {
      showSnackbar(errorMessage(err, 'Failed to submit donation batch.'), 'error');
      // a batch that could not be sent is saved as FAILED
      await fetch();
    }
  }, [fetch, showSnackbar]);

  useEffect(() => {
    void fetch();
  }, [fetch]);

  return { donations, loading, onCreate, onApprove, onSubmit };
};

export default useDonations;
