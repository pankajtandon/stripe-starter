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

package com.technochord.stripe.service;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stripe.Stripe;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Card;
import com.stripe.model.Charge;
import com.stripe.model.Coupon;
import com.stripe.model.CouponCollection;
import com.stripe.model.Customer;
import com.stripe.model.CustomerCollection;
import com.stripe.model.ExternalAccount;
import com.stripe.model.ExternalAccountCollection;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceCollection;
import com.stripe.model.Plan;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionCollection;
import com.technochord.stripe.StripeException;
import com.technochord.stripe.model.CustomerLite;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;

/**
 * A service that encapsulates the Stripe API and offers convenient methods to manipulate
 * the Stripe API.
 *
 * @author Pankaj Tandon
 */
@Slf4j
public class StripeService {

	private static String CUSTOMER_SOURCE = "source";

	private static String CUSTOMER_DESCRIPTION = "description";

	private static String CUSTOMER_COUPON = "coupon";

	private static String CUSTOMER_EMAIL = "email";

	private static String CUSTOMER_LIMIT = "limit";

	private static String CUSTOMER_METADATA = "metadata";

	private static String CUSTOMER_METADATA_CATEGORY = "category";

	private static String CUSTOMER_STARTING_AFTER = "starting_after";

	private static String SUBSCRIPTION_CUSTOMER = "customer";

	private static String SUBSCRIPTION_PLAN = "plan";

	private static String SUBSCRIPTION_ITEMS = "items";

	private static String SUBSCRIPTION_BILLING = "billing";

	private static String INVOICE_SUBSCRIPTION = "subscription";

	private static String INVOICE_LIMIT = "limit";

	private static String CARD_OBJECT = "object";

	private static String COUPON_STARTING_AFTER = "starting_after";

	private static String COUPON_LIMIT = "limit";

	public StripeService(String stripeApiKey) {
		Assert.assertTrue(
				"A Stripe API key must be supplied as a System property for the StripeService to be used! "
						+ "For example 'mvn clean test -Dgpg.skip=true -Dtest=StripeApplicationIntegrationTests -Dstripe.apiKey=<your-api-key-here>'. "
						+ "The key may be obtained from https://dashboard.stripe.com/developers",
				!StringUtils.isEmpty(stripeApiKey));
		Stripe.apiKey = stripeApiKey;
	}

	/**
	 * Creation of a customer requires a unique email address and a description. If that
	 * email address has been used before, then a <code>StripeException</code> will be
	 * thrown. You can also pass a category (optional). This category can be used to
	 * retrieve customers in this category using
	 * {@link #listAllCustomersByCategory(String category)}
	 * @param email required
	 * @param description required
	 * @param category optional - Any arbitrary string, used to search customers.
	 * @return customerId of created customer
	 */
	public String createCustomer(String email, String description, String category) {
		Map<String, Object> customerParameters = new HashMap();
		if (!StringUtils.isEmpty(email)) {
			Customer c = this.retrieveCustomerByEmail(email);
			if (c != null) {
				throw new StripeException(
						"A customer with this email address already exists. "
								+ "You will need to delete that customer (history of the deleted customer will be retained) "
								+ "and then reuse this email address");
			}
			customerParameters.put(CUSTOMER_EMAIL, email);
		}
		else {
			throw new StripeException("Email address needed for creating a customer");
		}

		if (!StringUtils.isEmpty(description)) {
			customerParameters.put(CUSTOMER_DESCRIPTION, description);
		}
		else {
			throw new StripeException("Description is required to create a Customer!");
		}
		if (!StringUtils.isEmpty(category)) {
			Map<String, Object> map = new HashMap();
			map.put(CUSTOMER_METADATA_CATEGORY, category);
			customerParameters.put(CUSTOMER_METADATA, map);
		}

		return this.createCustomer(customerParameters);
	}

	/**
	 * Creation of a customer requires a unique email address and a description. If that
	 * email address has been used before, then a <code>StripeException</code> will be
	 * thrown.
	 * @param email email address of customer
	 * @param description description of customer
	 * @return customerId corresponding to the created customer.
	 */
	public String createCustomer(String email, String description) {
		return this.createCustomer(email, description, null);
	}

