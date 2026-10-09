package mk.ukim.finki.aibotbackend.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mk.ukim.finki.aibotbackend.model.domain.ExtractedPost;
import mk.ukim.finki.aibotbackend.model.dto.PostFilterDto;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the query for browsing extracted posts from the filters that are set in a {@link PostFilterDto}.
 * Filters that are {@code null} are left out.
 */
public final class ExtractedPostSpecifications {
    private ExtractedPostSpecifications() {
    }

    public static Specification<ExtractedPost> matching(PostFilterDto filter) {
        List<Specification<ExtractedPost>> specifications = new ArrayList<>();
        if (filter == null) {
            return Specification.allOf(specifications);
        }

        if (filter.sessionId() != null) {
            specifications.add((root, query, builder) ->
                builder.equal(root.get("session").get("id"), filter.sessionId()));
        }
        if (filter.socialNetwork() != null) {
            specifications.add((root, query, builder) ->
                builder.equal(root.get("session").get("socialNetwork"), filter.socialNetwork()));
        }
        if (filter.minMacedonianConfidence() != null) {
            specifications.add((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.<Double>get("macedonianConfidence"), filter.minMacedonianConfidence()));
        }
        if (filter.donated() != null) {
            specifications.add((root, query, builder) -> filter.donated()
                ? builder.isNotNull(root.get("donationBatch"))
                : builder.isNull(root.get("donationBatch")));
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            String pattern = "%" + escapeLike(filter.search().strip().toLowerCase(Locale.ROOT)) + "%";
            specifications.add((root, query, builder) ->
                builder.like(builder.lower(root.<String>get("content")), pattern, '\\'));
        }
        return Specification.allOf(specifications);
    }

    // so that % and _ typed in the search box are searched for literally
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
