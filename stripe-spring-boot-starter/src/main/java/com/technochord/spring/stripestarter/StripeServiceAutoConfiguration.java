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

package com.technochord.spring.stripestarter;

import com.stripe.Stripe;
import com.technochord.stripe.service.StripeService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link Stripe}.
 *
 * @author Pankaj Tandon
 * @since 2.1.0
 */
@Configuration
@ConditionalOnClass(Stripe.class)
@EnableConfigurationProperties(StripeProperties.class)
@ConditionalOnProperty(prefix = "stripe", name = "enabled", havingValue = "true")
@Slf4j
public class StripeServiceAutoConfiguration {

	@Autowired
	private StripeProperties stripeProperties;

	@Bean
	@ConditionalOnMissingBean
	public StripeService stripeService() {
		log.debug("Instantiating Stripe Service");
		return new StripeService(this.stripeProperties.getApiKey());
	}

}
