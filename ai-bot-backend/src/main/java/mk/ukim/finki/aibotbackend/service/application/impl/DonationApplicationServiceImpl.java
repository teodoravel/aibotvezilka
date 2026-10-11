package mk.ukim.finki.aibotbackend.service.application.impl;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.dto.CreateDonationBatchDto;
import mk.ukim.finki.aibotbackend.model.dto.DisplayDonationBatchDto;
import mk.ukim.finki.aibotbackend.model.exception.VezilkaIntegrationException;
import mk.ukim.finki.aibotbackend.service.application.DonationApplicationService;
import mk.ukim.finki.aibotbackend.service.domain.DonationService;
import org.springframework.stereotype.Service;

@Service
public class DonationApplicationServiceImpl implements DonationApplicationService {
    private final DonationService donationService;

    public DonationApplicationServiceImpl(DonationService donationService) {
        this.donationService = donationService;
    }

    @Override
    public List<DisplayDonationBatchDto> findAll() {
        // TODO(student): Implement DonationApplicationService.findAll().
        return DisplayDonationBatchDto.from(donationService.findAll());
    }

    @Override
    public Optional<DisplayDonationBatchDto> findById(Long id) {
        // TODO(student): Implement DonationApplicationService.findById().
        return donationService
            .findById(id)
            .map(DisplayDonationBatchDto::from);
    }

    @Override
    @Transactional
    public DisplayDonationBatchDto create(CreateDonationBatchDto createDonationBatchDto) {
        // TODO(student): Implement DonationApplicationService.create().
        return DisplayDonationBatchDto.from(donationService.createBatch(createDonationBatchDto.postIds()));
    }

    @Override
    @Transactional
    public DisplayDonationBatchDto approve(Long id) {
        // TODO(student): Implement DonationApplicationService.approve().
        return DisplayDonationBatchDto.from(donationService.approve(id));
    }

    @Override
    // when Vezilka cannot be reached the batch is still saved as FAILED, so it can be sent again later
    @Transactional(dontRollbackOn = VezilkaIntegrationException.class)
    public DisplayDonationBatchDto submit(Long id) {
        // TODO(student): Implement DonationApplicationService.submit().
        return DisplayDonationBatchDto.from(donationService.submit(id));
    }
}
