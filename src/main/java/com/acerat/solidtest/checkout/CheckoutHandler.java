package com.acerat.solidtest.checkout;

import com.acerat.solidtest.cardpayments.CardDetails;
import com.acerat.solidtest.cardpayments.CardPaymentService;
import com.acerat.solidtest.checkout.state.*;
import com.acerat.solidtest.checkout.validation.ValidationHandler;
import com.acerat.solidtest.configuration.ApplicationConfiguration;
import com.acerat.solidtest.customers.Address;
import com.acerat.solidtest.customers.Customer;
import com.acerat.solidtest.customers.CustomerPaymentMethod;
import com.acerat.solidtest.customers.CustomerRepository;
import com.acerat.solidtest.encryptedstores.Encryption;
import com.acerat.solidtest.encryptedstores.TrustStore;
import com.acerat.solidtest.invoicing.Invoice;
import com.acerat.solidtest.invoicing.InvoiceHandler;
import com.acerat.solidtest.logistics.ShipmentTracker;
import com.acerat.solidtest.logistics.Warehouse;
import com.acerat.solidtest.product.Product;
import com.acerat.solidtest.product.ProductStore;
import com.acerat.solidtest.shoppingcart.Order;
import com.acerat.solidtest.shoppingcart.OrderLine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class CheckoutHandler {
    public CheckoutState checkout(CheckoutState checkoutState) {
        // Initialising order reference variable from getOrder method in the checkoutState object
        Order order = checkoutState.getOrder();

        /* Initialising a customer object from customerRespository method
        get using customer ID gotten from order method getCustomerId */

        CustomerRepository customerRepository = new CustomerRepository(ApplicationConfiguration.getConnectionString());
        Customer customer = customerRepository.get(order.getCustomerId());

        // Validate shipping information
        ValidationHandler validation = new ValidationHandler();
        if(validation.shippingAddressValidation(customer) == true)
            checkoutState.shipmentVerified();

        // Make sure we don't charge customer twice
        if (!checkoutState.isPaid()) {
            // Adjust Payment according to payment method by customer
            validation.makePayment(customer.getConfiguration().getPaymentMenthod(),customer,order);
        }

        // Make sure we reserve items in stock for the order and then ship them
        validation.warehouseOrderReservationAndShipment(order);
        checkoutState.warehouseReservationSucceeded();
        checkoutState.shipmentActivated();

        return checkoutState;
    }

}
