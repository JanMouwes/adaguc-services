package nl.knmi.adaguc.tools.http.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;

public class BadRequestException extends HttpStatusCodeException {

    public BadRequestException() {
        super(HttpStatus.BAD_REQUEST, "Bad request");
    }
}
