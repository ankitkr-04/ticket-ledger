package com.ticketledger.constant;

public final class RouteConstant {

    private RouteConstant() {
        throw new IllegalStateException("Utility class");
    }

    public static final String BASE_PATH = "/api/v1";
    public static final String BOOKING_PATH = BASE_PATH + "/bookings";
    public static final String AUTH_PATH = BASE_PATH + "/auth";
    public static final String WEBHOOK_PATH = BASE_PATH + "/webhooks";
    public static final String ADMIN_BOOKING_PATH = BASE_PATH + "/admin/bookings";
    public static final String ADMIN_SHOWTIME_PATH = BASE_PATH + "/admin/showtimes";
}