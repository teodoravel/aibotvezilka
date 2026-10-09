package mk.ukim.finki.aibotbackend.model.exception;

public class PostAlreadyDonatedException extends RuntimeException {
    public PostAlreadyDonatedException(Long id) {
        super("The extracted post with id %d is part of a donation batch and cannot be deleted.".formatted(id));
    }
}