	/**
	 * Update an existing customer's category.
	 * @param customerId id of customer
	 * @param category optional category of customer
	 */
	public void updateCustomerCategory(String customerId, String category) {
		if (StringUtils.isEmpty(category)) {
			throw new StripeException(String.format(
					"Customer category needed to update for customerId: %s", customerId));
		}
		Customer customer = this.retrieveCustomerById(customerId);
		try {
			Map<String, Object> customerParams = new HashMap();
			Map<String, Object> metadataMap = new HashMap();
			metadataMap.put(CUSTOMER_METADATA_CATEGORY, category);
			customerParams.put(CUSTOMER_METADATA, metadataMap);
			customer.update(customerParams);
			log.debug(String.format("Updated custome with Id %s with category %s",
					customer.getId(), category));
		}
		catch (Exception ex) {
			throw new StripeException("Error retrieving customer", ex);
		}
	}

	/**
	 * When the customer’s latest invoice is billed by charging automatically, delinquent
	 * is true if the invoice’s latest charge is failed.
	 * @param customerId id of customer
	 * @return boolean true if the customer is delinquent
	 */
	public Boolean isCustomerDelinquent(String customerId) {
		Boolean delinquent = false;
		try {
			Customer c = this.retrieveCustomerById(customerId);
			delinquent = c.getDelinquent();
		}
		catch (Exception ex) {
			throw new StripeException("Error determining delinquency of customer.", ex);
		}
		return delinquent;
	}

	/**
	 * Returns all customers against this Stripe API key.
	 * @return list &lt;CustomerLite &gt;. This can be used to retrieve each
	 * <code>Customer</code> object by using
	 * <code>retrieveCustomerById(String customerId) </code>
	 */
	public List<CustomerLite> listAllCustomers() {
		Map<String, Object> customerParameters = new HashMap();
		customerParameters.put(CUSTOMER_LIMIT, 100);
		List<CustomerLite> totalCustomerLiteList = new ArrayList();
		try {
			CustomerCollection customerCollection = Customer.list(customerParameters);
			Customer last = null;
			do {
				List<CustomerLite> customerLiteList = customerCollection.getData()
						.stream().map((c) -> {
							CustomerLite cl = new CustomerLite();
							cl.setId(c.getId());
							cl.setDelinquent(c.getDelinquent());
							cl.setDescription(c.getDescription());
							cl.setEmail(c.getEmail());
							return cl;
						}).collect(Collectors.toList());

				totalCustomerLiteList.addAll(customerLiteList);
				// Get the last element
				if (customerCollection.getData().size() > 0) {
					last = customerCollection.getData()
							.get(customerCollection.getData().size() - 1);
					customerParameters.put(CUSTOMER_STARTING_AFTER, last.getId());
					customerCollection = Customer.list(customerParameters);
				}
			}
			while (last != null && customerCollection != null
					&& customerCollection.getData().size() > 1);
		}
		catch (Exception ex) {
			throw new StripeException("Error retrieving customers", ex);
		}
		return totalCustomerLiteList;
	}

