package mk.ukim.finki.aibotbackend.repository;

import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Filtering by {@code PostFilterDto} is done with {@link ExtractedPostSpecifications}.
 */
@Repository
public interface ExtractedPostRepository
    extends JpaRepository<ExtractedPost, Long>, JpaSpecificationExecutor<ExtractedPost> {
}
