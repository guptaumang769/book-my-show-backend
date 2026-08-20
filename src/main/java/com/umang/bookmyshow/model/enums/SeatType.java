package com.umang.bookmyshow.model.enums;

/**
 * Seat category. The price increment is added to a show's base price to
 * derive the final per-seat price (see ShowSeat creation in ShowService).
 */
public enum SeatType {
    REGULAR(0),
    PREMIUM(50),
    VIP(100);

    private final int priceIncrement;

    SeatType(int priceIncrement) {
        this.priceIncrement = priceIncrement;
    }

    public int getPriceIncrement() {
        return priceIncrement;
    }
}