	/**
	 * Returns all customers who have the specified category. <code>Customer</code> object
	 * by using <code>retrieveCustomerById(String customerId) </code>
	 * @param category id of customer
	 * @return list &lt;CustomerLite&gt; List of CustomerLite
	 */
	public List<CustomerLite> listAllCustomersByCategory(String category) {
		Map<String, Object> customerParameters = new HashMap();
		customerParameters.put(CUSTOMER_LIMIT, 100);
		List<CustomerLite> totalCustomerLiteList = new ArrayList();
		try {
			CustomerCollection customerCollection = Customer.list(customerParameters);
			Customer last = null;
			// Impl note: This filter is necessary to reduce heap usage here (instead of
			// calling listAllCustomers(), and *then*
			// filtering.
			do {
				List<CustomerLite> customerLiteList = customerCollection.getData()
						.stream()
						.filter((Customer c) -> (c.getMetadata() != null) && (category
								.equals(c.getMetadata().get(CUSTOMER_METADATA_CATEGORY))))
						.map((Customer c) -> {
							CustomerLite cl = new CustomerLite();
							cl.setId(c.getId());
							cl.setDelinquent(c.getDelinquent());
							cl.setDescription(c.getDescription());
							cl.setEmail(c.getEmail());
							return cl;
						}).collect(Collectors.toList());

				totalCustomerLiteList.addAll(customerLiteList);
				// Get the last element
				if (customerCollection.getData().size() > 0) {
					last = customerCollection.getData()
							.get(customerCollection.getData().size() - 1);
					customerParameters.put(CUSTOMER_STARTING_AFTER, last.getId());
					customerCollection = Customer.list(customerParameters);
				}
			}
			while (last != null && customerCollection != null
					&& customerCollection.getData().size() > 1);
		}
		catch (Exception ex) {
			throw new StripeException("Error retrieving customers", ex);
		}
		return totalCustomerLiteList;
	}

	/**
	 * Returns a full <code>Customer</code> object corresponding to customerId passed in.
	 * If no user is found, then a null object is returned.
	 * @param customerId id of customer
	 * @return customer
	 */
	public Customer retrieveCustomerById(String customerId) {
		Customer c = null;
		try {
			c = Customer.retrieve(customerId);
		}
		catch (InvalidRequestException ire) {
			// log and ignore
			log.debug(String.format("Could not find customer with Id %s.", customerId));
		}
		catch (Exception ex) {
			throw new StripeException("Error retrieving customer.", ex);
		}
		return c;
	}

	/**
	 * Returns a single customer with the passed in email address. Since email address is
	 * unique, if more than one customer is found using the same email address, then a
	 * <code>StripeException</code> is thrown. If no user is found, then a null object is
	 * returned.
	 * @param email email of customer
	 * @return customer
	 * @throws StripeException if the number of users with this email address &gt; 1
	 */
	public Customer retrieveCustomerByEmail(String email) {
		List<Customer> customerList = null;
		Customer c = null;
		try {
			Map<String, Object> customerParams = new HashMap();
			customerParams.put(CUSTOMER_EMAIL, email);
			CustomerCollection customerCollection = Customer.list(customerParams);
			customerList = customerCollection.getData();
		}
		catch (InvalidRequestException ire) {
			// log and ignore
			log.debug(String.format("Could not find customer with email %s.", email));
		}
		catch (Exception ex) {
			throw new StripeException("Error retrieving customer", ex);
		}
		log.debug(String.format("Retireved %s customers with email %s",
				((customerList != null) ? customerList.size() : 0), email));
		if (customerList != null && customerList.size() == 1) {
			c = customerList.get(0);
		}
		else if (customerList != null && customerList.size() > 1) {
			throw new StripeException(String.format(
					"More than one customer with the same email address (%s) found!.",
					email));
		}
		return c;
	}

	/**
	 * Deleted customers' history is retained by Stripe and they can still be retrieved
	 * with the `delete` flag set to true. Deleted customers cannot be used in future
	 * transactions or subscriptions.
	 * @param customerId id of customer to delete.
	 */
	public void deleteCustomer(String customerId) {
		try {
			Customer retrievedCustomer = Customer.retrieve(customerId);
			retrievedCustomer.delete();
			log.info("Deleted customer with Id " + retrievedCustomer.getId());
		}
		catch (Exception ex) {
			throw new StripeException("Error deleting customer.", ex);
		}
	}

	public void deleteAllCustomers() {
		List<CustomerLite> customerList = this.listAllCustomers();
		if (customerList != null) {
			customerList.stream().forEach((c) -> {
				this.deleteCustomer(c.getId());
			});
			log.debug(String.format("Deleted %s customes.", customerList.size()));
		}
	}

