package mk.ukim.finki.aibotbackend.service.domain.impl;

import java.util.List;
import java.util.Optional;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.dto.PostFilterDto;
import mk.ukim.finki.aibotbackend.model.exception.PostAlreadyDonatedException;
import mk.ukim.finki.aibotbackend.repository.ExtractedPostRepository;
import mk.ukim.finki.aibotbackend.repository.ExtractedPostSpecifications;
import mk.ukim.finki.aibotbackend.service.domain.ExtractedPostService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ExtractedPostServiceImpl implements ExtractedPostService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ExtractedPostRepository extractedPostRepository;

    public ExtractedPostServiceImpl(ExtractedPostRepository extractedPostRepository) {
        this.extractedPostRepository = extractedPostRepository;
    }

    @Override
    public Page<ExtractedPost> findAll(PostFilterDto filter, int page, int size) {
        // TODO(student): Combine the non-null filter fields into a query, e.g.
        //  with JPA Specifications (ExtractedPostRepository already extends
        //  JpaSpecificationExecutor).
        PageRequest pageRequest = PageRequest.of(
            Math.max(page, 0),
            Math.clamp(size, 1, MAX_PAGE_SIZE),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return extractedPostRepository.findAll(ExtractedPostSpecifications.matching(filter), pageRequest);
    }

    @Override
    public Optional<ExtractedPost> findById(Long id) {
        // TODO(student): Implement ExtractedPostService.findById().
        return extractedPostRepository.findById(id);
    }

    @Override
    public List<ExtractedPost> findAllById(List<Long> ids) {
        // TODO(student): Implement ExtractedPostService.findAllById().
        return extractedPostRepository.findAllById(ids);
    }

    @Override
    public List<ExtractedPost> saveAll(List<ExtractedPost> posts) {
        // TODO(student): Implement ExtractedPostService.saveAll().
        return extractedPostRepository.saveAll(posts);
    }

    @Override
    public Optional<ExtractedPost> deleteById(Long id) {
        // TODO(student): Implement ExtractedPostService.deleteById().
        Optional<ExtractedPost> post = extractedPostRepository.findById(id);
        post.ifPresent(found -> {
            // a donated post stays, so its donation batch keeps the content that was sent
            if (found.getDonationBatch() != null) {
                throw new PostAlreadyDonatedException(id);
            }
            extractedPostRepository.delete(found);
        });
        return post;
    }
}
