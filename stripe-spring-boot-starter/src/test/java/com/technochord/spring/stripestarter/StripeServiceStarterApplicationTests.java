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

import com.technochord.stripe.service.StripeService;
import org.junit.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for {@link StripeServiceAutoConfiguration}.
 *
 * @author Pankaj Tandon
 */
public class StripeServiceStarterApplicationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(
					AutoConfigurations.of(StripeServiceAutoConfiguration.class));

	@Test
	public void testAutoConfigurationIsLoadedWhenEnabled() {
		this.contextRunner
				.withPropertyValues("stripe.enabled=true", "stripe.apiKey=dummy")
				.run((context) -> {
					assertThat(context).hasSingleBean(StripeService.class);
				});
	}

	@Test
	public void testAutoConfigurationIsNotLoadedWhenDisabled() {
		this.contextRunner
				.withPropertyValues("stripe.enabled=false", "stripe.apiKey=dummy")
				.run((context) -> {
					assertThat(context).doesNotHaveBean(StripeService.class);
				});
	}

	@Test
	public void testAutoConfigurationIsNotLoadedAndDoesNotThrowExceptionWhenDisabledAndNoKeyPassed() {
		this.contextRunner.withPropertyValues("stripe.enabled=false", "stripe.apiKey=")
				.run((context) -> {
					assertThat(context).doesNotHaveBean(StripeService.class);
				});
	}

	@Test
	public void testAutoConfigurationIsNotLoadedWhenDisabledAndZeroLengthKeyPassed() {
		this.contextRunner.withPropertyValues("stripe.enabled=false").run((context) -> {
			assertThat(context).doesNotHaveBean(StripeService.class);
		});
	}

	@Test(expected = AssertionError.class)
	public void testAutoConfigurationThrowsExceptionWhenEnabledAndNoKeyPassed() {
		this.contextRunner.withPropertyValues("stripe.enabled=true").run((context) -> {
			assertThat(context).doesNotHaveBean(StripeService.class);
		});
	}

	@Test(expected = AssertionError.class)
	public void testAutoConfigurationThrowsExceptionWhenEnabledAndZeroLengthKeyPassed() {
		this.contextRunner.withPropertyValues("stripe.enabled=true", "stripe.apiKey=")
				.run((context) -> {
					assertThat(context).doesNotHaveBean(StripeService.class);
				});
	}

}