	/**
	 * Removes the applied Payment Source (token) for the passed in customer.
	 * @param customerId id of customer
	 */
	public void removePaymentSourceFromCustomer(String customerId) {
		try {
			Map<String, Object> cardParams = new HashMap();
			cardParams.put("limit", 100);
			cardParams.put(CARD_OBJECT, "card");
			ExternalAccountCollection externalAccountCollection = Customer
					.retrieve(customerId).getSources().all(cardParams);
			List<ExternalAccount> externalAccountList = externalAccountCollection
					.getData();
			Customer customer = Customer.retrieve(customerId);
			externalAccountList.forEach((ea) -> {
				Card c = (Card) ea;
				try {
					customer.getSources().retrieve(c.getId()).delete();
				}
				catch (Exception ex) {
					throw new StripeException(
							String.format("Error deleting card Id %s.", c.getId()), ex);
				}
			});
		}
		catch (Exception ex) {
			throw new StripeException("Error removing token.", ex);
		}
	}

	/**
	 * The email address of a customer can be changed to the specified email address.
	 * @param customerId id of customer
	 * @param newEmail new email to change to
	 * @throws StripeException if the passed in email address is already associated to an
	 * existing Stripe customer.
	 */
	public void changeCustomerEmail(String customerId, String newEmail) {
		Customer existing = this.retrieveCustomerByEmail(newEmail);
		if (existing != null) {
			throw new StripeException(String.format(
					"Cannot change to email %s because a customer already exists with that email address!",
					newEmail));
		}
		try {
			Map<String, Object> customerParameters = new HashMap();
			if (!StringUtils.isEmpty(newEmail)) {
				customerParameters.put(CUSTOMER_EMAIL, newEmail);
			}
			else {
				throw new StripeException("New email to apply not specified!");
			}
			Customer customer = Customer.retrieve(customerId);
			customer.update(customerParameters);
			log.debug(String
					.format("Updated email for customer with Id " + customer.getId()));
		}
		catch (Exception ex) {
			throw new StripeException("Error updating customer.", ex);
		}
	}

	/**
	 * Creates a subscription for the Customer who's email address is passed in using the
	 * plan corresponding to the passed in plainId. The customer is also charged as per
	 * the the plan amount (after applying discounts as specified by coupons applied to
	 * the Customer)
	 * @param email email of the customer who is being added to the subscription with the
	 * specified plainId
	 * @param planId required. The plan to tie this customer to via this subscription.
	 * @return the created subscriptionId.
	 * @throws StripeException if the PlanId is invalid
	 * @throws StripeException if the customer represented by the passed in email has no
	 * Payment Source
	 */
	public String createSubscriptionForCustomerAndCharge(String email, String planId) {
		Customer c = this.retrieveCustomerByEmail(email);
		String subscriptonId = this.createSubscriptionAndCharge(c.getId(), planId);
		return subscriptonId;
	}

	/**
	 * Creates a Subscription tying a Customer to a Plan. Since a customer can have only
	 * one plan at a time, all existing subscriptions are canceled before creating a new
	 * subscription.
	 * @param customerId required
	 * @param planId required
	 * @return subscriptionId of created Subscription
	 * @throws StripeException if the PlanId is invalid
	 * @throws StripeException if the customer represented by the passed in customerId has
	 * no Payment Source
	 */
	public String createSubscriptionAndCharge(String customerId, String planId) {
		// Ensure that the plan is valid
		if (!this.isPlanValid(planId)) {
			throw new StripeException(String.format(
					"PlanId %s is invalid. Please create a valid plan using the Stripe Dashbpard specify the planId in this call",
					planId));
		}

		// Ensure that the passed in customer has a token (payment source)
		Customer retrievedCustomer = this.retrieveCustomerById(customerId);
		if (!this.doesCustomerHaveActivePaymentSource(retrievedCustomer.getId())) {
			throw new StripeException(String
					.format("There is no payment source for customerId %s", customerId));
		}
		// Cancel existing
		this.cancelAllExistingSubscriptionsForCustomer(customerId);

		// Create a new subscription and charge.
		log.debug(
				"No subscription for this customer and plan found, creating a new subscription.");

		Map<String, Object> item = new HashMap();
		item.put(SUBSCRIPTION_PLAN, planId);
		Map<String, Object> items = new HashMap();
		items.put("0", item);

		Map<String, Object> params = new HashMap();
		params.put(SUBSCRIPTION_CUSTOMER, customerId);
		params.put(SUBSCRIPTION_ITEMS, items);
		params.put(SUBSCRIPTION_BILLING, "charge_automatically"); // Will cause charge to
																	// be made when
																	// Subscription is
																	// created.
		Subscription createdSubscription = null;
		try {
			Subscription.create(params);
		}
		catch (Exception ex) {
			throw new StripeException("Error creating a new Subscription", ex);
		}
		createdSubscription = this.getSubscriptionByCustomerAndPlan(customerId, planId);
		log.debug(String.format("Created new subscription with Id %s.",
				createdSubscription.getId()));

		Invoice latestInvoice = this
				.getLatestInvoiceForSubscription(createdSubscription.getId());
		DecimalFormat twoPlaces = new DecimalFormat("0.00");
		log.info(String.format(
				"Created Subscription and charged $%s as per associated plan",
				(latestInvoice.getAmountPaid() == null) ? 0
						: (twoPlaces.format(latestInvoice.getAmountPaid() / 100.0))));

		if (createdSubscription == null) {
			throw new StripeException(
					"Could not retrieve just created Subscription! Something bad hapenned!");
		}
		return createdSubscription.getId();
	}

