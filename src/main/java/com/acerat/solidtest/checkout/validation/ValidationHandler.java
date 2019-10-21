package com.acerat.solidtest.checkout.validation;

import com.acerat.solidtest.cardpayments.CardDetails;
import com.acerat.solidtest.cardpayments.CardPaymentService;
import com.acerat.solidtest.checkout.state.*;
import com.acerat.solidtest.configuration.ApplicationConfiguration;
import com.acerat.solidtest.customers.Address;
import com.acerat.solidtest.customers.Customer;
import com.acerat.solidtest.customers.CustomerPaymentMethod;
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

public class ValidationHandler {

    private static CheckoutState state;
    private static boolean validState = false;

    //Validating Customer Shipping Address
    public boolean shippingAddressValidation (Customer customerInfo)
    { 
        //Get Shipping Address Details from customerInfo 
         Address customerAddress = customerInfo.getShippingAddress();
         
         //Check if customer has an address in there repository
        if(customerAddress.getFullAddress() == null || customerAddress == null) {
            //Either no full address or just a part of the address
            state.shipmentFailed(ShipmentFailures.INVALID_CUSTOMER_ADDRESS);

            return validState;
        }
        else {
            
            //Initialize reference variable "addressValidation" from ShipmentTracker Object
            ShipmentTracker shipmentDestination = new ShipmentTracker(ApplicationConfiguration.getConnectionString());

            //Check whether domain can ship to destination
            if (!shipmentDestination.canShipToDestination(customerAddress)) {
                state.shipmentFailed(ShipmentFailures.CANNOT_SHIP_TO_DESTINATION);

                return validState;
            } else {

                //Validated Customer Shipment Address that is shippable
                validState = true;
                return validState;
            }

        }
    }

    public void warehouseOrderReservationAndShipment(Order order)
    {
        //Initializing the warehouse and products reference variables from the Warehouse and ProductStore class objects
        Warehouse warehouse = new Warehouse(ApplicationConfiguration.getConnectionString());
        ProductStore products = new ProductStore(ApplicationConfiguration.getConnectionString());

        //Access all products under the order
        for (OrderLine orderProduct: order.getOrderLines()) {
            Product product = products.getById(orderProduct.getProductId());

            //confirm product is existent from the ProductId supplied from the order
            if (product == null)
            {
                state.warehouseReservationFailed(WarehouseReservationFailures.PRODUCT_NOT_FOUND);
                state.shipmentActivationFailed(WarehouseSendFailures.PRODUCT_NOT_FOUND);
            }

            //check whether product is in warehouse, if its not move to the next order product
            if (!product.isStoredInWarehouse())
                continue;
            //reserve product in stock in the event of failure
            if (!warehouse.isReservedInStock(orderProduct.getUniqueOrderLineReference(), orderProduct.getQty())) {
                if (!warehouse.tryReserveItems(orderProduct.getUniqueOrderLineReference(), orderProduct.getQty())) {
                    state.warehouseReservationFailed(WarehouseReservationFailures.COULD_NOT_RESERVE_ITEMS_IN_STOCK);
                }
            }

            //activate shipment else log the error
            if (!warehouse.activateShipment(orderProduct.getUniqueOrderLineReference())) {
                state.shipmentActivationFailed(WarehouseSendFailures.COULD_NOT_ACTIVATE_SHIPMENT);
            }

        }

    }

    public void makePayment(CustomerPaymentMethod method, Customer customer, Order order) {
        switch (method)
        {
            case CARD:
                //Decrypt Card details
                //Initialize trustore reference variable
                TrustStore trustStore = new TrustStore(ApplicationConfiguration.getTrustStoreCredentials());

                byte[] encryptedCardDetails = trustStore.getCardDetailsByCustomerId(customer.getCustomerId());
                List<CardDetails> cardDetailsList = Encryption.decryptFromSecret(encryptedCardDetails, customer.getCustomerSecret());

                // Pick the currently valid credit card
                Optional<CardDetails> currentCardDetails = Optional.empty();
                for (CardDetails cardDetails : cardDetailsList) {
                    if (cardDetails.getExpiresAt().isAfter(LocalDate.now())) {
                        currentCardDetails = Optional.of(cardDetails);
                        break;
                    }
                }

                // If there is no valid card update checkout state
                if (!currentCardDetails.isPresent()) {
                    state.cardPaymentFailed(CardPaymentFailures.NO_VALID_CREDIT_CARDS);
                }

                CardPaymentService cardPaymentService = new CardPaymentService(ApplicationConfiguration.getCardPaymentConfiguration());
                CardPaymentResult cardPaymentResult = cardPaymentService.chargeCreditCard(currentCardDetails.get());
                if (!cardPaymentResult.succeeded()) {
                    state.cardPaymentFailed(CardPaymentFailures.COULD_NOT_COMPLETE_CARD_PAYMENT);
                }
                state.cardPaymentCompletedUsing(currentCardDetails.get().getCardDetailsReference());

                break;

            case INVOICE:
                // Confirm customer address before producing an invoice for sending
                Address invoiceAddress = customer.getInvoiceAddress();
                if (invoiceAddress == null) {
                    state.failedToInvoiceCustomer(InvoiceFailures.MISSING_INVOICE_ADDRESS);
                }

                if (invoiceAddress.getFullAddress() == null ) {
                    state.failedToInvoiceCustomer(InvoiceFailures.INVALID_CUSTOMER_ADDRESS);
                }

                //produce invoice using the invoice handler object and adjust the check out state.
                InvoiceHandler invoiceHandler = new InvoiceHandler(ApplicationConfiguration.getConnectionString());

                Invoice invoice = invoiceHandler.produceInvoice(order, customer);
                state.invoiceSentSuccessfully(invoice.getInvoiceId());
                break;
        }
    }


}
