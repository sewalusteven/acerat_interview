package com.acerat.solidtest.customers;

public class Address {
    public String getStreet() {
        return null;
    }

    public String getZipCode() {
        return null;
    }

    public String getCity() {
        return null;
    }

    public String getFullAddress(){
        //confirm all values are filled in
        if (this.getStreet() == null || this.getStreet().isEmpty() ||this.getZipCode() == null || this.getZipCode().isEmpty() ||this.getCity() == null || this.getCity().isEmpty())
            return null;
        else
            return this.getStreet() + this.getZipCode() + this.getCity();
    }

}