	/**
	 * Cancels all existing Subscritptions for the customer corresponding to the passed in
	 * customerId.
	 * @param customerId id of customer
	 */
	public void cancelAllExistingSubscriptionsForCustomer(String customerId) {
		List<Subscription> existingSubscriptions = this
				.getAllSubscriptionByCustomer(customerId);
		if (existingSubscriptions != null) {
			existingSubscriptions.stream().forEach((s) -> {
				try {
					s.cancel(null);
					log.debug(String.format(
							"Canceled subscription with SubscriptionId: %s for customerId",
							s.getId(), customerId));
				}
				catch (Exception ex) {
					throw new StripeException(String.format(
							"Error canceling subscription with Id %s", s.getId()), ex);
				}
			});
		}
	}

	/**
	 * Returns Subscription for a customerId and Plan If there are more than one
	 * subscription for this combination then a <code>StripeException</code> is thrown.
	 * @param customerId id of customer
	 * @param planId plan Id configured on Stripe.com
	 * @return subscription
	 * @throws StripeException if there are more than one subscriptions for this
	 * customerId and with the this planId
	 */
	public Subscription getSubscriptionByCustomerAndPlan(String customerId,
			String planId) {
		Subscription foundSubscription = null;
		try {
			Map<String, Object> params = new HashMap();
			params.put(SUBSCRIPTION_CUSTOMER, customerId);
			params.put(SUBSCRIPTION_PLAN, planId);
			SubscriptionCollection subscriptions = Subscription.list(params);
			List<Subscription> subscriptionList = subscriptions.getData();
			log.debug(
					String.format("Found %s subsciptions for customerId %s and planId %s",
							((subscriptionList != null) ? subscriptionList.size() : 0),
							customerId, planId));
			if (subscriptionList != null && subscriptionList.size() > 1) {
				throw new Exception(String.format(
						"There are more than one subscriptions for this customerId (%s) with the this planId (%s).",
						customerId, planId));
			}
			if (subscriptionList != null && subscriptionList.size() == 1) {
				foundSubscription = subscriptionList.get(0);
			}
		}
		catch (Exception ex) {
			throw new StripeException("Error retrieving Subscription.", ex);
		}
		return foundSubscription;
	}

	/**
	 * Returns Subscription for a customerId If there are more than one subscription then
	 * a <code>StripeException</code> is thrown If none, then null Subscription is
	 * returned.
	 * @param customerId id of customer
	 * @return subscription
	 * @throws StripeException there are more than one subscriptions for this customerId
	 */
	public List<Subscription> getAllSubscriptionByCustomer(String customerId) {
		List<Subscription> subscriptionList = null;
		try {
			Map<String, Object> params = new HashMap();
			params.put(SUBSCRIPTION_CUSTOMER, customerId);
			SubscriptionCollection subscriptions = Subscription.list(params);
			subscriptionList = subscriptions.getData();
			log.debug(String.format("Found %s subsciptions for customerId %s.",
					((subscriptionList != null) ? subscriptionList.size() : 0),
					customerId));
			if (subscriptionList != null && subscriptionList.size() > 1) {
				throw new Exception(String.format(
						"There are more than one subscriptions for this customerId (%s).",
						customerId));
			}

		}
		catch (Exception ex) {
			throw new StripeException("Error retrieving Subscription.", ex);
		}
		return subscriptionList;
	}

