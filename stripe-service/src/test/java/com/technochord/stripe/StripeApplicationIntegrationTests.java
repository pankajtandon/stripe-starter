/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.technochord.stripe;

import java.util.List;

import com.stripe.model.Charge;
import com.stripe.model.Coupon;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.technochord.stripe.model.CustomerLite;
import com.technochord.stripe.service.StripeService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StripeApplicationIntegrationTests {

	private StripeService stripeService;

	private String apiKey = System.getProperty("stripe.apiKey");

	@Before
	public void setup() {
		this.stripeService = new StripeService(this.apiKey);
		List<CustomerLite> customerList = this.stripeService.listAllCustomers();
		for (CustomerLite c : customerList) {
			// cancel all subscriptions
			this.stripeService.cancelAllExistingSubscriptionsForCustomer(c.getId());
		}
		this.stripeService.deleteAllCustomers();
	}

	@Test(expected = StripeException.class)
	public void test_creation_of_customer_with_same_email_should_fail() {
		// Create a customer
		String customerId = this.stripeService.createCustomer("anEmail@email.com",
				"a description");

		// Create customer eith same email different description
		this.stripeService.createCustomer("anEmail@email.com", "a new description");
	}

	@Test
	public void test_retrieval_of_non_existent_customerId() {
		// Create a customer
		String customerId = this.stripeService.createCustomer("anEmail@email.com",
				"a description");

		Customer c = this.stripeService.retrieveCustomerById(customerId + "bogus");
		Assert.assertTrue(c == null);
	}

	@Test
	public void test_retrieval_of_non_existent_customer_email() {
		// Create a customer
		this.stripeService.createCustomer("anEmail@email.com", "a description");

		Customer c = this.stripeService.retrieveCustomerByEmail("bogus@email.com");
		Assert.assertTrue(c == null);
	}

	@Test
	public void test_successful_subscription() {
		// Create a customer
		String customerId = this.stripeService.createCustomer("anEmail@email.com",
				"a description");

		// Add a payment source
		this.stripeService.replacePaymentSourceForCustomer(customerId, "tok_amex");

		// Create a subscription against a plan and charge the customer
		String subscriptionId = this.stripeService.createSubscriptionForCustomerAndCharge(
				"anEmail@email.com", "monthly-plan");

		// Invoice
		Invoice invoice = this.stripeService
				.getLatestInvoiceForSubscription(subscriptionId);

		Assert.assertTrue(invoice.getAmountPaid() > 0);
	}

	@Test(expected = StripeException.class)
	public void test_subscription_without_payment_source_on_customer_should_fail() {
		// Create a customer
		String customerId = this.stripeService.createCustomer("anEmail@email.com",
				"a description");

		// Create a subscription against a plan and charge the customer
		String subscriptionId = this.stripeService.createSubscriptionForCustomerAndCharge(
				"anEmail@email.com", "monthly-plan");

	}

	@Test(expected = StripeException.class)
	public void test_subscription_after_removal_of_payment_source_on_customer_should_fail() {
		// Create a customer
		String customerId = this.stripeService.createCustomer("anEmail@email.com",
				"a description");

		// Add a payment source
		this.stripeService.replacePaymentSourceForCustomer(customerId, "tok_amex");

		// Create a subscription against a plan and charge the customer
		String subscriptionId = this.stripeService.createSubscriptionForCustomerAndCharge(
				"anEmail@email.com", "monthly-plan");

		// Now remove the payment source
		this.stripeService.removePaymentSourceFromCustomer(customerId);

		// Try subscribing again
		this.stripeService.createSubscriptionForCustomerAndCharge("anEmail@email.com",
				"monthly-plan");
	}

	@Test
	public void test_that_coupon_gets_applied() {
		// Create a customer
		String customerId = this.stripeService.createCustomer("anEmail@email.com",
				"a description");

		// Add a payment source
		this.stripeService.replacePaymentSourceForCustomer(customerId, "tok_amex");

		// Create a subscription against a plan and charge the customer
		String subscriptionId = this.stripeService.createSubscriptionForCustomerAndCharge(
				"anEmail@email.com", "monthly-plan");

		// Check the invoiced amount is correct
		Invoice invoice = this.stripeService
				.getLatestInvoiceForSubscription(subscriptionId);

		Assert.assertTrue(invoice.getAmountPaid() == 100);

		// Apply a coupon to the customer
		this.stripeService.applyCouponToCustomer(customerId, "TEST_COUPON_ID");

		// Subscribe again
		String reSubscriptionId = this.stripeService
				.createSubscriptionForCustomerAndCharge("anEmail@email.com",
						"monthly-plan");

		// Check invoice.. should be the discounted amount
		Invoice reInvoice = this.stripeService
				.getLatestInvoiceForSubscription(reSubscriptionId);

		Assert.assertTrue(reInvoice.getAmountPaid() == 90);
	}

	@Test
	public void test_that_payment_source_can_get_changed() {
		// Create a customer
		String customerId = this.stripeService.createCustomer("anEmail@email.com",
				"a description");

		// Add a payment source
		this.stripeService.replacePaymentSourceForCustomer(customerId, "tok_amex");

		// Create a subscription against a plan and charge the customer
		String subscriptionId = this.stripeService.createSubscriptionForCustomerAndCharge(
				"anEmail@email.com", "monthly-plan");

		// Invoice
		Invoice invoice = this.stripeService
				.getLatestInvoiceForSubscription(subscriptionId);

		// Get the related charge
		Charge charge = this.stripeService.getCharge(invoice.getCharge());

		Assert.assertTrue(charge.getStatus().equals("succeeded"));

		// Change the payment source
		this.stripeService.replacePaymentSourceForCustomer(customerId, "tok_visa");

		// Subscribe again
		String reSubscriptionId = this.stripeService
				.createSubscriptionForCustomerAndCharge("anEmail@email.com",
						"monthly-plan");

		// Invoice
		Invoice reInvoice = this.stripeService
				.getLatestInvoiceForSubscription(reSubscriptionId);

		// Get the related charge
		Charge reCharge = this.stripeService.getCharge(reInvoice.getCharge());

		// Assert success
		Assert.assertTrue(reCharge.getStatus().equals("succeeded"));
	}

	@Test
	public void test_get_customers_of_a_certain_category_and_updation_of_customer_category() {
		// Create some customers, some with categories
		String customerId1 = this.stripeService.createCustomer("anEmail1@email.com",
				"a description1");
		String customerId2 = this.stripeService.createCustomer("anEmail2@email.com",
				"a description2");
		String customerId3 = this.stripeService.createCustomer("anEmail3@email.com",
				"a description3", "CAT1");
		String customerId4 = this.stripeService.createCustomer("anEmail4@email.com",
				"a description4");
		String customerId5 = this.stripeService.createCustomer("anEmail5@email.com",
				"a description5", "CAT1");
		String customerId6 = this.stripeService.createCustomer("anEmail6@email.com",
				"a description6", "CAT1");
		String customerId7 = this.stripeService.createCustomer("anEmail7@email.com",
				"a description7");
		String customerId8 = this.stripeService.createCustomer("anEmail8@email.com",
				"a description8");

		// Retrieve that category of customers
		List<CustomerLite> cat1CustomerLiteList = this.stripeService
				.listAllCustomersByCategory("CAT1");

		Assert.assertTrue(cat1CustomerLiteList != null);
		Assert.assertTrue(cat1CustomerLiteList.size() == 3);
		Assert.assertTrue(cat1CustomerLiteList.stream()
				.filter((c) -> c.getEmail().equals("anEmail6@email.com")).count() == 1);
		Assert.assertTrue(cat1CustomerLiteList.stream()
				.filter((c) -> c.getEmail().equals("anEmail5@email.com")).count() == 1);
		Assert.assertTrue(cat1CustomerLiteList.stream()
				.filter((c) -> c.getEmail().equals("anEmail3@email.com")).count() == 1);

		// Now update the category of one of the customers
		this.stripeService.updateCustomerCategory(customerId5, "CAT2");

		// Re-retrieve that category of customers
		cat1CustomerLiteList = this.stripeService.listAllCustomersByCategory("CAT1");

		Assert.assertTrue(cat1CustomerLiteList != null);
		Assert.assertTrue(cat1CustomerLiteList.size() == 2);

		// Re-retrieve that category of customers
		List<CustomerLite> cat2CustomerLiteList = this.stripeService
				.listAllCustomersByCategory("CAT2");

		Assert.assertTrue(cat2CustomerLiteList != null);
		Assert.assertTrue(cat2CustomerLiteList.size() == 1);
		Assert.assertTrue(cat2CustomerLiteList.stream()
				.filter((c) -> c.getEmail().equals("anEmail5@email.com")).count() == 1);
	}

	@Test
	public void test_change_customers_email_before_subscription() {
		// Create a customer
		String customerId1 = this.stripeService.createCustomer("anEmail1@email.com",
				"a description1");

		this.stripeService.changeCustomerEmail(customerId1, "someOther@email.com");

		Customer customer = this.stripeService
				.retrieveCustomerByEmail("someOther@email.com");

		Assert.assertTrue(customer != null);
	}

	@Test
	public void test_change_customers_email_after_subscription() {
		// Create a customer
		String customerId = this.stripeService.createCustomer("anEmail@email.com",
				"a description");

		// Add a payment source
		this.stripeService.replacePaymentSourceForCustomer(customerId, "tok_amex");

		// Create a subscription against a plan and charge the customer
		String subscriptionId = this.stripeService.createSubscriptionForCustomerAndCharge(
				"anEmail@email.com", "monthly-plan");

		this.stripeService.changeCustomerEmail(customerId, "someOther@email.com");

		Customer customer = this.stripeService
				.retrieveCustomerByEmail("someOther@email.com");

		Assert.assertTrue(customer != null);
	}

	@Test(expected = StripeException.class)
	public void test_change_customers_email_to_existing_email() {

		// Create 2 customers
		String customerId1 = this.stripeService.createCustomer("anEmail1@email.com",
				"a description1");
		String customerId2 = this.stripeService.createCustomer("anEmail2@email.com",
				"a description2");

		this.stripeService.changeCustomerEmail(customerId1, "anEmail2@email.com");
	}

	@Test
	public void test_if_customer_has_valid_payment_source() {
		// Create a customer
		String customerId = this.stripeService.createCustomer("anEmail@email.com",
				"a description");

		// Add a payment source
		this.stripeService.replacePaymentSourceForCustomer(customerId, "tok_amex");

		Boolean boo = this.stripeService.doesCustomerHaveActivePaymentSource(customerId);

		Assert.assertTrue(boo);

		this.stripeService.removePaymentSourceFromCustomer(customerId);

		boo = this.stripeService.doesCustomerHaveActivePaymentSource(customerId);

		Assert.assertTrue(!boo);
	}

	@Test
	// @Ignore //external dep
	public void test_list_all_coupons() {
		List<Coupon> couponList = this.stripeService.listAllCoupons();

		Assert.assertTrue(couponList != null);
		Assert.assertTrue(couponList.size() == 1);
	}

	@Test
	public void test_list_all_invoices() {
		List<Invoice> invoiceList = this.stripeService.listAllInvoices();

		Assert.assertTrue(invoiceList != null);
		Assert.assertTrue(invoiceList.size() > 0);
	}

}
