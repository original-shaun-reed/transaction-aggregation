package za.co.reed.apiservice.exception;


public class ApiNotFoundException extends RuntimeException {
    public ApiNotFoundException(String message) {
        super(message);
    }

}