	/**
	 * Returns true if this plan is a valid plan (created typically via the Stripe
	 * Dashboard).
	 * @param planId plan Id configured on Stripe.com
	 * @return boolean
	 */
	public boolean isPlanValid(String planId) {
		boolean valid = false;
		Plan plan = null;
		try {
			plan = Plan.retrieve(planId);
			if (plan != null) {
				valid = true;
			}
		}
		catch (Exception ex) {
			// Ignore
		}
		return valid;
	}

	/**
	 * Returns true if this customer does not have a valid payment source (aka token).
	 * @param customerId id of customer
	 * @return boolean true if Customer has an active payment source
	 */
	public boolean doesCustomerHaveActivePaymentSource(String customerId) {
		boolean hasPaymentSource = false;
		Customer c = this.retrieveCustomerById(customerId);
		if (c != null) {
			String source = c.getDefaultSource();
			if (!StringUtils.isEmpty(source)) {
				hasPaymentSource = true;
			}
		}
		return hasPaymentSource;
	}

	/**
	 * Returns the latest Invoice for the passed in subscriptionId. If more than one
	 * invoice is returned, then only the latest invoice is returned. If no invoice is
	 * found, a null invoice is returned.
	 * @param subscriptionId subscription Id of the subscription.
	 * @return invoice latest Invoice, null if no invoice found
	 */
	public Invoice getLatestInvoiceForSubscription(String subscriptionId) {
		// Retrieve Invoice
		Map<String, Object> invoiceParams = new HashMap();
		invoiceParams.put(INVOICE_SUBSCRIPTION, subscriptionId);
		InvoiceCollection invoiceCollection = null;
		try {
			invoiceCollection = Invoice.list(invoiceParams);
		}
		catch (Exception ex) {
			throw new StripeException("Error determining invoice list.", ex);
		}
		List<Invoice> invoiceList = invoiceCollection.getData();
		if (invoiceList != null && invoiceList.size() > 1) {
			invoiceList.sort(Comparator.comparing(Invoice::getDate));
		}

		return (invoiceList != null) ? (invoiceList.get(0)) : null;
	}

	/**
	 * This method adds a token (payment source) to the (existing) customer with the
	 * passed in Id If a payment source already exists for this customer, then the passed
	 * in token *replaces* the existing payment source. It also makes the payment source
	 * passed in (token) the default. If the customerId being passed in does not represent
	 * a customer, then <code>StripeException</code> is thrown.
	 * @param customerId id of customer
	 * @param token payment source
	 * @throws StripeException if the passed token is null
	 */
	public void replacePaymentSourceForCustomer(String customerId, String token) {
		Customer c = this.retrieveCustomerById(customerId);
		if (c == null) {
			throw new StripeException(
					String.format("Could not find customer with Id: ", customerId));
		}
		try {
			Map<String, Object> customerParameters = new HashMap();
			if (!StringUtils.isEmpty(token)) {
				customerParameters.put(CUSTOMER_SOURCE, token);
			}
			else {
				throw new StripeException("New token to apply not specified!");
			}
			c.update(customerParameters);
			log.debug(String.format("Added token for customer with Id %s.", c.getId()));
		}
		catch (Exception ex) {
			throw new StripeException("Error adding token to customer.", ex);
		}
	}

