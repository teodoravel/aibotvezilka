package mk.ukim.finki.aibotbackend.model.exception;

public class PostNotDonatableException extends RuntimeException {
    public PostNotDonatableException(Long id, String reason) {
        super("The extracted post with id %d cannot be donated: %s.".formatted(id, reason));
    }
}
