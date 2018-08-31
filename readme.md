Stripe Starter Service
===

This project offers a `Spring Boot Starter` and a `Java service` that allows users to access the [rich API](https://stripe.com/docs/api/java#intro) provided by the [Stripe Payment Gateway](https://stripe.com) and offers an opinionated but limited functionality of using payment gateway.

### To use in your project

The `Spring Boot Starter` can be used in your _Spring Boot application_ by being added as a dependency your application's `pom.xml`.

```
    <dependency>
	<groupId>com.technochord.spring.starter.stripe</groupId>
	<artifactId>stripe-spring-boot-starter</artifactId>
	<version>1.0.0</version>
    </dependency>
```
This transitively pulls in the `StripeService` described below. The `Demo` project can be viewed to see usage of the starter project.

The `StripeService` can be used in your _plain java application_ by being added as a dependency your application's `pom.xml`. Note that there is no dependency on the `SpringFramework` with this approach:

```
    <dependency>
        <groupId>com.technochord.stripe</groupId>
        <artifactId>stripe-service</artifactId>
        <version>1.0.2</version>
    </dependency>
```

In any class that belongs to your project:

```
  import com.technochord.stripe.service.StripeService;
  ...

  //Obtain an apiKey from https://dashboard.stripe.com/developers
  StripeService stripeService = new StripeService(stripeApiKey);
  ...

  //Use stripeService methods
  stripeService.createCustomer("anEmail@gmail.com", "A new Pilot Customer", "GENERAL");
  ...

```

Most [Common Use Cases](#Common-Use-Cases) are covered by this service. 

### Basics

- A Stripe `Customer` represents a user who's using the payment gateway to make a payment.
- A `Payment Source` is applied to a `Customer`. A `Customer` can have several `Payment Sources` 
but only one default at a time.
_StripeService_ only supports a `Payment Source` of type `Credit Card` currently. A `Payment Source`
is also called a `Token`.
- Stripe provided client side Javascript (called `Elements`) can be used to enter
credit card info in the browser or mobile device and submit the form info to your application. The payload that 
is sent by submitting the form carries in it a `Token` that can be supplied to the appropriate API call.
- A `Plan` should be created on the Stripe dashboard and there you could specify frequency, price, period.
- A `Coupon` should be created on the Stripe dashboard and there you could specify duration, discount amount and period.
- A `Subscription` ties a `Customer` to a `Plan`. A Customer can subscribe to _only_ one subscription at a time. 
- A `Payment Source` (aka credit card info), needs to recorded against a `Customer` _before_ he can subscribe to a `Plan`.
- A `Customer` can have at most, _one_ payment source at a time.
- If a customer needs to subscribe to a new `Plan`, a new subscription needs to 
be created and the old subscription is (automatically) canceled. `Invoice`s generated
by the old subscription are still available.
- A `Discount` is applied (via a `Coupon`) to a `Customer` and `Invoice`s that are generated apply the coupon discount based 
on what coupon is applied on the `Customer` at that time.


### Pre Conditions
 - The e-commerce site (that intends to use this service to access [Stripe Payment Gateway](https://stripe.com/docs/api/java)) has
 created a (business) account at [Stripe](https://dashboard.stripe.com/register).
 
 - A Product has been configured at [Stripe](https://dashboard.stripe.com/register).
 
 - The Subscription Plans have been configured on Stripe. This will determine how much you intend to ask your customers to 
 pay and at what frequency.

  - Coupons have been (optionally) created at Stripe. These can be applied or removed from Customers. Coupons can 
  also be used to specify trial periods and discounts.
  
 - An Email address is needed to create a customer. (The validitity of the email address is not guaranteed by this service)
 
### Common Use Cases     
The use cases that can currently be addressed are below. See [Tests](stripe-service/src/test/java/com/technochord/stripe/StripeApplicationIntegrationTests.java) for examples of most of these use case.

- Create a Stripe `Customer` and record the Stripe `customerId` in your application 
(possibly against a User object in your application).

    ```
      String customerId = stripeService.createCustomer("anEmail@email.com", "a description");
    ```

- Associate a `Payment Source` to this customer.

    ```
      stripeService.addOrReplacePaymentSourceForCustomer(customerId, "tok_amex");
      - OR -
      stripeService.addOrReplacePaymentSourceForCustomer(customerId, "tok_visa");
    ```

- Remove a `Payment Source` for a `Customer`:

    ```
      stripeService.removePaymentSourceFromCustomer(customerId);
    ```

- Allow a user to select and subscribe to a pre-configured plan (on Stripe.com).

    ```
      String subscriptionId = stripeService.createSubscriptionForCustomerAndCharge("anEmail@email.com", "monthly-plan");
    ```
    
- Check to see if any customer is delinquent (Scheduled payment has been declined or the card expired).

    ```
      boolean isDelinquent = stripeService.isCustomerDelinquent(customerId);
    ```

- Change subscription plan

    ```
		//Create a customer
		String customerId = stripeService.createCustomer("anEmail@email.com", "a description");

		//Add a payment source
		stripeService.replacePaymentSourceForCustomer(customerId, "tok_amex");

		//Create a subscription against a plan and charge the customer
		String subscriptionId = stripeService.createSubscriptionForCustomerAndCharge("anEmail@email.com", "monthly-plan");

		//Check the invoiced amount is correct
		Invoice invoice = stripeService.getLatestInvoiceForSubscription(subscriptionId);

		Assert.assertTrue(invoice.getAmountPaid() == 100);

		//Apply a coupon to the customer that is configured to extend a discount of 10% 
		stripeService.applyCouponToCustomer(customerId, "TEST_COUPON_ID");

		//Subscribe again
		String reSubscriptionId = stripeService.createSubscriptionForCustomerAndCharge("anEmail@email.com", "monthly-plan");

		// Check invoice.. should be the discounted amount
		Invoice reInvoice = stripeService.getLatestInvoiceForSubscription(reSubscriptionId);

		Assert.assertTrue(reInvoice.getAmountPaid() == 90);
    ```

- Cancel subscription for a customer.

    ```
      stripeService.removePaymentSourceFromCustomer(customerId);
    ```

- Allow a user to change their credit card info

    Note that the credit card info is _not_ stored in Stripe service because _client side tokenization_ 
    is being used. The Stripe supplied `Elements` package, accepts credit card info from the user
    and returns a unique `token` that is can be recorded against the customer using the below API call:

    ```
      stripeService.replacePaymentSourceForCustomer(customerId, "tok_amex");
    ```
- Change customer's email address

    Can change the email address to an address that is not already assigned to an existing customer.
    ```
      stripeService.changeCustomerEmail(customerId1, "anEmail2@email.com");
    ```

- See list of Coupons available.

    ```
      List<Coupon> couponList = stripeService.listAllCoupons();
    ```

- Apply a coupon to a customer

    ```
      stripeService.applyCouponToCustomer(customerId, "TEST_COUPON_ID");
    ```

- See a list of invoices for a customer
    ```
      List<Invoice> invoiceList = stripeService.listAllInvoices();
    ```

   
### To build

```
  mvn clean install
  
  # Skip gpg signing 
  mvn clean install -Dgpg.skip=true
  
```     

### To run integration tests
```
  cd stripe-service
  mvn clean test -Dgpg.skip=true -Dtest=StripeApplicationIntegrationTests -Dstripe.apiKey=<your-key-here>
```

### Adding Project Lombok Agent
This project uses Project Lombok to generate getters and setters etc. Compiling from the command line this shouldn't cause any problems, but in an IDE you need to add an agent to the JVM. Full instructions can be found in the Lombok website. The sign that you need to do this is a lot of compiler errors to do with missing methods and fields.
