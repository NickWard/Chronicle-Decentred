package town.lost.examples.exchange.dto;

import net.openhft.chronicle.decentred.dto.VanillaSignedMessage;
import town.lost.examples.exchange.api.CurrencyPair;

public class NewOrderRequest extends VanillaSignedMessage<NewOrderRequest> {
    private double quantity;
    private double maxPrice;
    private CurrencyPair currencyPair;
    private long timeToLive; // in milliseconds
    private boolean buy;

    public NewOrderRequest() {

    }

    public NewOrderRequest(double quantity, double maxPrice, CurrencyPair currencyPair, long timeToLive, boolean buy) {
        this.quantity = quantity;
        this.maxPrice = maxPrice;
        this.currencyPair = currencyPair;
        this.timeToLive = timeToLive;
        this.buy = buy;
    }

    public double quantity() {
        return quantity;
    }

    public NewOrderRequest quantity(double quantity) {
        this.quantity = quantity;
        return this;
    }

    public double maxPrice() {
        return maxPrice;
    }

    public NewOrderRequest maxPrice(double maxPrice) {
        this.maxPrice = maxPrice;
        return this;
    }

    public CurrencyPair currencyPair() {
        return currencyPair;
    }

    public NewOrderRequest currencyPair(CurrencyPair currencyPair) {
        this.currencyPair = currencyPair;
        return this;
    }

    public long timeToLive() {
        return timeToLive;
    }

    public NewOrderRequest timeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
        return this;
    }

    public boolean buy() {
        return buy;
    }

    public NewOrderRequest buy(boolean buy) {
        this.buy = buy;
        return this;
    }
}