package com.ticketledger.constant;

public final class RouteConstant {

    private RouteConstant() {
        throw new IllegalStateException("Utility class");
    }

    public static final String API_BASE = "/api/v1";

    public static final String AUTH_PATH = API_BASE + "/auth";
    public static final String BOOKING_PATH = API_BASE + "/bookings";
    public static final String ADMIN_BOOKING_PATH = API_BASE + "/admin/bookings";
    public static final String ADMIN_SHOWTIME_PATH = API_BASE + "/admin/showtimes";
    public static final String WEBHOOK_PATH = API_BASE + "/webhooks";
}