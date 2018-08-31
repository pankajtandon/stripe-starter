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

package com.technochord.stripe.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a 'lite' version of the {@link com.stripe.model.Customer} object. This
 * object contains the customerId and this object can be used to retrieve the full
 * {@link com.stripe.model.Customer}.
 *
 * Besides the customerId, this class contains the email, description and
 * delinquent(boolean)
 *
 * @author Pankaj Tandon
 */
@NoArgsConstructor
@Getter
@Setter
public class CustomerLite {

	private String id;

	private String email;

	private String description;

	private boolean delinquent;

}
