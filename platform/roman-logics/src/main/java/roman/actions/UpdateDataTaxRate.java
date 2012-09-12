package roman.actions;

import java.io.Serializable;

public class UpdateDataTaxRate implements Serializable {
    public Integer taxRateNumber;
    public Double taxRateValue;

    public UpdateDataTaxRate(Integer taxRateNumber, Double taxRateValue) {
        this.taxRateNumber = taxRateNumber;
        this.taxRateValue = taxRateValue;
    }
}
