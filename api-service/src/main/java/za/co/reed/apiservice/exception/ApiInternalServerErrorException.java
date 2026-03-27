package za.co.reed.apiservice.exception;

public class ApiInternalServerErrorException extends RuntimeException {
    public ApiInternalServerErrorException(String message) {
        super(message);
    }
}