	/**
	 * Applies Coupon (typically created on teh Stripe Dashboard) to a Customer specified.
	 * by customerId
	 * @param customerId id of customer
	 * @param couponId coupon to apply (created on Stripe.com)
	 * @throws StripeException if the passed in coupon does not exist or is invalid.
	 * @throws StripeException if the passed in customerId does not represent a customer.
	 */
	public void applyCouponToCustomer(String customerId, String couponId) {
		try {
			Coupon.retrieve(couponId);
		}
		catch (Exception ex) {
			throw new StripeException(
					String.format("This coupon %s does not exist!", couponId), ex);
		}

		Customer c = this.retrieveCustomerById(customerId);
		if (c == null) {
			throw new StripeException(
					String.format("Could not find customer with Id: ", customerId));
		}
		try {
			Map<String, Object> customerParameters = new HashMap();
			if (!StringUtils.isEmpty(couponId)) {
				customerParameters.put(CUSTOMER_COUPON, couponId);
			}
			else {
				throw new StripeException("CouponId to apply not specified!");
			}
			c.update(customerParameters);
			log.debug(String.format("Added coupon for customer with Id %s.", c.getId()));
		}
		catch (Exception ex) {
			throw new StripeException("Error adding coupon to customer.", ex);
		}

	}

	/**
	 * Lists all coupons.
	 * @return list&lt;Coupon&gt; Coupon list
	 */
	public List<Coupon> listAllCoupons() {
		Map<String, Object> couponParams = new HashMap();
		couponParams.put(COUPON_LIMIT, 100);
		List<Coupon> totalCouponList = new ArrayList();
		try {
			CouponCollection couponCollection = Coupon.list(couponParams);
			Coupon last = null;
			do {
				totalCouponList.addAll(couponCollection.getData());

				// Get the last element
				if (couponCollection.getData().size() > 0) {
					last = couponCollection.getData()
							.get(couponCollection.getData().size() - 1);
					couponParams.put(COUPON_STARTING_AFTER, last.getId());
					couponCollection = Coupon.list(couponParams);
				}
			}
			while (last != null && couponCollection != null
					&& couponCollection.getData().size() > 1);

		}
		catch (Exception ex) {
			throw new StripeException("Error retrieving coupons.", ex);
		}

		return totalCouponList;
	}

	/**
	 * Lists all Invoices.
	 * @return list&lt;Invoice&gt; Invoice list
	 */
	public List<Invoice> listAllInvoices() {
		Map<String, Object> invoiceParams = new HashMap();
		invoiceParams.put(INVOICE_LIMIT, 100);
		List<Invoice> totalInvoiceList = new ArrayList();
		try {
			InvoiceCollection invoiceCollection = Invoice.list(invoiceParams);
			Invoice last = null;
			do {
				totalInvoiceList.addAll(invoiceCollection.getData());

				// Get the last element
				if (invoiceCollection.getData().size() > 0) {
					last = invoiceCollection.getData()
							.get(invoiceCollection.getData().size() - 1);
					invoiceParams.put(COUPON_STARTING_AFTER, last.getId());
					invoiceCollection = Invoice.list(invoiceParams);
				}
			}
			while (last != null && invoiceCollection != null
					&& invoiceCollection.getData().size() > 1);

		}
		catch (Exception ex) {
			throw new StripeException("Error retrieving coupons.", ex);
		}

		return totalInvoiceList;
	}

	/**
	 * Returns the Charge for a given chargeId.
	 * @param chargeId charge Id
	 * @return charge
	 */
	public Charge getCharge(String chargeId) {
		Charge charge = null;
		try {
			charge = Charge.retrieve(chargeId);
		}
		catch (Exception ex) {
			throw new StripeException(
					String.format("Error retrieving charge for chargeId %s.", chargeId),
					ex);
		}
		return charge;
	}

	// ----- P R I V A T E --------
	private String createCustomer(Map<String, Object> customerParameters) {
		Customer retrievedCustomer = null;
		try {
			Customer.create(customerParameters);
			retrievedCustomer = this.retrieveCustomerByEmail(
					(String) customerParameters.get(CUSTOMER_EMAIL));
			log.info(String.format("Created customer with Id %s",
					retrievedCustomer.getId()));
		}
		catch (Exception ex) {
			throw new StripeException("Error creating customer.", ex);
		}
		return retrievedCustomer.getId();
	}

}
