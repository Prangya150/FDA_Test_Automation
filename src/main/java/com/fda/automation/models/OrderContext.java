package com.fda.automation.models;

import java.math.BigDecimal;

/**
 * Carries dynamic values captured at one stage of the FBS order lifecycle (FDA) that are
 * required by a later stage (Mirakl, Kibo). Populated incrementally by TC_FBS_001_Test as the
 * scenario progresses; never pre-populated with hardcoded values.
 */
public class OrderContext {

    private String orderId;
    private BigDecimal fdaOrderTotal;
    private BigDecimal miraklOrderTotal;
    private String kiboAccessToken;
    private String kiboOrderId;
    private String deliveryType;
    private String trackingNumber;

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public BigDecimal getFdaOrderTotal() {
        return fdaOrderTotal;
    }

    public void setFdaOrderTotal(BigDecimal fdaOrderTotal) {
        this.fdaOrderTotal = fdaOrderTotal;
    }

    public BigDecimal getMiraklOrderTotal() {
        return miraklOrderTotal;
    }

    public void setMiraklOrderTotal(BigDecimal miraklOrderTotal) {
        this.miraklOrderTotal = miraklOrderTotal;
    }

    public String getKiboAccessToken() {
        return kiboAccessToken;
    }

    public void setKiboAccessToken(String kiboAccessToken) {
        this.kiboAccessToken = kiboAccessToken;
    }

    public String getKiboOrderId() {
        return kiboOrderId;
    }

    public void setKiboOrderId(String kiboOrderId) {
        this.kiboOrderId = kiboOrderId;
    }

    public String getDeliveryType() {
        return deliveryType;
    }

    public void setDeliveryType(String deliveryType) {
        this.deliveryType = deliveryType;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    /** Deliberately omits kiboAccessToken so it never ends up in a log line via this object. */
    @Override
    public String toString() {
        return "OrderContext{orderId=" + orderId
                + ", fdaOrderTotal=" + fdaOrderTotal
                + ", miraklOrderTotal=" + miraklOrderTotal
                + ", kiboOrderId=" + kiboOrderId
                + ", deliveryType=" + deliveryType
                + ", trackingNumber=" + trackingNumber
                + ", kiboAccessToken=****}";
    }
}
