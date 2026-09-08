package com.umang.bookmyshow.model.enums;

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
