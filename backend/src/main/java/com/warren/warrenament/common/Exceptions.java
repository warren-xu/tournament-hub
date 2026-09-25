package com.warren.warrenament.common;

/** Thin domain exceptions mapped to HTTP status codes by {@link GlobalExceptionHandler}. */
public final class Exceptions {

    private Exceptions() {
    }

    /** 404 - the requested entity does not exist. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }

        public static NotFoundException of(String what, Object id) {
            return new NotFoundException(what + " " + id + " not found");
        }
    }

    /** 400 - the request is structurally fine but violates a domain rule. */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    /** 403 - the caller is authenticated but not allowed to do this. */
    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }

    /**
     * 409 - a bid was valid-looking but lost to the rules of the auction
     * (too low, over budget, lot already closed). Separate from BadRequest so the
     * client can show these inline on the bid button instead of as an error toast.
     */
    public static class BidRejectedException extends RuntimeException {
        public BidRejectedException(String message) {
            super(message);
        }
    }
}
